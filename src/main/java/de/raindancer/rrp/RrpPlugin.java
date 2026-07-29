package de.raindancer.rrp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import de.raindancer.rrp.catalog.CatalogService;
import de.raindancer.rrp.command.ChatPromptListener;
import de.raindancer.rrp.command.RrpCommand;
import de.raindancer.rrp.core.RrpConfig;
import de.raindancer.rrp.core.RrpService;
import de.raindancer.rrp.gui.MainMenu;
import de.raindancer.rrp.gui.MenuManager;
import de.raindancer.rrp.pack.DatapackService;
import de.raindancer.rrp.pack.PackHttpServer;
import de.raindancer.rrp.pack.PackMerger;
import de.raindancer.rrp.pack.PackStore;
import de.raindancer.rrp.util.Banner;
import de.raindancer.rrp.util.Downloader;
import de.raindancer.rrp.util.Msg;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Rain's Resourcepack Manager — install, remove, require and combine resource packs, install the
 * datapack half that belongs to them, and hand out the items they add.
 *
 * <p>This class only wires things together and owns the two resources that must not leak: the
 * worker thread used for network and zip work, and the HTTP server that publishes the combined
 * pack.
 */
public final class RrpPlugin extends JavaPlugin implements Listener {

    /** Full access: installing, removing, applying, configuring. */
    public static final String PERMISSION = "rrp.admin";
    /** Handing out pack items. */
    public static final String PERMISSION_GIVE = "rrp.give";

    private volatile RrpConfig config;
    private volatile Downloader downloader;
    private ExecutorService worker;
    private RrpService service;
    private MenuManager menus;
    private PackHttpServer http;
    private String userAgent;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = RrpConfig.from(getConfig());
        this.userAgent = "RainsResourcepackManager/" + getPluginMeta().getVersion()
                + " (Paper " + Bukkit.getMinecraftVersion() + "; +https://github.com/Raindancer118)";
        this.downloader = new Downloader(config, userAgent);

