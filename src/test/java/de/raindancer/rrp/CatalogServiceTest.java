package de.raindancer.rrp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import de.raindancer.rrp.catalog.Catalog;
import de.raindancer.rrp.catalog.CatalogItem;
import de.raindancer.rrp.catalog.CatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/** Parsing is tested through the cache path, which needs no network. */
class CatalogServiceTest {

    private static final String INDEX = """
            {
              "format": 1,
              "generated": "2026-07-29T12:02:39Z",
              "base_url": "https://example.com/",
              "combine_endpoint": "https://example.com/combine",
              "packs": [
                {
                  "id": "yeukpack",
                  "namespace": "yeukpack",
                  "name": "yeukpack — Yeuk SMP",
                  "description": "Discs and enchantments.",
                  "modules": [{"id": "xp-enchantment", "name": "Wisdom", "description": "x"}],
                  "items": [
                    {"id": "barricades", "name": "Barricades", "item": "minecraft:music_disc_13",
                     "needs": "both", "source": "yeukpack"},
                    {"id": "wisdom_book", "name": "Wisdom III", "item": "minecraft:enchanted_book",
                     "needs": "datapack", "source": "xp-enchantment"}
                  ],
                  "resourcepack": {"url": "https://example.com/y.zip", "sha1": "abc", "size": 12},
                  "datapack": {"url": "https://example.com/y-dp.zip", "sha1": "def", "size": 5}
                }
              ]
            }
            """;

    private CatalogService load(Path dir) throws Exception {
        Path cache = dir.resolve("catalog.json");
        Files.writeString(cache, INDEX);
        CatalogService service = new CatalogService(null, cache,
                LoggerFactory.getLogger(CatalogServiceTest.class));
        service.loadCache();
        return service;
    }

    @Test
    void readsPacksItemsAndZips(@TempDir Path dir) throws Exception {
        Catalog catalog = load(dir).catalog();

        assertThat(catalog.packs()).hasSize(1);
        var pack = catalog.packs().get(0);
        assertThat(pack.id()).isEqualTo("yeukpack");
        assertThat(pack.modules()).containsExactly("Wisdom");
        assertThat(pack.resourcepack()).isPresent();
        assertThat(pack.resourcepack().get().sha1()).isEqualTo("abc");
        assertThat(pack.datapack()).isPresent();
        assertThat(catalog.allItems()).hasSize(2);
    }

    @Test
    void readsTheCombineEndpointTheHostAdvertises(@TempDir Path dir) throws Exception {
        assertThat(load(dir).catalog().combineEndpoint()).isEqualTo("https://example.com/combine");
    }

    @Test
    void resolvesItemsQualifiedAndUnqualified(@TempDir Path dir) throws Exception {
        Catalog catalog = load(dir).catalog();

        assertThat(catalog.findItem("yeukpack:barricades")).isPresent();
        assertThat(catalog.findItem("barricades").map(CatalogItem::key))
                .contains("yeukpack:barricades");
        assertThat(catalog.findItem("nope")).isEmpty();
    }

    @Test
    void knowsWhichHalfAnItemNeeds(@TempDir Path dir) throws Exception {
        Catalog catalog = load(dir).catalog();

        CatalogItem book = catalog.findItem("wisdom_book").orElseThrow();
        assertThat(book.needs().needsDatapack()).isTrue();
        assertThat(book.needs().needsResourcepack()).isFalse();
        assertThat(book.source()).isEqualTo("xp-enchantment");
    }

    @Test
    void survivesAMissingCacheFile(@TempDir Path dir) {
        CatalogService service = new CatalogService(null, dir.resolve("nothing.json"),
                LoggerFactory.getLogger(CatalogServiceTest.class));
        service.loadCache();

        assertThat(service.catalog().packs()).isEmpty();
    }
}
