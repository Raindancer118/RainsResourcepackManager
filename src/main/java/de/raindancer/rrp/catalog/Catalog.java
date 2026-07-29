package de.raindancer.rrp.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The pack catalogue: what the pack host offers.
 *
 * <p>Parsed from {@code index.json} (see {@code MC-Resourcepacks/tools/gen_index.py}), which is
 * the single source of truth for download URLs, hashes and the items a pack adds. RRP hardcodes
 * nothing about any individual pack.
 */
public record Catalog(int format, Instant generated, List<CatalogPack> packs, boolean fromCache) {

    /** The format RRP understands. A newer index is used anyway, with a warning. */
    public static final int SUPPORTED_FORMAT = 1;

    public static Catalog empty() {
        return new Catalog(SUPPORTED_FORMAT, Instant.EPOCH, List.of(), false);
    }

    public Optional<CatalogPack> find(String id) {
        return packs.stream()
                .filter(pack -> pack.id().equalsIgnoreCase(id))
                .findFirst();
    }

    /** All items of all packs, as {@code <pack>:<item>} keyed entries. */
    public List<CatalogItem> allItems() {
        return packs.stream().flatMap(pack -> pack.items().stream()).toList();
    }

    /**
     * Resolves an item reference, either fully qualified ({@code yeukpack:barricades}) or by the
     * bare item id when that is unambiguous across all packs.
     */
    public Optional<CatalogItem> findItem(String reference) {
        String ref = reference.toLowerCase(Locale.ROOT);
        List<CatalogItem> items = allItems();
        Optional<CatalogItem> qualified = items.stream()
                .filter(item -> item.key().equalsIgnoreCase(ref))
                .findFirst();
        if (qualified.isPresent()) {
            return qualified;
        }
        List<CatalogItem> matches = items.stream()
                .filter(item -> item.id().equalsIgnoreCase(ref))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }
}
