package de.raindancer.rrp.gui;

import java.util.Locale;

import de.raindancer.rrp.core.RrpConfig;
import de.raindancer.rrp.core.RrpService;
import de.raindancer.rrp.util.Msg;
import org.bukkit.Material;

/**
 * Every setting that matters, as buttons — the GUI half of {@code /rrp set}.
 *
 * <p>Each click writes {@code config.yml} and reloads the snapshot, so the GUI and the file never
 * drift apart.
 */
public final class SettingsMenu extends RrpMenu {

    private final RrpService service;
    private final MenuManager menus;
    private final RrpMenu parent;

    public SettingsMenu(RrpService service, MenuManager menus, RrpMenu parent) {
        super(5, MenuManager.title("settings", ""));
        this.service = service;
        this.menus = menus;
        this.parent = parent;
    }

    @Override
    public RrpMenu parent() {
        return parent;
    }

    @Override
    public void render() {
        clear();
        RrpConfig config = service.plugin().config();

        set(10, Icon.of(Material.COMPARATOR)
                .title("<" + Msg.ACCENT + ">Mode: <mode>",
                        Msg.arg("mode", config.mode().name().toLowerCase(Locale.ROOT)))
                .lore("auto — combine when it is possible")
                .lore("merged — always combine")
                .lore("stacked — always send them one by one")
                .blank()
                .action("Click to cycle")
                .build(), (player, click) -> {
                    RrpConfig.ApplyMode next = switch (config.mode()) {
                        case AUTO -> RrpConfig.ApplyMode.MERGED;
                        case MERGED -> RrpConfig.ApplyMode.STACKED;
                        case STACKED -> RrpConfig.ApplyMode.AUTO;
                    };
                    service.plugin().setConfigValue("apply.mode", next.name().toLowerCase(Locale.ROOT));
                    service.rebuildMerge((success, message) -> {
                        player.sendMessage(message);
                        refresh();
                    });
                });

        set(12, toggle(config.applyOnJoin(), Material.OAK_DOOR, "Send on join")
                .lore("Players receive the packs a second after joining.")
                .build(), (player, click) -> {
                    service.plugin().setConfigValue("apply.on-join", !config.applyOnJoin());
                    refresh();
                });

        set(14, toggle(config.requiredByDefault(), Material.BARRIER, "New packs required")
                .lore("What a freshly installed pack's 'required' flag starts as.")
                .build(), (player, click) -> {
                    service.plugin().setConfigValue("apply.required-by-default",
                            !config.requiredByDefault());
                    refresh();
                });

        set(16, toggle(config.autoInstallDatapacks(), Material.KNOWLEDGE_BOOK, "Install datapacks")
                .lore("Install the datapack half together with a pack.")
                .build(), (player, click) -> {
                    service.plugin().setConfigValue("datapacks.auto-install",
                            !config.autoInstallDatapacks());
                    refresh();
                });

        set(28, Icon.of(config.combineMode() == RrpConfig.CombineMode.REMOTE
                        ? Material.ENDER_CHEST : Material.CRAFTING_TABLE)
                .title("<" + Msg.ACCENT + ">Combined by: <who>",
                        Msg.arg("who", config.combineMode() == RrpConfig.CombineMode.REMOTE
                                ? "the pack host" : "this server"))
                .lore("remote — the host merges and serves it over its own HTTPS")
                .lore("local — merge here and serve it via the built-in HTTP server")
                .blank()
                .action("Click to switch")
                .build(), (player, click) -> {
                    RrpConfig.CombineMode next =
                            config.combineMode() == RrpConfig.CombineMode.REMOTE
                                    ? RrpConfig.CombineMode.LOCAL : RrpConfig.CombineMode.REMOTE;
                    service.plugin().setConfigValue("combine.mode",
                            next.name().toLowerCase(Locale.ROOT));
                    service.rebuildMerge((success, message) -> {
                        player.sendMessage(message);
                        refresh();
                    });
                });

        set(37, toggle(config.httpEnabled(), Material.REDSTONE_TORCH, "Built-in HTTP server")
                .lore("Only needed with local combining. Port <port>.",
                        Msg.num("port", config.httpPort()))
                .lore(service.httpServer().isPresent() ? "running" : "not running")
                .build(), (player, click) -> {
                    service.plugin().setConfigValue("http.enabled", !config.httpEnabled());
                    service.plugin().restartHttpServer();
                    service.rebuildMerge((success, message) -> {
                        player.sendMessage(message);
                        refresh();
                    });
                });

        set(30, Icon.of(Material.NAME_TAG)
                .title("<" + Msg.ACCENT + ">Public URL")
                .lore("<url>", Msg.arg("url",
                        config.publicBaseUrl().isBlank() ? "not set" : config.publicBaseUrl()))
                .lore("Where clients can reach the combined pack.")
                .blank()
                .action("Click to type a new one")
                .build(), (player, click) -> menus.promptForText(player,
                        "the public base URL, e.g. http://mc.example.com:8124",
                        url -> {
                            service.plugin().setConfigValue("http.public-url", url);
                            service.rebuildMerge((success, message) -> {
                                player.sendMessage(message);
                                menus.open(player, this);
                            });
                        },
                        () -> menus.open(player, this)));

        set(32, Icon.of(Material.HOPPER)
                .title("<" + Msg.ACCENT + ">HTTP port: <port>", Msg.num("port", config.httpPort()))
                .lore("Restarts the built-in server when changed.")
                .blank()
                .action("Click to type a new port")
                .build(), (player, click) -> menus.promptForText(player, "a port number",
                        text -> {
                            try {
                                int port = Integer.parseInt(text.trim());
                                if (port < 1 || port > 65535) {
                                    throw new NumberFormatException("out of range");
                                }
                                service.plugin().setConfigValue("http.port", port);
                                service.plugin().restartHttpServer();
                                player.sendMessage(Msg.success("HTTP port set to <port>.",
                                        Msg.num("port", port)));
                            } catch (NumberFormatException e) {
                                player.sendMessage(Msg.error("'<text>' is not a valid port.",
                                        Msg.arg("text", text)));
                            }
                            menus.open(player, this);
                        },
                        () -> menus.open(player, this)));

        set(34, Icon.of(Material.WRITABLE_BOOK)
                .title("<" + Msg.ACCENT + ">Catalogue URL")
                .lore("<url>", Msg.arg("url", config.catalogUrl()))
                .blank()
                .action("Click to type a new one")
                .build(), (player, click) -> menus.promptForText(player, "the catalogue URL",
                        url -> {
                            service.plugin().setConfigValue("catalog.url", url);
                            service.refreshCatalog((success, message) -> {
                                player.sendMessage(message);
                                menus.open(player, this);
                            });
                        },
                        () -> menus.open(player, this)));

        fillFooterBackground();
        drawBackOrClose(menus, size() - 9);
        set(size() - 1, Icon.of(Material.CLOCK)
                .title("<" + Msg.ACCENT + ">Reload config.yml")
                .lore("Re-reads the file from disk.")
                .build(), (player, click) -> {
                    service.plugin().reloadRrpConfig();
                    player.sendMessage(Msg.success("Configuration reloaded."));
                    refresh();
                });
    }

    private Icon toggle(boolean on, Material material, String label) {
        return Icon.of(on ? material : Material.GRAY_DYE)
                .title("<" + (on ? Msg.OK : Msg.MUTED) + "><label>: <state>",
                        Msg.arg("label", label), Msg.arg("state", on ? "on" : "off"));
    }
}
