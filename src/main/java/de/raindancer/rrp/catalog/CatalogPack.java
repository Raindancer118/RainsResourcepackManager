package de.raindancer.rrp.catalog;

import java.util.List;
import java.util.Optional;

/** One pack as the catalogue describes it. */
public record CatalogPack(
        String id,
        String namespace,
        String name,
        String description,
        String page,
        List<String> modules,
        List<CatalogItem> items,
        Optional<Zip> resourcepack,
        Optional<Zip> datapack) {

    /** A downloadable half of a pack. */
    public record Zip(String url, String sha1, long size) {
    }
}
