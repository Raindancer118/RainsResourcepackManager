package de.raindancer.rrp.pack;

import java.util.Optional;

/**
 * A pack this server has installed.
 *
 * <p>"Installed" means: RRP knows about it, keeps a local copy of the zip (so it can be combined
 * with others) and sends it to players. The client still downloads it from {@link #url()} —
 * either the original pack host or RRP's own HTTP server.
 */
public record InstalledPack(
        String id,
        String name,
        String url,
        String sha1,
        long size,
        String localFile,
        boolean enabled,
        boolean required,
        int order,
        boolean datapackInstalled,
        String datapackFile,
        String source,
        long installedAt) {

    public InstalledPack withEnabled(boolean value) {
        return new InstalledPack(id, name, url, sha1, size, localFile, value, required, order,
                datapackInstalled, datapackFile, source, installedAt);
    }

    public InstalledPack withRequired(boolean value) {
        return new InstalledPack(id, name, url, sha1, size, localFile, enabled, value, order,
                datapackInstalled, datapackFile, source, installedAt);
    }

    public InstalledPack withOrder(int value) {
        return new InstalledPack(id, name, url, sha1, size, localFile, enabled, required, value,
                datapackInstalled, datapackFile, source, installedAt);
    }

    public InstalledPack withDatapack(boolean installed, String file) {
        return new InstalledPack(id, name, url, sha1, size, localFile, enabled, required, order,
                installed, file, source, installedAt);
    }

    /** The local zip, if RRP managed to download one. Required for combining packs. */
    public Optional<String> localCopy() {
        return localFile == null || localFile.isBlank() ? Optional.empty() : Optional.of(localFile);
    }
}
