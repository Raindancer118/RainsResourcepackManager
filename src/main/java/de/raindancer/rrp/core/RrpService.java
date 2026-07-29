package de.raindancer.rrp.core;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.raindancer.rrp.RrpPlugin;
import de.raindancer.rrp.catalog.Catalog;
import de.raindancer.rrp.catalog.CatalogItem;
import de.raindancer.rrp.catalog.CatalogPack;
import de.raindancer.rrp.catalog.CatalogService;
import de.raindancer.rrp.give.GiveService;
import de.raindancer.rrp.pack.ApplyService;
import de.raindancer.rrp.pack.DatapackService;
import de.raindancer.rrp.pack.InstalledPack;
import de.raindancer.rrp.pack.MergedPack;
import de.raindancer.rrp.pack.PackHttpServer;
import de.raindancer.rrp.pack.PackMerger;
import de.raindancer.rrp.pack.PackStore;
import de.raindancer.rrp.util.Downloader;
import de.raindancer.rrp.util.Msg;
import de.raindancer.rrp.util.SafeFileName;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

/**
 * Everything RRP can do, in one place.
 *
 * <p>The command tree and the GUI are both thin front ends over this class — "every command has
 * a GUI equivalent" is therefore true by construction rather than by discipline. Long running
 * work (downloads, merging) is run on the plugin's worker thread and reports back on the main
 * thread through {@link Callback}.
 */
public final class RrpService {

    /**
     * Datapack registries — enchantments, jukebox songs, damage types — are built once when the
     * server starts. {@code /minecraft:reload} rebuilds functions, tags, loot and advancements,
     * but not those registries, so a freshly installed datapack that adds one is only half live
     * until the next restart. Saying so is better than letting an operator wonder why their new
     * enchantment does not exist yet.
     */
    private static final Component RESTART_NOTE = Msg.warn(
            "The datapack is in place, but entries it adds to datapack registries "
            + "(enchantments, jukebox songs) only exist after a server restart.");

    /** Reports the outcome of an operation, always on the main thread. */
    public interface Callback {
        void done(boolean success, Component message);

        /** A callback that goes nowhere, for fire-and-forget calls. */
        Callback NONE = (success, message) -> {
        };
    }

    private final RrpPlugin plugin;
    private final PackStore store;
    private final CatalogService catalogs;
    private final DatapackService datapacks;
    private final ApplyService apply;
    private final PackMerger merger;
    private final GiveService give = new GiveService();
    private final Logger log;

    private volatile Optional<MergedPack> merged = Optional.empty();
    private volatile PackHttpServer http;

    public RrpService(RrpPlugin plugin, PackStore store, CatalogService catalogs,
                      DatapackService datapacks, PackMerger merger, Logger log) {
        this.plugin = plugin;
        this.store = store;
        this.catalogs = catalogs;
        this.datapacks = datapacks;
        this.merger = merger;
        this.log = log;
        this.apply = new ApplyService(store, plugin::config, this::merged, log);
    }

    // --- state -----------------------------------------------------------------------------

    public RrpPlugin plugin() {
        return plugin;
    }

    public PackStore store() {
        return store;
    }

    public Catalog catalog() {
        return catalogs.catalog();
    }

    public ApplyService applyService() {
        return apply;
    }

    public Optional<MergedPack> merged() {
        return merged;
    }

    public Optional<PackHttpServer> httpServer() {
        return Optional.ofNullable(http);
    }

    public void setHttpServer(PackHttpServer server) {
        this.http = server;
    }

    /** Packs from the catalogue that are not installed yet. */
    public List<CatalogPack> available() {
        return catalog().packs().stream()
                .filter(pack -> !store.has(pack.id()))
                .toList();
    }

    // --- catalogue -------------------------------------------------------------------------

    /** Refreshes the catalogue in the background. */
    public void refreshCatalog(Callback callback) {
        String url = plugin.config().catalogUrl();
        plugin.runAsync(() -> {
            try {
                Catalog fresh = catalogs.refresh(url);
                plugin.runOnMain(() -> callback.done(true, Msg.success(
                        "Catalogue refreshed: <count> pack(s) available.",
                        Msg.num("count", fresh.packs().size()))));
            } catch (Downloader.DownloadException e) {
                plugin.runOnMain(() -> callback.done(false, Msg.error(
                        "Could not refresh the catalogue: <detail>",
                        Msg.arg("detail", e.getMessage()))));
            }
        });
    }

    // --- installing ------------------------------------------------------------------------