        // One worker thread on purpose: downloads and merges are rare, and serialising them
        // removes any chance of two merges racing for the same output file.
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "rrp-worker");
            thread.setDaemon(true);
            return thread;
        });

        Path dataFolder = getDataFolder().toPath();
        try {
            Files.createDirectories(dataFolder.resolve("packs"));
        } catch (IOException e) {
            getSLF4JLogger().error("Could not create RRP's pack folder — installs will fail.", e);
        }

        PackStore store = new PackStore(dataFolder.resolve("installed.yml"), getSLF4JLogger());
        store.load();

        CatalogService catalogs = new CatalogService(
                downloader, dataFolder.resolve("catalog.json"), getSLF4JLogger());
        catalogs.loadCache();

        this.service = new RrpService(this, store, catalogs,
                new DatapackService(getSLF4JLogger()), new PackMerger(getSLF4JLogger()),
                getSLF4JLogger());

        startHttpServer();

        this.menus = new MenuManager(this);
        menus.register();
        getServer().getPluginManager().registerEvents(new ChatPromptListener(menus), this);
        getServer().getPluginManager().registerEvents(this, this);
        // What clients do with the packs: a failed download otherwise looks exactly like success.
        getServer().getPluginManager().registerEvents(
                new de.raindancer.rrp.pack.PackStatusListener(service, getSLF4JLogger()), this);

        RrpCommand command = new RrpCommand(this, service, menus);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        "rrp",
                        "Manage this server's resource packs and the items they add.",
                        java.util.List.of("resourcepacks", "rrpm"),
                        command));

        // Refresh the catalogue once at startup, then on the configured interval. Both are
        // background work; a server without internet access keeps running off the cache.
        service.refreshCatalog(RrpService.Callback.NONE);
        scheduleCatalogRefresh();

        // Build the combined pack from what is already installed, then report the real state.
        service.rebuildMerge((success, message) -> runStartupSelfCheck());
    }

    @Override
    public void onDisable() {
        if (http != null) {
            http.stop();
            http = null;
        }
        if (menus != null) {
            menus.shutdown();
        }
        if (worker != null) {
            worker.shutdownNow();
            try {
                if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                    getSLF4JLogger().warn("RRP's worker thread did not stop within 5 seconds.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Sends the installed packs to a joining player. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!config.applyOnJoin()) {
            return;
        }
        // One tick later: the client is still finishing the join sequence, and a pack request
        // sent inside the join event can be dropped by some clients.
        getServer().getScheduler().runTaskLater(this, () -> {
            if (event.getPlayer().isOnline()) {
                service.applyTo(event.getPlayer());
            }
        }, 20L);
    }

    private void startHttpServer() {
        if (!config.httpEnabled()) {
            return;
        }
        PackHttpServer server = new PackHttpServer(
                service.mergeFolder(), config.httpBind(), config.httpPort(), getSLF4JLogger());
        try {
            server.start();
            this.http = server;
            service.setHttpServer(server);
        } catch (IOException e) {
            getSLF4JLogger().error("Could not start RRP's HTTP server on {}:{} — combined packs "
                    + "will not be reachable. {}", config.httpBind(), config.httpPort(),
                    e.getMessage());
        }
    }

    private void scheduleCatalogRefresh() {
        int minutes = config.refreshMinutes();
        if (minutes <= 0) {
            return;
        }
        long ticks = minutes * 60L * 20L;
        getServer().getScheduler().runTaskTimer(this,
                () -> service.refreshCatalog(RrpService.Callback.NONE), ticks, ticks);
    }

    /**
     * Proves at startup that RRP works, rather than only that it loaded: the store must be
     * readable, every installed pack must still have its local copy, and the combined pack —
     * if one is configured — must exist and be reachable.
     */
    private void runStartupSelfCheck() {
        int installed = service.store().all().size();
        int active = service.store().active().size();
        int missing = 0;
        for (var pack : service.store().all()) {
            if (pack.localCopy().isPresent()
                    && !Files.isRegularFile(service.packFolder().resolve(pack.localCopy().get()))) {
                missing++;
                getSLF4JLogger().warn("The local copy of '{}' is gone — it can still be sent to "
                        + "players, but it cannot be combined. Reinstall it to fix that.",
                        pack.id());
            }
        }
        boolean guiOk = renderEveryScreenOnce();
        String mergeState;
        if (service.merged().isPresent()) {
            var merged = service.merged().get();
            mergeState = merged.url().isBlank()
                    ? "combined, but not reachable (set http.public-url)"
                    : "combined → " + merged.url();
            if (!merged.conflicts().isEmpty()) {
                getSLF4JLogger().warn("The combined pack has {} overlapping file(s); the pack "
                        + "applied last wins. First few: {}", merged.conflicts().size(),
                        merged.conflicts().subList(0, Math.min(3, merged.conflicts().size())));
            }
        } else {
            mergeState = active > 1 ? "stacked (" + active + " packs)" : "single pack";
        }
        Banner.print(getComponentLogger(), getPluginMeta().getVersion(), Bukkit.getMinecraftVersion(),
                service.catalog().packs().size(), installed, active, mergeState,
                missing == 0 && guiOk);
    }

    /**
     * Builds and draws every menu once, without showing it to anybody.
     *
     * <p>A GUI that throws while rendering is invisible until a player happens to open exactly
     * that screen, which is far too late. Drawing them all at startup — inventories can be
     * created without a viewer — turns "the menus compile" into "the menus actually render on
     * this server, with this server's items".
     *
     * @return true when every screen drew without an exception
     */
    private boolean renderEveryScreenOnce() {
        MainMenu main = new MainMenu(service, menus);
        java.util.List<de.raindancer.rrp.gui.RrpMenu> screens = new java.util.ArrayList<>(
                java.util.List.of(
                        main,
                        new de.raindancer.rrp.gui.InstalledMenu(service, menus, main, 0),
                        new de.raindancer.rrp.gui.CatalogMenu(service, menus, main, 0),
                        new de.raindancer.rrp.gui.GiveMenu(service, menus, main, 0),
                        new de.raindancer.rrp.gui.SettingsMenu(service, menus, main),
                        new de.raindancer.rrp.gui.ConfirmMenu(menus, main, "Self-check",
                                "Rendered at startup, never shown.", player -> {
                                })));
        service.store().all().stream().findFirst().ifPresent(pack -> screens.add(
                new de.raindancer.rrp.gui.PackDetailMenu(service, menus, main, pack.id())));

        for (var screen : screens) {
            try {
                screen.render();
            } catch (RuntimeException e) {
                getSLF4JLogger().error("Self-check FAILED: the {} screen could not be drawn. "
                        + "The GUI is broken on this server.", screen.getClass().getSimpleName(), e);
                return false;
            }
        }
        getSLF4JLogger().info("Self-check: {} screens rendered cleanly.", screens.size());
        return true;
    }

    // --- shared services -------------------------------------------------------------------

    public RrpConfig config() {
        return config;
    }

    public Downloader downloader() {
        return downloader;
    }

    public RrpService service() {
        return service;
    }

    public String userAgent() {
        return userAgent;
    }

    /** Re-reads {@code config.yml} and swaps the snapshot atomically. */
    public void reloadRrpConfig() {
        reloadConfig();
        this.config = RrpConfig.from(getConfig());
        this.downloader = new Downloader(config, userAgent);
    }

    /**
     * Writes a single config value, saves the file and reloads the snapshot.
     *
     * <p>This is what makes "everything the commands can do, the GUI can do too" hold for
     * settings as well — no hand editing of {@code config.yml} for the common switches.
     */
    public void setConfigValue(String path, Object value) {
        getConfig().set(path, value);
        saveConfig();
        reloadRrpConfig();
    }

    /** Restarts the HTTP server after its configuration changed. */
    public void restartHttpServer() {
        if (http != null) {
            http.stop();
            http = null;
            service.setHttpServer(null);
        }
        startHttpServer();
    }

    /** Runs {@code task} on RRP's worker thread. */
    public void runAsync(Runnable task) {
        if (worker == null || worker.isShutdown()) {
            getSLF4JLogger().warn("Ignoring a background task because RRP is shutting down.");
            return;
        }
        worker.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                getSLF4JLogger().error("A background task failed", e);
            }
        });
    }

    /** Runs {@code task} on the main server thread, or immediately when already there. */
    public void runOnMain(Runnable task) {
        if (!isEnabled()) {
            return;
        }
        if (getServer().isPrimaryThread()) {
            task.run();
            return;
        }
        getServer().getScheduler().runTask(this, task);
    }

    /** Console, RCON and command blocks are trusted by the server itself; players need the node. */
    public boolean hasAdminPermission(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return true;
        }
        return sender.isOp() || sender.hasPermission(PERMISSION);
    }

    public boolean hasGivePermission(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return true;
        }
        return sender.isOp() || sender.hasPermission(PERMISSION_GIVE)
                || sender.hasPermission(PERMISSION);
    }

    /** The message shown when someone lacks the permission for what they tried. */
    public static net.kyori.adventure.text.Component noPermission() {
        return Msg.error("You need to be an operator to use RRP.");
    }
}
