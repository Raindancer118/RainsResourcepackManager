package de.raindancer.rrp.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import de.raindancer.rrp.core.RrpConfig;

/**
 * Every byte RRP pulls off the network goes through here.
 *
 * <p>Resource packs are verified by <b>SHA-1</b> — not because SHA-1 is a good hash, but because
 * it is the only digest the Minecraft protocol carries for a resource pack, so it is the digest
 * the catalogue publishes and the client checks. The check is an integrity check against a
 * corrupted or truncated download, not a security boundary; the security boundary is HTTPS plus
 * the host allow list.
 */
public final class Downloader {

    /** Anything the user did wrong or a remote server refused. The message is user facing. */
    public static class DownloadException extends Exception {
        public DownloadException(String message) {
            super(message);
        }

        public DownloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * @param file      where the payload landed
     * @param sha1      lowercase hex SHA-1 of the payload
     * @param bytes     payload size
     * @param sourceUri the URI the payload actually came from, after redirects
     */
    public record Result(Path file, String sha1, long bytes, URI sourceUri) {
    }

    private final HttpClient client;
    private final RrpConfig config;
    private final String userAgent;

    public Downloader(RrpConfig config, String userAgent) {
        this.config = config;
        this.userAgent = userAgent;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(config.connectTimeoutSeconds()))
                .build();
    }

    /**
     * Validates a user supplied URL against the configured security policy.
     *
     * @throws DownloadException if the URL is malformed or the policy rejects it
     */
    public URI validate(String rawUrl) throws DownloadException {
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new DownloadException("That is not a valid URL: " + e.getMessage(), e);
        }
        if (!uri.isAbsolute() || uri.getHost() == null) {
            throw new DownloadException(
                    "The URL needs a scheme and a host, e.g. https://example.com/pack.zip");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (config.requireHttps() && !scheme.equals("https")) {
            throw new DownloadException("Only HTTPS downloads are allowed. Set "
                    + "security.require-https to false in RRP's config.yml to override this.");
        }
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new DownloadException("Unsupported URL scheme '" + scheme + "'.");
        }
        if (!config.isHostAllowed(uri.getHost())) {
            throw new DownloadException("Downloads from '" + uri.getHost()
                    + "' are blocked by security.allowed-hosts in RRP's config.yml.");
        }
        return uri;
    }

    /** Fetches a text body — used for the catalogue JSON. */
    public String getString(URI uri) throws DownloadException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(config.readTimeoutSeconds()))
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new DownloadException("Not found (HTTP 404): " + uri);
            }
            if (response.statusCode() / 100 != 2) {
                throw new DownloadException(
                        "Request failed with HTTP " + response.statusCode() + ": " + uri);
            }
            return response.body();
        } catch (IOException e) {
            throw new DownloadException("Could not reach " + uri.getHost() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownloadException("Download was interrupted.", e);
        }
    }

    /**
     * Streams {@code uri} to {@code target}, enforcing the configured size cap while reading.
     *
     * @param expectedSha1 optional lowercase hex digest the payload must match
     */
    public Result download(URI uri, Path target, Optional<String> expectedSha1)
            throws DownloadException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(config.readTimeoutSeconds()))
                .GET()
                .build();

        Path partial = target.resolveSibling(target.getFileName() + ".part");
        try {
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(partial);

            HttpResponse<InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                response.body().close();
                throw new DownloadException("Download failed with HTTP " + response.statusCode() + ".");
            }

            long advertised = response.headers().firstValueAsLong("content-length").orElse(-1L);
            if (advertised > config.maxDownloadBytes()) {
                response.body().close();
                throw new DownloadException("That file is " + Msg.bytes(advertised)
                        + ", which exceeds the " + Msg.bytes(config.maxDownloadBytes())
                        + " limit from config.yml.");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            long total = 0;
            byte[] buffer = new byte[64 * 1024];
            try (InputStream in = response.body();
                 OutputStream out = Files.newOutputStream(partial)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > config.maxDownloadBytes()) {
                        throw new DownloadException("Download exceeded the "
                                + Msg.bytes(config.maxDownloadBytes()) + " limit from config.yml.");
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            if (total == 0) {
                throw new DownloadException("The server returned an empty file.");
            }

            String actual = HexFormat.of().formatHex(digest.digest());
            if (expectedSha1.isPresent() && !expectedSha1.get().equalsIgnoreCase(actual)) {
                throw new DownloadException("Checksum mismatch — the downloaded pack does not match "
                        + "the sha1 the catalogue publishes. Aborting for safety.");
            }

            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
            return new Result(target, actual, total, response.uri());
        } catch (DownloadException e) {
            deleteQuietly(partial);
            throw e;
        } catch (NoSuchAlgorithmException e) {
            deleteQuietly(partial);
            throw new DownloadException("This JVM has no SHA-1 implementation.", e);
        } catch (IOException e) {
            deleteQuietly(partial);
            throw new DownloadException("Download failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            deleteQuietly(partial);
            Thread.currentThread().interrupt();
            throw new DownloadException("Download was interrupted.", e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Nothing sensible to do — the .part file is inside RRP's own folder.
        }
    }
}
