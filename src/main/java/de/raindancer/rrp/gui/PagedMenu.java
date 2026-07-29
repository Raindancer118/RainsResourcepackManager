package de.raindancer.rrp.gui;

import java.util.List;

import de.raindancer.rrp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * A list screen with paging.
 *
 * <p>Content lives in the three middle rows so the frame never moves: the footer always has back,
 * page numbers and the page arrows in the same slots, whatever is being listed.
 */
public abstract class PagedMenu extends RrpMenu {

    /** Content slots: rows 2 to 4, without the outer columns. */
    protected static final int[] CONTENT = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};

    protected final MenuManager menus;
    private final RrpMenu parent;
    protected int page;

    protected PagedMenu(Component title, MenuManager menus, RrpMenu parent, int page) {
        super(6, title);
        this.menus = menus;
        this.parent = parent;
        this.page = Math.max(0, page);
    }

    @Override
    public RrpMenu parent() {
        return parent;
    }

    /** @return how many entries the list has in total */
    protected abstract int entryCount();

    /** Draws entry {@code index} into {@code slot}. */
    protected abstract void drawEntry(int index, int slot);

    /** Called after the entries; lets a subclass add its own buttons to the footer. */
    protected void drawExtras() {
    }

    /** Shown in the middle of the footer when the list is empty. */
    protected ItemStack emptyIcon() {
        return Icon.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                .title("<" + Msg.MUTED + ">Nothing here")
                .build();
    }

    protected final int pageCount() {
        return Math.max(1, (entryCount() + CONTENT.length - 1) / CONTENT.length);
    }

    @Override
    public final void render() {
        clear();
        int total = entryCount();
        if (page >= pageCount()) {
            page = pageCount() - 1;
        }
        int start = page * CONTENT.length;

        if (total == 0) {
            set(22, emptyIcon());
        }
        for (int i = 0; i < CONTENT.length; i++) {
            int index = start + i;
            if (index >= total) {
                break;
            }
            drawEntry(index, CONTENT[i]);
        }

        fillFooterBackground();
        drawBackOrClose(menus, size() - 9);

        if (page > 0) {
            set(size() - 7, Icon.of(Material.SPECTRAL_ARROW)
                    .title("<" + Msg.ACCENT + ">Previous page")
                    .build(), (player, click) -> {
                        page--;
                        refresh();
                    });
        }
        if (page < pageCount() - 1) {
            set(size() - 3, Icon.of(Material.SPECTRAL_ARROW)
                    .title("<" + Msg.ACCENT + ">Next page")
                    .build(), (player, click) -> {
                        page++;
                        refresh();
                    });
        }
        set(size() - 5, Icon.of(Material.PAPER)
                .title("<" + Msg.TEXT + ">Page <page> / <pages>",
                        Msg.num("page", page + 1L), Msg.num("pages", pageCount()))
                .lore("<count> entr(y/ies)", Msg.num("count", total))
                .build());

        drawExtras();
    }
}