    /**
     * Installs a pack from the catalogue: downloads the resource pack, optionally installs the
     * datapack half, rebuilds the combined pack and sends the result to everyone online.
     */
    public void install(String packId, Callback callback) {
        Optional<CatalogPack> entry = catalog().find(packId);
        if (entry.isEmpty()) {
            callback.done(false, Msg.error("No pack called '<id>' in the catalogue. "
                    + "Try /rrp catalog refresh.", Msg.arg("id", packId)));
            return;
        }
        CatalogPack pack = entry.get();
        if (store.has(pack.id())) {
            callback.done(false, Msg.error("'<id>' is already installed.", Msg.arg("id", pack.id())));
            return;
        }
        if (pack.resourcepack().isEmpty()) {
            callback.done(false, Msg.error("'<id>' has no resource pack to install.",
                    Msg.arg("id", pack.id())));
            return;
        }

        RrpConfig config = plugin.config();
        CatalogPack.Zip zip = pack.resourcepack().get();
        plugin.runAsync(() -> {
            try {
                Downloader downloader = plugin.downloader();
                URI uri = downloader.validate(zip.url());
                Path target = packFolder().resolve(SafeFileName.of(pack.id(), "pack") + ".zip");
                Downloader.Result result = downloader.download(uri, target,
                        zip.sha1().isBlank() ? Optional.empty() : Optional.of(zip.sha1()));

                Optional<Path> datapackFile = Optional.empty();
                if (config.autoInstallDatapacks() && pack.datapack().isPresent()) {
                    CatalogPack.Zip dp = pack.datapack().get();
                    URI dpUri = downloader.validate(dp.url());
                    Path dpTarget = packFolder()
                            .resolve(SafeFileName.of(pack.id(), "pack") + "-datapack.zip");
                    downloader.download(dpUri, dpTarget,
                            dp.sha1().isBlank() ? Optional.empty() : Optional.of(dp.sha1()));
                    datapackFile = Optional.of(dpTarget);
                }

                Optional<Path> finalDatapack = datapackFile;
                plugin.runOnMain(() -> {
                    String datapackName = "";
                    boolean datapackInstalled = false;
                    if (finalDatapack.isPresent()) {
                        try {
                            datapackName = datapacks.install(finalDatapack.get(), pack.id());
                            datapackInstalled = true;
                            if (config.reloadAfterDatapackInstall()) {
                                datapacks.reloadData();
                            }
                        } catch (IOException | IllegalStateException e) {
                            log.warn("Could not install the datapack of {}: {}",
                                    pack.id(), e.getMessage());
                        }
                    }

                    store.put(new InstalledPack(
                            pack.id(),
                            pack.name(),
                            zip.url(),
                            result.sha1(),
                            result.bytes(),
                            target.getFileName().toString(),
                            true,
                            config.requiredByDefault(),
                            store.nextOrder(),
                            datapackInstalled,
                            datapackName,
                            "catalog",
                            System.currentTimeMillis()));

                    boolean withDatapack = datapackInstalled;
                    rebuildMerge((ok, message) -> {
                        applyToAll();
                        callback.done(true, Msg.success(
                                "Installed <name> (<size><datapack>) and sent it to everyone online.",
                                Msg.arg("name", pack.name()),
                                Msg.arg("size", Msg.bytes(result.bytes())),
                                Msg.arg("datapack", withDatapack ? ", datapack included" : "")));
                        if (withDatapack) {
                            callback.done(true, RESTART_NOTE);
                        }
                    });
                });
            } catch (Downloader.DownloadException e) {
                plugin.runOnMain(() -> callback.done(false, Msg.error("Install failed: <detail>",
                        Msg.arg("detail", e.getMessage()))));
            }
        });
    }

