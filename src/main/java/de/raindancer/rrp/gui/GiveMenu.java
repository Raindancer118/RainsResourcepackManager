package de.raindancer.rrp.gui;

import java.util.List;

import de.raindancer.rrp.catalog.CatalogItem;
import de.raindancer.rrp.core.RrpService;
import de.raindancer.rrp.give.GiveService;
import de.raindancer.rrp.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * The items the packs add — the GUI half of {@code /rrp give}.
 *
 * <p>Each entry is the real item, built by the server's own item parser, so what an operator
 * sees in this menu is exactly what a player would get. An item whose datapack is missing cannot
 * be built; it is shown greyed out with the reason instead of being hidden.
 */
public final class GiveMenu extends PagedMenu {

    private final RrpService service;
    private int amount = 1;

    public GiveMenu(RrpService service, MenuManager menus, RrpMenu parent, int page) {
        super(MenuManager.title("items", "from the packs"), menus, parent, page);
        this.service = service;
    }

    @Override
    protected int entryCount() {
        return service.catalog().allItems().size();
    }

    @Override
    protected void drawEntry(int index, int slot) {
        List<CatalogItem> items = service.catalog().allItems();
        CatalogItem item = items.get(index);

        ItemStack preview;
        String problem = null;
        try {
            preview = service.give().build(item, Math.max(1, amount));
        } catch (GiveService.GiveException e) {
            preview = Icon.of(Material.BARRIER).build();
            problem = e.getMessage();
        }

        List<Component> lore = new java.util.ArrayList<>();
        lore.add(Msg.lore("<key>", Msg.arg("key", item.key())));
        lore.add(Msg.lore("needs <needs>",
                Msg.arg("needs", item.needs().name().toLowerCase(java.util.Locale.ROOT))));
        if (!item.source().equals(item.pack())) {
            lore.add(Msg.lore("from module <module>", Msg.arg("module", item.source())));
        }
        if (problem != null) {
            lore.addAll(Msg.wrapLore(problem, 38, Msg.BAD));
        } else {
            lore.add(Component.empty());
            lore.add(Msg.itemTitle("<" + Msg.ACCENT + ">▸ Click: give <amount>× to yourself",
                    Msg.num("amount", amount)).decoration(TextDecoration.ITALIC, false));
            lore.add(Msg.itemTitle("<" + Msg.ACCENT + ">▸ Right-click: give it to someone else")
                    .decoration(TextDecoration.ITALIC, false));
        }

        String failure = problem;
        ItemStack icon = preview.clone();
        List<Component> finalLore = lore;
        icon.editMeta(meta -> {
            meta.displayName(Msg.itemTitle("<" + (failure == null ? Msg.ACCENT : Msg.MUTED)
                    + "><name>", Msg.arg("name", item.name()))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(finalLore);
        });
        set(slot, icon, (player, click) -> {
            if (failure != null) {
                player.sendMessage(Msg.error("<detail>", Msg.arg("detail", failure)));
                return;
            }
            if (click.isRightClick()) {
                menus.open(player, new GivePlayerMenu(service, menus, this, item, amount, 0));
                return;
            }
            service.giveItem(item, player, amount, (success, message) -> player.sendMessage(message));
        });
    }

    @Override
    protected void drawExtras() {
        set(size() - 1, Icon.of(Material.PAPER)
                .title("<" + Msg.ACCENT + ">Amount: <amount>", Msg.num("amount", amount))
                .lore("How many of an item a click hands out.")
                .blank()
                .action("Click: 1 → 8 → 16 → 32 → 64")
                .action("Right-click: type an amount")
                .build(), (player, click) -> {
                    if (click.isRightClick()) {
                        menus.promptForText(player, "an amount between 1 and 64", text -> {
                            try {
                                amount = Math.max(1, Math.min(64, Integer.parseInt(text.trim())));
                            } catch (NumberFormatException e) {
                                player.sendMessage(Msg.error("'<text>' is not a number.",
                                        Msg.arg("text", text)));
                            }
                            menus.open(player, this);
                        }, () -> menus.open(player, this));
                        return;
                    }
                    amount = switch (amount) {
                        case 1 -> 8;
                        case 8 -> 16;
                        case 16 -> 32;
                        case 32 -> 64;
                        default -> 1;
                    };
                    refresh();
                });
    }

    @Override
    protected ItemStack emptyIcon() {
        return Icon.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                .title("<" + Msg.MUTED + ">No items")
                .lore("The catalogue lists no items yet. Try refreshing it.")
                .build();
    }
}
