package de.raindancer.rrp.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.function.Supplier;

/**
 * All of RRP's user-facing text lives behind this class.
 *
 * <p>Everything is built as an Adventure {@link Component} through MiniMessage — no legacy
 * {@code §} codes anywhere. Untrusted strings (pack names, URLs, remote error messages) are
 * always passed as {@link Placeholder#unparsed}, so a pack called {@code <red>oops} cannot
 * inject formatting into RRP's output.
 *
 * <h2>Why this is also the seam between the standalone jar and the module</h2>
 * Folded into Rain's SMP Core, every chat message this plugin sends has to wear the host's tag
 * instead of RRP's own, and a message that concerns only its recipient has to obey the host's
 * {@code messages.personal-in-action-bar} setting rather than always landing in chat. Both are
 * static, installed once at startup, for the same reason the tag itself is one method rather than
 * a constant: every command and every menu already reaches through this file for its colours, so
 * putting the two decisions here — rather than in a class of their own — keeps this the one file
 * a module needs to configure and {@code RrpPlugin} the one file that is allowed to differ between
 * the two builds. The defaults below are RRP's own: the gradient {@code rrp ›} tag it has always
 * used, and chat, because a standalone plugin has no host setting to obey and moving a message
 * somewhere the admin never asked for would be a surprise, not a feature.
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

    /**
     * The plugin's chat tag, shared with the rest of the jar.
     * <p>
     * A supplier rather than the constant this used to be: inside Rain's SMP Core the tag is an
     * admin setting, so it has to be read per message rather than fixed at class-load time.
     */
    private static volatile Supplier<String> prefix = () ->
            "<gradient:" + ACCENT + ":" + ACCENT_DIM + "><bold>rrp</bold></gradient><"
                    + MUTED + "> › </" + MUTED + ">";

    /** How a message that concerns only its recipient is delivered; plain chat until installed otherwise. */
    private static volatile Sender sender = Audience::sendMessage;

    /** How a message that concerns only its recipient is delivered. */
    @FunctionalInterface
    public interface Sender {
        void send(Audience recipient, Component message);
    }

    private Msg() {
    }

    /**
     * Installed once, at startup, by {@code RrpPlugin}.
     *
     * @param chatPrefix what {@link #info}, {@link #success}, {@link #warn} and {@link #error} start
     *                   with, as MiniMessage; null keeps RRP's own gradient tag
     * @param personal   delivers a message that concerns only its recipient; null keeps sending to chat
     */
    public static void configure(Supplier<String> chatPrefix, Sender personal) {
        if (chatPrefix != null) {
            prefix = chatPrefix;
        }
        if (personal != null) {
            sender = personal;
        }
    }

    private static String prefix() {
        String configured = prefix.get();
        return configured == null ? "" : configured;
    }

    /**
     * Sends a message that concerns nobody but its recipient.
     *
     * <h2>Why not every message in this class goes through here</h2>
     * RRP's command output is often a <em>list</em>: a heading built with {@link #info} and then a run
     * of rows built with {@link #raw}. Moving the heading somewhere else and leaving the rows in chat
     * would take a list apart. So the rule here is by shape rather than by class — the messages that
     * are the whole answer to what somebody just did (a refusal, a confirmation, the GUI's feedback, a
     * pack a player declined) are sent through this; headings and progress lines are not.
     */
    public static void tell(Audience recipient, Component message) {
        sender.send(recipient, message);
    }

    /** Parses MiniMessage without a prefix. */
    public static Component raw(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(miniMessage, resolvers);
    }

    public static Component info(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(prefix() + "<" + TEXT + ">" + miniMessage, resolvers);
    }

    public static Component success(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(prefix() + "<" + OK + ">" + miniMessage, resolvers);
    }

    public static Component warn(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(prefix() + "<" + WARN + ">" + miniMessage, resolvers);
    }

    public static Component error(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(prefix() + "<" + BAD + ">" + miniMessage, resolvers);
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

    /**
     * Splits {@code text} into lore lines of at most {@code width} characters, on word boundaries,
     * capped at nine lines.
     * <p>
     * The wrapping is the same {@code de.raindancer.smpcore.util.Wrapping} does for the rest of the
     * jar's menus — kept as its own copy here, rather than a call into it, because the standalone
     * build may not depend on {@code smpcore} classes. Only the colouring below is this module's own.
     * A tooltip longer than the cap ends in an ellipsis, so a shortened description says so instead of
     * quietly looking like the whole thing.
     */
    public static java.util.List<Component> wrapLore(String text, int width, String colour) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        for (String line : wrap(text, width, 9)) {
            lines.add(lore("<" + colour + "><value>", arg("value", line)));
        }
        return lines;
    }

    /**
     * Fills lines up to {@code width} characters, on word boundaries, then caps the result at
     * {@code maxLines}, marking the last kept line with an ellipsis when it cut something off.
     * <p>
     * A single word longer than the width is left whole rather than cut: breaking
     * {@code Raindancer118} across two lines makes it unreadable, and a slightly wide line does not.
     */
    private static java.util.List<String> wrap(String text, int width, int maxLines) {
        if (text == null || text.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (!line.isEmpty() && line.length() + word.length() + 1 > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        if (lines.size() <= maxLines || maxLines <= 0) {
            return lines;
        }
        java.util.List<String> capped = new java.util.ArrayList<>(lines.subList(0, maxLines));
        capped.set(maxLines - 1, capped.get(maxLines - 1) + "…");
        return capped;
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
