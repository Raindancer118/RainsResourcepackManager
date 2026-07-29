package de.raindancer.rrp.gui;

import java.util.List;

import de.raindancer.rrp.catalog.CatalogPack;
import de.raindancer.rrp.core.RrpService;
import de.raindancer.rrp.util.Msg;
import org.bukkit.Material;

/** Packs the host offers that are not installed here yet. */
public final class CatalogMenu extends PagedMenu {

    private final RrpService service;

    public CatalogMenu(RrpService service, MenuManager menus, RrpMenu parent, int page) {
        super(MenuManager.title("catalogue", "available packs"), menus, parent, page);
        this.service = service;
    }

    @Override
    protected int entryCount() {
        return service.available().size();
    }

    @Override
    protected void drawEntry(int index, int slot) {
        List<CatalogPack> packs = service.available();
        CatalogPack pack = packs.get(index);
        Icon icon = Icon.of(Material.WHITE_SHULKER_BOX)
                .title("<" + Msg.ACCENT + "><name>", Msg.arg("name", pack.name()))
                .lore("id <id>", Msg.arg("id", pack.id()))
                .lore(Msg.wrapLore(pack.description(), 38, Msg.MUTED));
        pack.resourcepack().ifPresent(zip -> icon.lore("resource pack <size>",
                Msg.arg("size", Msg.bytes(zip.size()))));
        if (pack.datapack().isPresent()) {
            icon.lore("comes with a datapack");
        }
        if (!pack.items().isEmpty()) {
            icon.lore("adds <count> item(s)", Msg.num("count", pack.items().size()));
        }
        for (String module : pack.modules()) {
            icon.lore("+ <module>", Msg.arg("module", module));
        }
        icon.blank().action("Install");

        set(slot, icon.build(), (player, click) -> {
            player.sendMessage(Msg.info("Installing <name> …", Msg.arg("name", pack.name())));
            service.install(pack.id(), (success, message) -> {
                player.sendMessage(message);
                if (player.isOnline()) {
                    refresh();
                }
            });
        });
    }

    @Override
    protected void drawExtras() {
        set(size() - 1, Icon.of(Material.WRITABLE_BOOK)
                .title("<" + Msg.ACCENT + ">Install from a URL")
                .lore("For packs that are not in the catalogue.")
                .blank()
                .action("Type a URL in chat")
                .build(), (player, click) -> menus.promptForText(player,
                        "the pack URL (https://…/pack.zip)",
                        url -> menus.promptForText(player, "a short id for this pack",
                                id -> service.installFromUrl(url, id, (success, message) -> {
                                    player.sendMessage(message);
                                    menus.open(player, this);
                                }),
                                () -> menus.open(player, this)),
                        () -> menus.open(player, this)));

        set(size() - 4, Icon.of(Material.CLOCK)
                .title("<" + Msg.ACCENT + ">Refresh catalogue")
                .build(), (player, click) -> service.refreshCatalog((success, message) -> {
                    player.sendMessage(message);
                    if (player.isOnline()) {
                        refresh();
                    }
                }));
    }

    @Override
    protected org.bukkit.inventory.ItemStack emptyIcon() {
        return Icon.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                .title("<" + Msg.MUTED + ">Nothing left to install")
                .lore("Every pack in the catalogue is already installed.")
                .build();
    }
}
