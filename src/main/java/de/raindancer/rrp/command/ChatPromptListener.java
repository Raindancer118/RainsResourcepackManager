package de.raindancer.rrp.command;

import de.raindancer.rrp.gui.MenuManager;
import de.raindancer.rrp.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Catches the chat line a player types in response to a GUI prompt.
 *
 * <p>A chest GUI has no text field, so asking for a pack URL or an item amount means closing the
 * menu and reading the player's next chat message. Registered at {@code LOWEST} so the message is
 * cancelled before any chat plugin renders it — a pasted URL has no business in public chat.
 */
public final class ChatPromptListener implements Listener {

    private final MenuManager menus;

    public ChatPromptListener(MenuManager menus) {
        this.menus = menus;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!menus.hasPrompt(event.getPlayer())) {
            return;
        }
        String text = Msg.plain(event.message());
        if (menus.feedPrompt(event.getPlayer(), text)) {
            event.setCancelled(true);
        }
    }
}
