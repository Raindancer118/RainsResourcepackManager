package de.raindancer.rrp.gui;

import java.util.function.Consumer;

import de.raindancer.rrp.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * A yes/no screen for anything that removes something.
 *
 * <p>Confirm sits on the right, cancel on the left and is the wider target — a misclick should
 * land on "no".
 */
public final class ConfirmMenu extends RrpMenu {

    private final MenuManager menus;
    private final RrpMenu parent;
    private final String question;
    private final String detail;
    private final Consumer<Player> onConfirm;

    public ConfirmMenu(MenuManager menus, RrpMenu parent, String question, String detail,
                       Consumer<Player> onConfirm) {
        super(3, MenuManager.title("Confirm", ""));
        this.menus = menus;
        this.parent = parent;
        this.question = question;
        this.detail = detail;
        this.onConfirm = onConfirm;
    }

    @Override
    public RrpMenu parent() {
        return parent;
    }

    @Override
    public void render() {
        clear();
        set(4, Icon.of(Material.PAPER)
                .title("<" + Msg.WARN + "><question>", Msg.arg("question", question))
                .lore(Msg.wrapLore(detail, 38, Msg.MUTED))
                .build());

        for (int slot : new int[]{9, 10, 11, 12, 18, 19, 20, 21}) {
            set(slot, Icon.of(Material.GRAY_STAINED_GLASS_PANE)
                    .title("<" + Msg.MUTED + ">Cancel")
                    .build(), (player, click) -> menus.open(player, parent));
        }
        for (int slot : new int[]{14, 15, 16, 17, 23, 24, 25, 26}) {
            set(slot, Icon.of(Material.RED_STAINED_GLASS_PANE)
                    .title("<" + Msg.BAD + ">Yes, do it")
                    .build(), (player, click) -> onConfirm.accept(player));
        }
    }
}
