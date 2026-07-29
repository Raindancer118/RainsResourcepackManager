package de.raindancer.rrp.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

/**
 * The startup banner, printed through {@link ComponentLogger} so the gradient renders in colour
 * instead of leaving escape-code soup in the log.
 *
 * <p>It doubles as a health report: it prints what the startup self-check actually found, so an
 * operator scrolling past it learns whether RRP came up healthy without reading further.
 */
public final class Banner {

    private static final List<String> ART = List.of(
            "  █▀█ █▀█ █▀█ ",
            "  █▀▄ █▀▄ █▀▀ ",
            "  ▀ ▀ ▀ ▀ ▀   ");

    private Banner() {
    }

    public static void print(ComponentLogger logger, String version, String serverVersion,
                             int catalogCount, int installed, int active, String mergeState,
                             boolean healthy) {
        for (Component line : build(version, serverVersion, catalogCount, installed, active,
                mergeState, healthy)) {
            logger.info(line);
        }
    }

    /** Built separately from printing so the layout can be asserted in a test. */
    public static List<Component> build(String version, String serverVersion, int catalogCount,
                                        int installed, int active, String mergeState,
                                        boolean healthy) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());

        List<String> details = List.of(
                "<" + Msg.TEXT + ">Rain's Resourcepack Manager <" + Msg.MUTED + ">v" + escape(version),
                "<" + Msg.MUTED + ">resource packs, datapacks and their items",
                "<" + Msg.MUTED + ">by Raindancer118");
        for (int row = 0; row < ART.size(); row++) {
            lines.add(Msg.raw("<gradient:" + Msg.ACCENT + ":" + Msg.ACCENT_DIM + ">"
                    + ART.get(row) + "</gradient>  " + details.get(row)));
        }

        lines.add(Component.empty());
        lines.add(Msg.raw("  <" + Msg.MUTED + ">server  </" + Msg.MUTED + "><" + Msg.TEXT
                + ">Minecraft <version>", Msg.arg("version", serverVersion)));
        lines.add(Msg.raw("  <" + Msg.MUTED + ">catalog </" + Msg.MUTED + "><" + Msg.TEXT
                + "><count> pack(s) available", Msg.num("count", catalogCount)));
        lines.add(Msg.raw("  <" + Msg.MUTED + ">packs   </" + Msg.MUTED + "><" + Msg.TEXT
                        + "><installed> installed, <active> active",
                Msg.num("installed", installed), Msg.num("active", active)));
        lines.add(Msg.raw("  <" + Msg.MUTED + ">sending </" + Msg.MUTED + "><" + Msg.TEXT
                + "><state>", Msg.arg("state", mergeState)));
        lines.add(healthy
                ? Msg.raw("  <" + Msg.MUTED + ">status  </" + Msg.MUTED + "><" + Msg.OK
                        + ">ready <" + Msg.MUTED + ">— try <" + Msg.ACCENT + ">/rrp gui</"
                        + Msg.ACCENT + ">")
                : Msg.raw("  <" + Msg.MUTED + ">status  </" + Msg.MUTED + "><" + Msg.WARN
                        + ">degraded <" + Msg.MUTED + ">— see the warnings above"));
        lines.add(Component.empty());
        return lines;
    }

    private static String escape(String raw) {
        return raw == null ? "?" : raw.replace("<", "\\<").toLowerCase(Locale.ROOT);
    }
}