    /** Installs a pack from a plain URL, for packs that are not in the catalogue. */
    public void installFromUrl(String rawUrl, String rawId, Callback callback) {
        String id = SafeFileName.of(rawId, "").toLowerCase(Locale.ROOT);
        if (id.isBlank()) {
            callback.done(false, Msg.error("That id cannot be used as a pack name."));
            return;
        }
        if (store.has(id)) {
            callback.done(false, Msg.error("'<id>' is already installed.", Msg.arg("id", id)));
            return;
        }
        RrpConfig config = plugin.config();
        plugin.runAsync(() -> {
            try {
                Downloader downloader = plugin.downloader();
                URI uri = downloader.validate(rawUrl);
                Path target = packFolder().resolve(id + ".zip");
                Downloader.Result result = downloader.download(uri, target, Optional.empty());
                plugin.runOnMain(() -> {
                    store.put(new InstalledPack(id, id, rawUrl, result.sha1(), result.bytes(),
                            target.getFileName().toString(), true, config.requiredByDefault(),
                            store.nextOrder(), false, "", "url", System.currentTimeMillis()));
                    rebuildMerge((ok, message) -> {
                        applyToAll();
                        callback.done(true, Msg.success("Installed <id> (<size>).",
                                Msg.arg("id", id), Msg.arg("size", Msg.bytes(result.bytes()))));
                    });
                });
            } catch (Downloader.DownloadException e) {
                plugin.runOnMain(() -> callback.done(false, Msg.error("Install failed: <detail>",
                        Msg.arg("detail", e.getMessage()))));
            }
        });
    }

    /**
     * Removes a pack: its local copy, its datapack half if RRP installed one, and its entry.
     *
     * <p>Only files RRP created are deleted — world data and datapacks installed by hand are
     * never touched.
     */
    public void uninstall(String packId, Callback callback) {
        Optional<InstalledPack> entry = store.get(packId);
        if (entry.isEmpty()) {
            callback.done(false, Msg.error("'<id>' is not installed.", Msg.arg("id", packId)));
            return;
        }
        InstalledPack pack = entry.get();

        boolean datapackRemoved = false;
        if (pack.datapackInstalled()) {
            try {
                datapackRemoved = datapacks.remove(pack.datapackFile());
            } catch (IOException | IllegalStateException e) {
                log.warn("Could not remove the datapack of {}: {}", pack.id(), e.getMessage());
            }
        }
        pack.localCopy().ifPresent(name -> {
            try {
                Files.deleteIfExists(packFolder().resolve(SafeFileName.of(name, "pack.zip")));
                Files.deleteIfExists(packFolder()
                        .resolve(SafeFileName.of(pack.id(), "pack") + "-datapack.zip"));
            } catch (IOException e) {
                log.warn("Could not delete the local copy of {}: {}", pack.id(), e.getMessage());
            }
        });
        store.remove(pack.id());

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            apply.remove(player, pack);
        }
        if (datapackRemoved && plugin.config().reloadAfterDatapackInstall()) {
            datapacks.reloadData();
        }

