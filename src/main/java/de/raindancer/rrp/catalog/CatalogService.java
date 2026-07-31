package de.raindancer.rrp.catalog;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.raindancer.rrp.util.Downloader;
import org.slf4j.Logger;

/**
 * Fetches and caches the catalogue.
 *
 * <p>The last successful download is written to {@code catalog.json} in the plugin folder and
 * loaded at startup, so a server whose pack host is unreachable still knows which packs it has
 * installed and which items exist. gson comes with Paper — nothing is shaded.
 */
public final class CatalogService {

    private final Downloader downloader;
    private final Path cacheFile;
    private final Logger log;

    private volatile Catalog catalog = Catalog.empty();

    public CatalogService(Downloader downloader, Path cacheFile, Logger log) {
        this.downloader = downloader;
        this.cacheFile = cacheFile;
        this.log = log;
    }

    /** The catalogue as it currently stands. Never null; possibly empty. */
    public Catalog catalog() {
        return catalog;
    }

    /** Loads the cached catalogue, if there is one. Called once at startup. */
    public void loadCache() {
        if (!Files.exists(cacheFile)) {
            return;
        }
        try {
            catalog = parse(Files.readString(cacheFile), true);
            log.info("Loaded the cached pack catalogue: {} pack(s).", catalog.packs().size());
        } catch (IOException | JsonParseException e) {
            log.warn("The cached catalogue at {} is unreadable and will be refetched: {}",
                    cacheFile, e.getMessage());
        }
    }

    /**
     * Downloads the catalogue and replaces the in-memory copy.
     *
     * <p>Blocking; call from the worker thread.
     *
     * @throws Downloader.DownloadException when the host is unreachable or the JSON is broken
     */
    public Catalog refresh(String url) throws Downloader.DownloadException {
        URI uri = downloader.validate(url);
        String body = downloader.getString(uri);
        Catalog fresh;
        try {
            fresh = parse(body, false);
        } catch (JsonParseException e) {
            throw new Downloader.DownloadException(
                    "The catalogue at " + uri + " is not valid JSON: " + e.getMessage(), e);
        }
        this.catalog = fresh;
        try {
            Files.createDirectories(cacheFile.getParent());
            Files.writeString(cacheFile, body);
        } catch (IOException e) {
            log.warn("Could not cache the catalogue to {}: {}", cacheFile, e.getMessage());
        }
        return fresh;
    }

    // --- parsing ---------------------------------------------------------------------------

    private Catalog parse(String json, boolean fromCache) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int format = root.has("format") ? root.get("format").getAsInt() : Catalog.SUPPORTED_FORMAT;
        if (format > Catalog.SUPPORTED_FORMAT) {
            log.warn("The catalogue declares format {} but this RRP build knows format {}. "
                    + "Reading it anyway — update the plugin if packs look wrong.",
                    format, Catalog.SUPPORTED_FORMAT);
        }
        Instant generated = Instant.EPOCH;
        if (root.has("generated")) {
            try {
                generated = Instant.parse(root.get("generated").getAsString());
            } catch (DateTimeParseException ignored) {
                // A missing or odd timestamp is cosmetic only.
            }
        }

        List<CatalogPack> packs = new ArrayList<>();
        JsonArray array = root.has("packs") ? root.getAsJsonArray("packs") : new JsonArray();
        for (JsonElement element : array) {
            try {
                packs.add(parsePack(element.getAsJsonObject()));
            } catch (RuntimeException e) {
                log.warn("Skipping a malformed pack entry in the catalogue: {}", e.getMessage());
            }
        }
        return new Catalog(format, generated, string(root, "combine_endpoint", ""),
                List.copyOf(packs), fromCache);
    }

    private CatalogPack parsePack(JsonObject object) {
        String id = object.get("id").getAsString();
        List<String> modules = new ArrayList<>();
        if (object.has("modules")) {
            for (JsonElement module : object.getAsJsonArray("modules")) {
                modules.add(module.isJsonObject()
                        ? module.getAsJsonObject().get("name").getAsString()
                        : module.getAsString());
            }
        }
        List<CatalogItem> items = new ArrayList<>();
        if (object.has("items")) {
            for (JsonElement element : object.getAsJsonArray("items")) {
                JsonObject item = element.getAsJsonObject();
                items.add(new CatalogItem(
                        id,
                        item.get("id").getAsString(),
                        string(item, "name", item.get("id").getAsString()),
                        item.get("item").getAsString(),
                        CatalogItem.Needs.parse(string(item, "needs", "both")),
                        string(item, "source", id),
                        string(item, "requires_plugin", "")));
            }
        }
        return new CatalogPack(
                id,
                string(object, "namespace", id),
                string(object, "name", id),
                string(object, "description", ""),
                string(object, "page", ""),
                List.copyOf(modules),
                List.copyOf(items),
                zip(object, "resourcepack"),
                zip(object, "datapack"));
    }

    private Optional<CatalogPack.Zip> zip(JsonObject parent, String key) {
        if (!parent.has(key) || parent.get(key).isJsonNull()) {
            return Optional.empty();
        }
        JsonObject object = parent.getAsJsonObject(key);
        return Optional.of(new CatalogPack.Zip(
                object.get("url").getAsString(),
                string(object, "sha1", ""),
                object.has("size") ? object.get("size").getAsLong() : -1L));
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString()
                : fallback;
    }
}
