package de.raindancer.rrp.give;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import de.raindancer.rrp.catalog.CatalogItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Builds and hands out the items the installed packs add.
 *
 * <p>The item definitions live in the catalogue as item-argument strings — the same syntax
 * {@code /give} takes — and are parsed by the server's own parser through
 * {@link org.bukkit.inventory.ItemFactory#createItemStack(String)}. That means a custom
 * enchantment or jukebox song registered by a datapack is validated against the real registry:
 * if the datapack is missing, the parse fails and the player is told why, instead of receiving
 * a silently broken item.
 */
public final class GiveService {

    /** Something about the item definition or the server state is wrong. Message is user facing. */
    public static class GiveException extends Exception {
        public GiveException(String message) {
            super(message);
        }

        public GiveException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Builds one item stack from a catalogue entry. Main thread. */
    public ItemStack build(CatalogItem item, int amount) throws GiveException {
        ItemStack stack;
        try {
            stack = Bukkit.getItemFactory().createItemStack(item.spec());
        } catch (IllegalArgumentException e) {
            String hint = item.needs().needsDatapack()
                    ? " This item needs the pack's datapack — install it with /rrp datapack install "
                      + item.pack() + " and try again."
                    : "";
            throw new GiveException("The server rejected the definition of '" + item.key()
                    + "': " + e.getMessage() + "." + hint, e);
        } catch (RuntimeException e) {
            throw new GiveException("Could not build '" + item.key() + "': " + e.getMessage(), e);
        }
        stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize() * 4, amount)));
        return stack;
    }

    /**
     * Puts a stack into a player's inventory, dropping whatever does not fit at their feet —
     * the same behaviour as vanilla {@code /give}, so nothing is ever silently lost.
     *
     * @return how many items ended up on the ground
     */
    public int give(Player target, ItemStack stack) {
        Map<Integer, ItemStack> leftover = new HashMap<>(target.getInventory().addItem(stack));
        int dropped = 0;
        for (ItemStack rest : leftover.values()) {
            dropped += rest.getAmount();
            Item entity = target.getWorld().dropItem(target.getLocation(), rest);
            entity.setPickupDelay(0);
            entity.setOwner(target.getUniqueId());
        }
        return dropped;
    }

    /** Every item reference that tab-complete should offer, fully qualified. */
    public Collection<String> keys(Collection<CatalogItem> items) {
        return items.stream().map(CatalogItem::key).sorted().toList();
    }
}
