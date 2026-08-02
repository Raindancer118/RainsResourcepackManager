package de.raindancer.rrp.gui;

import de.raindancer.rrp.core.RrpConfig;
import de.raindancer.rrp.core.RrpService;
import de.raindancer.rrp.util.Msg;
import org.bukkit.Material;

/**
 * The root screen: what is installed, what could be, what gets sent to players, and the way
 * into every other screen.
 */
public final class MainMenu extends RrpMenu {

    private final RrpService service;
    private final MenuManager menus;

    public MainMenu(RrpService service, MenuManager menus) {
        super(5, MenuManager.title("Resource packs", ""));
        this.service = service;
        this.menus = menus;
    }

    @Override
    public void render() {
        clear();
        RrpConfig config = service.plugin().config();
        int installed = service.store().all().size();
        int active = service.store().active().size();
        int available = service.available().size();

        set(11, Icon.of(Material.CHEST)
                .title("<" + Msg.ACCENT + ">Installed packs")
                .lore("<installed> installed, <active> active",
                        Msg.num("installed", installed), Msg.num("active", active))
                .blank()
                .action("Open")
                .build(), (player, click) -> menus.open(player, new InstalledMenu(service, menus, this, 0)));

        set(13, Icon.of(Material.WRITABLE_BOOK)
                .title("<" + Msg.ACCENT + ">Catalogue")
                .lore("<count> pack(s) not installed yet", Msg.num("count", available))
                .lore("from <url>", Msg.arg("url", host(config.catalogUrl())))
                .blank()
                .action("Browse and install")
                .build(), (player, click) -> menus.open(player, new CatalogMenu(service, menus, this, 0)));

        set(15, Icon.of(Material.MUSIC_DISC_13)
                .title("<" + Msg.ACCENT + ">Items")
                .lore("<count> item(s) the installed packs add",
                        Msg.num("count", service.catalog().allItems().size()))
                .blank()
                .action("Hand them out")
                .build(), (player, click) -> menus.open(player, new GiveMenu(service, menus, this, 0)));

        set(29, Icon.of(Material.COMPARATOR)
                .title("<" + Msg.ACCENT + ">Settings")
                .lore("mode <mode>, on join <join>",
                        Msg.arg("mode", config.mode().name().toLowerCase(java.util.Locale.ROOT)),
                        Msg.arg("join", config.applyOnJoin() ? "yes" : "no"))
                .blank()
                .action("Open")
                .build(), (player, click) -> menus.open(player, new SettingsMenu(service, menus, this)));

        set(31, Icon.of(Material.ANVIL)
                .title("<" + Msg.ACCENT + ">Combined pack")
                .lore(mergeLine())
                .blank()
                .action("Rebuild now")
                .build(), (player, click) -> service.rebuildMerge((success, message) -> {
                    Msg.tell(player, message);
                    refresh();
                }));

        set(33, Icon.of(Material.ENDER_EYE)
                .title("<" + Msg.ACCENT + ">Send to everyone")
                .lore("Re-sends the active packs to every player online.")
                .blank()
                .action("Apply now")
                .build(), (player, click) -> {
                    var result = service.applyToAll();
                    Msg.tell(player, Msg.success("Sent <count> pack(s) to everyone online (<how>).",
                            Msg.num("count", result.packsSent()),
                            Msg.arg("how", result.combined() ? "combined" : result.reason())));
                });

        fillFooterBackground();
        set(size() - 5, Icon.of(Material.CLOCK)
                .title("<" + Msg.ACCENT + ">Refresh catalogue")
                .lore("Fetch the pack list from the host again.")
                .build(), (player, click) -> service.refreshCatalog((success, message) -> {
                    Msg.tell(player, message);
                    refresh();
                }));
        drawBackOrClose(menus, size() - 1);
    }

    private String mergeLine() {
        return service.merged()
                .map(merged -> merged.url().isBlank()
                        ? "built, but no public URL is set"
                        : merged.packIds().size() + " packs · " + (merged.remote()
                                ? "built by the pack host" : "built here")
                                + " · sha1 " + merged.sha1().substring(0, 12))
                .orElse("not in use — packs are sent stacked");
    }

    private static String host(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (RuntimeException e) {
            return url;
        }
    }
}
