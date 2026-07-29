package de.raindancer.rrp.core;

import java.util.List;
import java.util.Locale;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * An immutable snapshot of {@code config.yml}.
 *
 * <p>Reading configuration from a snapshot rather than from {@link FileConfiguration} means the
 * worker thread never races with a {@code /rrp reload} happening on the main thread.
 */
public record RrpConfig(
        String catalogUrl,
        int refreshMinutes,
        boolean applyOnJoin,
        ApplyMode mode,
        boolean requiredByDefault,
        String prompt,
        String mergeFolder,
        String mergeDescription,
        CombineMode combineMode,
        String combineEndpoint,
        boolean httpEnabled,
        String httpBind,
        int httpPort,
        String publicUrl,
        boolean autoInstallDatapacks,
        boolean reloadAfterDatapackInstall,
        boolean requireHttps,
        List<String> allowedHosts,
        long maxDownloadBytes,
        int connectTimeoutSeconds,
        int readTimeoutSeconds) {

    /** Who builds the combined pack. */
    public enum CombineMode {
        /** The pack host builds and serves it. */
        REMOTE,
        /** This server merges it and serves it through the built-in HTTP server. */
        LOCAL;

        public static CombineMode parse(String raw, CombineMode fallback) {
            if (raw == null) {
                return fallback;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
    }

    /** How the installed packs reach the client. */
    public enum ApplyMode {
        /** Combine when possible, otherwise stack. */
        AUTO,
        /** Always combine; fall back to stacking with a warning. */
        MERGED,
        /** Always send the packs one after another. */
        STACKED;

        public static ApplyMode parse(String raw, ApplyMode fallback) {
            if (raw == null) {
                return fallback;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
    }

    public static RrpConfig from(FileConfiguration config) {
        return new RrpConfig(
                config.getString("catalog.url", "https://mc-packs.raindancer118.de/index.json"),
                config.getInt("catalog.refresh-minutes", 180),
                config.getBoolean("apply.on-join", true),
                ApplyMode.parse(config.getString("apply.mode", "auto"), ApplyMode.AUTO),
                config.getBoolean("apply.required-by-default", false),
                config.getString("apply.prompt", ""),
                config.getString("merge.folder", "merged"),
                config.getString("merge.description", "Combined resource pack"),
                CombineMode.parse(config.getString("combine.mode", "remote"), CombineMode.REMOTE),
                config.getString("combine.endpoint", ""),
                config.getBoolean("http.enabled", false),
                config.getString("http.bind", "0.0.0.0"),
                config.getInt("http.port", 8124),
                config.getString("http.public-url", ""),
                config.getBoolean("datapacks.auto-install", true),
                config.getBoolean("datapacks.reload-after-install", true),
                config.getBoolean("security.require-https", true),
                List.copyOf(config.getStringList("security.allowed-hosts")),
                Math.max(1, config.getLong("security.max-download-mb", 200)) * 1024L * 1024L,
                config.getInt("security.connect-timeout-seconds", 15),
                config.getInt("security.read-timeout-seconds", 120));
    }

    /** An empty allow list means "any host"; otherwise the host must match exactly. */
    public boolean isHostAllowed(String host) {
        if (allowedHosts.isEmpty()) {
            return true;
        }
        return allowedHosts.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(host));
    }

    /** @return the public base URL for RRP's own HTTP server, without a trailing slash */
    public String publicBaseUrl() {
        return trimSlashes(publicUrl);
    }

    private static String trimSlashes(String raw) {
        String url = raw == null ? "" : raw.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
