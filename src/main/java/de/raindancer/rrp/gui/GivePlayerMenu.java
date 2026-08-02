package de.raindancer.rrp.gui;

import java.util.List;

import de.raindancer.rrp.catalog.CatalogItem;
import de.raindancer.rrp.core.RrpService;
import de.raindancer.rrp.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** Picks who receives an item — the GUI half of {@code /rrp give <item> <player>}. */
public final class GivePlayerMenu extends PagedMenu {

    private final RrpService service;
    private final CatalogItem item;
    private final int amount;

    public GivePlayerMenu(RrpService service, MenuManager menus, RrpMenu parent,
                          CatalogItem item, int amount, int page) {
        super(MenuManager.title("Give", item.id()), menus, parent, page);
        this.service = service;
        this.item = item;
        this.amount = amount;
    }

    /**
     * Only online players here, deliberately — unlike the claim pickers.
     * <p>
     * Handing somebody an item needs an inventory to put it in, and an offline player has none.
     * Listing names that would fail on click would be a worse chooser, not a fuller one.
     */
    private List<? extends Player> players() {
        return List.copyOf(service.plugin().getServer().getOnlinePlayers());
    }

    @Override
    protected int entryCount() {
        return players().size();
    }

    @Override
    protected void drawEntry(int index, int slot) {
        List<? extends Player> online = players();
        if (index >= online.size()) {
            return;
        }
        Player target = online.get(index);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            meta.setOwningPlayer(target);
            meta.displayName(Msg.itemTitle("<" + Msg.ACCENT + "><name>",
                            Msg.arg("name", target.getName()))
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Msg.lore("<amount>× <item>", Msg.num("amount", amount),
                            Msg.arg("item", item.name())),
                    Msg.lore("▸ Click to hand it over")));
        });
        set(slot, head, (player, click) -> service.giveItem(item, target, amount,
                (success, message) -> {
                    Msg.tell(player, message);
                    if (player.isOnline()) {
                        refresh();
                    }
                }));
    }

    @Override
    protected void drawExtras() {
        set(size() - 1, Icon.of(Material.BEACON)
                .title("<" + Msg.ACCENT + ">Everyone online")
                .lore("<amount>× <item> for all <count> player(s)",
                        Msg.num("amount", amount), Msg.arg("item", item.name()),
                        Msg.num("count", players().size()))
                .blank()
                .action("Hand it to everyone")
                .build(), (player, click) -> {
                    int given = 0;
                    for (Player target : players()) {
                        service.giveItem(item, target, amount, RrpService.Callback.NONE);
                        given++;
                    }
                    Msg.tell(player, Msg.success("Gave <item> to <count> player(s).",
                            Msg.arg("item", item.name()), Msg.num("count", given)));
                });
    }
}
