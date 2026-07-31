package de.raindancer.rrp.catalog;

import java.util.Locale;

/**
 * An item a pack adds, as an item-argument string the vanilla parser understands, e.g.
 * {@code minecraft:music_disc_13[minecraft:jukebox_playable="yeukpack:barricades", …]}.
 *
 * <p>Keeping the definition in the catalogue instead of in the plugin means a new pack with new
 * items needs no plugin update — {@code /rrp give} picks it up on the next catalogue refresh.
 */
public record CatalogItem(String pack, String id, String name, String spec, Needs needs, String source,
                          String requiresPlugin) {

    /**
     * Some items are not data at all.
     * <p>
     * A pack can carry the texture and the model for something whose <em>behaviour</em> is plugin code —
     * a backpack that opens an inventory, a portable jukebox that tracks playback. Installing the pack
     * on a server without that plugin yields an item that looks exactly right and does nothing, which is
     * the most confusing possible outcome: it is not broken enough to report and not working enough to
     * use. Naming the plugin lets {@code /rrp info} and {@code /rrp give} say so up front.
     *
     * @param requiresPlugin the plugin's name as the server knows it, or empty for the usual case
     */
    public CatalogItem {
        requiresPlugin = requiresPlugin == null ? "" : requiresPlugin.trim();
    }

    /** Whether this item's behaviour comes from a plugin rather than from the pack. */
    public boolean needsPlugin() {
        return !requiresPlugin.isEmpty();
    }

    /** Whether the plugin it needs is actually on this server. */
    public boolean pluginPresent() {
        return !needsPlugin()
                || org.bukkit.Bukkit.getPluginManager().getPlugin(requiresPlugin) != null;
    }

    /** What has to be installed for the item to work and look right. */
    public enum Needs {
        RESOURCEPACK,
        DATAPACK,
        BOTH;

        public static Needs parse(String raw) {
            if (raw == null) {
                return BOTH;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "resourcepack", "rp" -> RESOURCEPACK;
                case "datapack", "dp" -> DATAPACK;
                default -> BOTH;
            };
        }

        public boolean needsDatapack() {
            return this == DATAPACK || this == BOTH;
        }

        public boolean needsResourcepack() {
            return this == RESOURCEPACK || this == BOTH;
        }
    }

    /** The fully qualified reference used by {@code /rrp give}: {@code <pack>:<item>}. */
    public String key() {
        return pack + ":" + id;
    }
}
