package de.raindancer.rrp.gui;

import java.util.List;

import de.raindancer.rrp.core.RrpService;
import de.raindancer.rrp.pack.InstalledPack;
import de.raindancer.rrp.util.Msg;
import org.bukkit.Material;

/** The installed packs, in the order they are applied. */
public final class InstalledMenu extends PagedMenu {

    private final RrpService service;

    public InstalledMenu(RrpService service, MenuManager menus, RrpMenu parent, int page) {
        super(MenuManager.title("Installed packs", ""), menus, parent, page);
        this.service = service;
    }

    @Override
    protected int entryCount() {
        return service.store().all().size();
    }

    @Override
    protected void drawEntry(int index, int slot) {
        List<InstalledPack> packs = service.store().all();
        InstalledPack pack = packs.get(index);
        Icon icon = Icon.of(pack.enabled() ? Material.LIME_SHULKER_BOX : Material.GRAY_SHULKER_BOX)
                .title("<" + (pack.enabled() ? Msg.OK : Msg.MUTED) + "><name>",
                        Msg.arg("name", pack.name()))
                .lore("id <id>", Msg.arg("id", pack.id()))
                .lore("<state> · <required> · <size>",
                        Msg.arg("state", pack.enabled() ? "active" : "inactive"),
                        Msg.arg("required", pack.required() ? "required" : "optional"),
                        Msg.arg("size", Msg.bytes(pack.size())))
                .lore("datapack <datapack>",
                        Msg.arg("datapack", pack.datapackInstalled() ? "installed" : "not installed"))
                .lore("position <pos> of <total>",
                        Msg.num("pos", index + 1L), Msg.num("total", packs.size()))
                .blank()
                .action("Open");
        set(slot, icon.build(), (player, click) ->
                menus.open(player, new PackDetailMenu(service, menus, this, pack.id())));
    }

    @Override
    protected void drawExtras() {
        set(size() - 1, Icon.of(Material.ENDER_EYE)
                .title("<" + Msg.ACCENT + ">Send to everyone")
                .lore("Re-sends the active packs to every player online.")
                .build(), (player, click) -> {
                    var result = service.applyToAll();
                    Msg.tell(player, Msg.success("Sent <count> pack(s) (<how>).",
                            Msg.num("count", result.packsSent()),
                            Msg.arg("how", result.combined() ? "combined" : result.reason())));
                });
    }

    @Override
    protected org.bukkit.inventory.ItemStack emptyIcon() {
        return Icon.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                .title("<" + Msg.MUTED + ">No packs installed")
                .lore("Open the catalogue to install one.")
                .build();
    }
}
