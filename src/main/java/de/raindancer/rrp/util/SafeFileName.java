package de.raindancer.rrp.util;

import java.util.Locale;

/**
 * Turns untrusted text (pack ids, URL path segments) into a file name that cannot escape the
 * folder it is resolved against.
 */
public final class SafeFileName {

    private SafeFileName() {
    }

    /**
     * @param raw      the untrusted name
     * @param fallback used when nothing usable is left
     * @return a name consisting only of {@code [a-z0-9._-]}, never {@code .} or {@code ..}
     */
    public static String of(String raw, String fallback) {
        String name = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        // Drop any path structure first: a value like "../../x" must not survive as "..x".
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^a-z0-9._-]", "-").replaceAll("-{2,}", "-");
        while (name.startsWith(".") || name.startsWith("-")) {
            name = name.substring(1);
        }
        while (name.endsWith("-")) {
            name = name.substring(0, name.length() - 1);
        }
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            return fallback;
        }
        return name.length() > 80 ? name.substring(0, 80) : name;
    }
}
