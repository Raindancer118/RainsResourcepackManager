package de.raindancer.rrp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * All of RRP's user-facing text lives behind this class.
 *
 * <p>Everything is built as an Adventure {@link Component} through MiniMessage — no legacy
 * {@code §} codes anywhere. Untrusted strings (pack names, URLs, remote error messages) are
 * always passed as {@link Placeholder#unparsed}, so a pack called {@code <red>oops} cannot
 * inject formatting into RRP's output.
 */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final String ACCENT = "#C9A0FF";
    public static final String ACCENT_DIM = "#7C5CBF";
    public static final String OK = "#7BE07B";
    public static final String WARN = "#FFD166";
    public static final String BAD = "#FF7B7B";
    public static final String TEXT = "#D7DCE0";
    public static final String MUTED = "#8B949E";

    private static final String PREFIX =
            "<gradient:" + ACCENT + ":" + ACCENT_DIM + "><bold>rrp</bold></gradient><"
                    + MUTED + "> › </" + MUTED + ">";

    private Msg() {
    }

    /** Parses MiniMessage without a prefix. */
    public static Component raw(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(miniMessage, resolvers);
    }

    public static Component info(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(PREFIX + "<" + TEXT + ">" + miniMessage, resolvers);
    }

    public static Component success(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(PREFIX + "<" + OK + ">" + miniMessage, resolvers);
    }

    public static Component warn(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(PREFIX + "<" + WARN + ">" + miniMessage, resolvers);
    }

    public static Component error(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(PREFIX + "<" + BAD + ">" + miniMessage, resolvers);
    }

    /** Wraps untrusted text so it can be dropped into a message safely. */
    public static TagResolver arg(String name, String value) {
        return Placeholder.unparsed(name, value == null ? "—" : value);
    }

    /** Wraps a number. */
    public static TagResolver num(String name, long value) {
        return Placeholder.unparsed(name, Long.toString(value));
    }

    /** Wraps a pre-built component for insertion. */
    public static TagResolver comp(String name, Component value) {
        return Placeholder.component(name, value);
    }

    /**
     * Builds an item label: italics switched off.
     *
     * <p>Minecraft italicises custom item names by default, which looks sloppy in a menu.
     */
    public static Component itemTitle(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize("<!italic>" + miniMessage, resolvers);
    }

    /** Builds a lore line with italics switched off. */
    public static Component lore(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize("<!italic><" + MUTED + ">" + miniMessage, resolvers)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    /** Splits {@code text} into lore lines of at most {@code width} characters, on word boundaries. */
    public static java.util.List<Component> wrapLore(String text, int width, String colour) {
        if (text == null || text.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<Component> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                lines.add(lore("<" + colour + "><value>", arg("value", line.toString())));
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
            if (lines.size() >= 8) {
                break;
            }
        }
        if (!line.isEmpty() && lines.size() < 9) {
            lines.add(lore("<" + colour + "><value>", arg("value", line.toString())));
        }
        return lines;
    }

    /** Flattens a component to plain text, e.g. to read a chat message a player typed. */
    public static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(component);
    }

    /** Human readable byte size, e.g. {@code 2.6 MB}. */
    public static String bytes(long value) {
        if (value < 1024) {
            return value + " B";
        }
        if (value < 1024 * 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f kB", value / 1024.0);
        }
        return String.format(java.util.Locale.ROOT, "%.1f MB", value / (1024.0 * 1024.0));
    }
}
