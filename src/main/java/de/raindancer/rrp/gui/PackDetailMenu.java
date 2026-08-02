package de.raindancer.rrp.gui;

import java.util.Optional;

import de.raindancer.rrp.core.RrpService;
import de.raindancer.rrp.pack.InstalledPack;
import de.raindancer.rrp.util.Msg;
import org.bukkit.Material;

/** One installed pack: everything {@code /rrp <pack>} can do, as buttons. */
public final class PackDetailMenu extends RrpMenu {

    private final RrpService service;
    private final MenuManager menus;
    private final RrpMenu parent;
    private final String packId;

    public PackDetailMenu(RrpService service, MenuManager menus, RrpMenu parent, String packId) {
        super(5, MenuManager.title("Pack", packId));
        this.service = service;
        this.menus = menus;
        this.parent = parent;
        this.packId = packId;
    }

    @Override
    public RrpMenu parent() {
        return parent;
    }

    @Override
    public void render() {
        clear();
        Optional<InstalledPack> entry = service.store().get(packId);
        if (entry.isEmpty()) {
            set(22, Icon.of(Material.BARRIER)
                    .title("<" + Msg.BAD + ">This pack is gone")
                    .build());
            fillFooterBackground();
            drawBackOrClose(menus, size() - 1);
            return;
        }
        InstalledPack pack = entry.get();

        set(4, Icon.of(Material.SHULKER_BOX)
                .title("<" + Msg.ACCENT + "><name>", Msg.arg("name", pack.name()))
                .lore("id <id>", Msg.arg("id", pack.id()))
                .lore("sha1 <sha1>", Msg.arg("sha1", shortHash(pack.sha1())))
                .lore("size <size>", Msg.arg("size", Msg.bytes(pack.size())))
                .lore("from <source>", Msg.arg("source", pack.source()))
                .build());

        set(10, toggle(pack.enabled(), "Active", "Inactive",
                        "Active packs are sent to players.")
                .build(), (player, click) -> service.setEnabled(pack.id(), !pack.enabled(), feedback(player)));

        set(12, toggle(pack.required(), "Required", "Optional",
                        "Required packs must be accepted by the client.")
                .build(), (player, click) -> service.setRequired(pack.id(), !pack.required(), feedback(player)));

        set(14, Icon.of(pack.datapackInstalled() ? Material.KNOWLEDGE_BOOK : Material.BOOK)
                .title("<" + (pack.datapackInstalled() ? Msg.OK : Msg.MUTED) + ">Datapack <state>",
                        Msg.arg("state", pack.datapackInstalled() ? "installed" : "not installed"))
                .lore("The server-side half: custom items, songs and enchantments.")
                .blank()
                .action(pack.datapackInstalled() ? "Remove it" : "Install it")
                .build(), (player, click) -> {
                    if (pack.datapackInstalled()) {
                        service.removeDatapack(pack.id(), feedback(player));
                    } else {
                        service.installDatapack(pack.id(), feedback(player));
                    }
                });

        set(16, Icon.of(Material.ENDER_EYE)
                .title("<" + Msg.ACCENT + ">Send to everyone")
                .lore("Applies the active packs again, right now.")
                .build(), (player, click) -> {
                    service.applyToAll();
                    Msg.tell(player, Msg.success("Sent the active packs to everyone online."));
                });

        set(20, Icon.of(Material.SPECTRAL_ARROW)
                .title("<" + Msg.ACCENT + ">Move up")
                .lore("Earlier packs lose against later ones when files overlap.")
                .build(), (player, click) -> service.move(pack.id(), -1, feedback(player)));

        set(24, Icon.of(Material.ARROW)
                .title("<" + Msg.ACCENT + ">Move down")
                .lore("Later packs win when files overlap.")
                .build(), (player, click) -> service.move(pack.id(), 1, feedback(player)));

        fillFooterBackground();
        drawBackOrClose(menus, size() - 9);
        set(size() - 5, Icon.of(Material.LAVA_BUCKET)
                .title("<" + Msg.BAD + ">Uninstall")
                .lore("Removes the pack, its local copy and its datapack half.")
                .blank()
                .danger("Click to confirm")
                .build(), (player, click) -> menus.open(player, new ConfirmMenu(
                        menus, this,
                        "Uninstall " + pack.name() + "?",
                        "The pack, its local copy and its datapack half are removed. "
                                + "World data is not touched.",
                        confirmer -> service.uninstall(pack.id(), (success, message) -> {
                            Msg.tell(confirmer, message);
                            menus.open(confirmer, parent == null ? this : parent);
                        }))));
    }

    private Icon toggle(boolean on, String onLabel, String offLabel, String help) {
        return Icon.of(on ? Material.LIME_DYE : Material.GRAY_DYE)
                .title("<" + (on ? Msg.OK : Msg.MUTED) + "><label>",
                        Msg.arg("label", on ? onLabel : offLabel))
                .lore(help)
                .blank()
                .action("Switch to " + (on ? offLabel.toLowerCase(java.util.Locale.ROOT)
                        : onLabel.toLowerCase(java.util.Locale.ROOT)));
    }

    private RrpService.Callback feedback(org.bukkit.entity.Player player) {
        return (success, message) -> {
            Msg.tell(player, message);
            if (player.isOnline()) {
                refresh();
            }
        };
    }

    private static String shortHash(String sha1) {
        return sha1 == null || sha1.length() < 12 ? String.valueOf(sha1) : sha1.substring(0, 12);
    }
}