        boolean removedDatapack = datapackRemoved;
        rebuildMerge((ok, message) -> {
            applyToAll();
            callback.done(true, Msg.success("Removed <id><datapack>.",
                    Msg.arg("id", pack.id()),
                    Msg.arg("datapack", removedDatapack ? " and its datapack" : "")));
        });
    }

    // --- per-pack settings -----------------------------------------------------------------

    public void setEnabled(String packId, boolean enabled, Callback callback) {
        update(packId, pack -> pack.withEnabled(enabled), callback,
                enabled ? "<id> is now active." : "<id> is now inactive.");
    }

    public void setRequired(String packId, boolean required, Callback callback) {
        update(packId, pack -> pack.withRequired(required), callback,
                required
                        ? "<id> is now required — clients must accept it."
                        : "<id> is now optional.");
    }

    /** Moves a pack up or down in the application order. Later packs win on conflicts. */
    public void move(String packId, int direction, Callback callback) {
        List<InstalledPack> all = new ArrayList<>(store.all());
        int index = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equalsIgnoreCase(packId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            callback.done(false, Msg.error("'<id>' is not installed.", Msg.arg("id", packId)));
            return;
        }
        int target = index + (direction < 0 ? -1 : 1);
        if (target < 0 || target >= all.size()) {
            callback.done(false, Msg.warn("'<id>' is already at the <edge>.",
                    Msg.arg("id", packId), Msg.arg("edge", direction < 0 ? "top" : "bottom")));
            return;
        }
        InstalledPack moved = all.remove(index);
        all.add(target, moved);
        for (int i = 0; i < all.size(); i++) {
            store.put(all.get(i).withOrder((i + 1) * 10));
        }
        rebuildMerge((ok, message) -> {
            applyToAll();
            callback.done(true, Msg.success("Moved <id> <direction>.",
                    Msg.arg("id", moved.id()), Msg.arg("direction", direction < 0 ? "up" : "down")));
        });
    }

    private void update(String packId, java.util.function.UnaryOperator<InstalledPack> change,
                        Callback callback, String message) {
        Optional<InstalledPack> entry = store.get(packId);
        if (entry.isEmpty()) {
            callback.done(false, Msg.error("'<id>' is not installed.", Msg.arg("id", packId)));
            return;
        }
        InstalledPack updated = change.apply(entry.get());
        store.put(updated);
        rebuildMerge((ok, detail) -> {
            applyToAll();
            callback.done(true, Msg.success(message, Msg.arg("id", updated.id())));
        });
    }

    // --- datapack half ---------------------------------------------------------------------

    /** Installs the datapack half of an already installed pack. */
    public void installDatapack(String packId, Callback callback) {
        Optional<InstalledPack> entry = store.get(packId);
        if (entry.isEmpty()) {
            callback.done(false, Msg.error("'<id>' is not installed.", Msg.arg("id", packId)));
            return;
        }
        Optional<CatalogPack> catalogEntry = catalog().find(packId);
        if (catalogEntry.isEmpty() || catalogEntry.get().datapack().isEmpty()) {
            callback.done(false, Msg.error("The catalogue lists no datapack for '<id>'.",
                    Msg.arg("id", packId)));
            return;
        }
        CatalogPack.Zip zip = catalogEntry.get().datapack().get();
        plugin.runAsync(() -> {
            try {
                Downloader downloader = plugin.downloader();
                URI uri = downloader.validate(zip.url());
                Path target = packFolder()
                        .resolve(SafeFileName.of(packId, "pack") + "-datapack.zip");
                downloader.download(uri, target,
                        zip.sha1().isBlank() ? Optional.empty() : Optional.of(zip.sha1()));
                plugin.runOnMain(() -> {
                    try {
                        String name = datapacks.install(target, packId);
                        store.put(entry.get().withDatapack(true, name));
                        if (plugin.config().reloadAfterDatapackInstall()) {
                            datapacks.reloadData();
                            callback.done(true, Msg.success(
                                    "Installed the datapack of <id> and reloaded data packs.",
                                    Msg.arg("id", packId)));
                            callback.done(true, RESTART_NOTE);
                        } else {
                            callback.done(true, Msg.success(
                                    "Installed the datapack of <id> — restart to activate it.",
                                    Msg.arg("id", packId)));
                        }
                    } catch (IOException | IllegalStateException e) {
                        callback.done(false, Msg.error("Could not install the datapack: <detail>",
                                Msg.arg("detail", String.valueOf(e.getMessage()))));
                    }
                });
            } catch (Downloader.DownloadException e) {
                plugin.runOnMain(() -> callback.done(false,
                        Msg.error("Download failed: <detail>", Msg.arg("detail", e.getMessage()))));
            }
        });
    }

    /** Removes the datapack half, leaving the resource pack installed. */
    public void removeDatapack(String packId, Callback callback) {
        Optional<InstalledPack> entry = store.get(packId);
        if (entry.isEmpty() || !entry.get().datapackInstalled()) {
            callback.done(false, Msg.error("RRP has no datapack installed for '<id>'.",
                    Msg.arg("id", packId)));
            return;
        }
        try {
            boolean removed = datapacks.remove(entry.get().datapackFile());
            store.put(entry.get().withDatapack(false, ""));
            if (removed && plugin.config().reloadAfterDatapackInstall()) {
                datapacks.reloadData();
            }
            callback.done(true, Msg.success("Removed the datapack of <id>.", Msg.arg("id", packId)));
        } catch (IOException | IllegalStateException e) {
            callback.done(false, Msg.error("Could not remove the datapack: <detail>",
                    Msg.arg("detail", String.valueOf(e.getMessage()))));
        }
    }

    // --- combining -------------------------------------------------------------------------

    /**
     * Rebuilds the combined pack if the current settings call for one.
     *
     * <p>Never fails loudly: when combining is impossible the plugin falls back to sending the
     * packs stacked, which works everywhere, and says so.
     */
    public void rebuildMerge(Callback callback) {
        RrpConfig config = plugin.config();
        List<InstalledPack> active = store.active();
        if (config.mode() == RrpConfig.ApplyMode.STACKED || active.size() < 2) {
            merged = Optional.empty();
            // A combined pack left over from an earlier configuration would still be served —
            // and could still be handed to a client — so it goes.
            discardCombined();
            callback.done(true, Msg.info("Packs are sent stacked; nothing to combine."));
            return;
        }

        List<Path> sources = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (InstalledPack pack : active) {
            Optional<String> local = pack.localCopy();
            if (local.isEmpty()) {
                continue;
            }
            Path file = packFolder().resolve(SafeFileName.of(local.get(), "pack.zip"));
            if (Files.isRegularFile(file)) {
                sources.add(file);
                ids.add(pack.id());
            }
        }
        if (sources.size() < 2) {
            merged = Optional.empty();
            callback.done(false, Msg.warn(
                    "Not enough local pack copies to combine — sending them stacked instead."));
            return;
        }

        Path folder = mergeFolder();
        String description = config.mergeDescription();
        plugin.runAsync(() -> {
            try {
                PackMerger.Result result = merger.merge(sources, folder, description);
                String url = mergedUrl(result.file().getFileName().toString());
                plugin.runOnMain(() -> {
                    merged = Optional.of(new MergedPack(result.file(), result.sha1(), result.size(),
                            url, List.copyOf(ids), result.conflicts()));
                    if (url.isBlank()) {
                        callback.done(false, Msg.warn("Combined <count> packs, but no public URL "
                                + "is configured — set http.public-url (and enable http) or the "
                                + "packs will keep going out stacked.",
                                Msg.num("count", sources.size())));
                    } else {
                        callback.done(true, Msg.success(
                                "Combined <count> packs into one (<size>, sha1 <sha1>).",
                                Msg.num("count", sources.size()),
                                Msg.arg("size", Msg.bytes(result.size())),
                                Msg.arg("sha1", result.sha1().substring(0, 12))));
                    }
                });
            } catch (PackMerger.MergeException e) {
                plugin.runOnMain(() -> {
                    merged = Optional.empty();
                    callback.done(false, Msg.warn("Could not combine the packs: <detail> "
                            + "They will be sent stacked instead.",
                            Msg.arg("detail", e.getMessage())));
                });
            }
        });
    }

    /** Deletes every combined pack RRP built. Only ever RRP's own output. */
    private void discardCombined() {
        Path folder = mergeFolder();
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (var stream = Files.list(folder)) {
            for (Path path : stream.toList()) {
                if (path.getFileName().toString().startsWith("combined-")) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException e) {
            log.warn("Could not clean up {}: {}", folder, e.getMessage());
        }
    }

    /** @return the URL clients would use for the combined pack, or an empty string */
    private String mergedUrl(String fileName) {
        String base = plugin.config().publicBaseUrl();
        if (!base.isBlank()) {
            return base + PackHttpServer.PREFIX + fileName;
        }
        return "";
    }

    // --- applying --------------------------------------------------------------------------

    public ApplyService.Result applyTo(Player player) {
        return apply.apply(player);
    }

    public ApplyService.Result applyToAll() {
        return apply.applyAll(plugin.getServer().getOnlinePlayers());
    }

    // --- items -----------------------------------------------------------------------------

    public GiveService give() {
        return give;
    }

    /**
     * Hands a catalogue item to a player.
     *
     * <p>Warns — but still gives — when the item's pack is not installed: an operator testing a
     * pack before installing it is a legitimate thing to do, a silently wrong item is not.
     */
    public void giveItem(CatalogItem item, Player target, int amount, Callback callback) {
        try {
            ItemStack stack = give.build(item, amount);
            int dropped = give.give(target, stack);
            List<String> warnings = new ArrayList<>();
            Optional<InstalledPack> installed = store.get(item.pack());
            if (item.needs().needsResourcepack() && installed.isEmpty()) {
                warnings.add("'" + item.pack() + "' is not installed, so it will have no texture");
            } else if (item.needs().needsDatapack() && installed.isPresent()
                    && !installed.get().datapackInstalled()) {
                warnings.add("the datapack of '" + item.pack() + "' is not installed");
            }
            callback.done(true, Msg.success("Gave <amount>× <item> to <player><warning>.",
                    Msg.num("amount", stack.getAmount()),
                    Msg.arg("item", item.name()),
                    Msg.arg("player", target.getName()),
                    Msg.arg("warning", warnings.isEmpty()
                            ? (dropped > 0 ? " (" + dropped + " dropped, inventory full)" : "")
                            : " — but " + String.join(", ", warnings))));
        } catch (GiveService.GiveException e) {
            callback.done(false, Msg.error("<detail>", Msg.arg("detail", e.getMessage())));
        }
    }

    // --- paths -----------------------------------------------------------------------------

    public Path packFolder() {
        return plugin.getDataFolder().toPath().resolve("packs");
    }

    public Path mergeFolder() {
        return plugin.getDataFolder().toPath()
                .resolve(SafeFileName.of(plugin.config().mergeFolder(), "merged"));
    }
}
