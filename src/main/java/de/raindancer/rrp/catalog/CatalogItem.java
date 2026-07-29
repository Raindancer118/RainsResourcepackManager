package de.raindancer.rrp.catalog;

import java.util.Locale;

/**
 * An item a pack adds, as an item-argument string the vanilla parser understands, e.g.
 * {@code minecraft:music_disc_13[minecraft:jukebox_playable="yeukpack:barricades", …]}.
 *
 * <p>Keeping the definition in the catalogue instead of in the plugin means a new pack with new
 * items needs no plugin update — {@code /rrp give} picks it up on the next catalogue refresh.
 */
public record CatalogItem(String pack, String id, String name, String spec, Needs needs, String source) {

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
