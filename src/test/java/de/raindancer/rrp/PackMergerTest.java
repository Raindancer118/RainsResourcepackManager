package de.raindancer.rrp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import de.raindancer.rrp.pack.PackMerger;
import de.raindancer.rrp.util.Hashes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class PackMergerTest {

    private final PackMerger merger = new PackMerger(LoggerFactory.getLogger(PackMergerTest.class));

    @Test
    void mergesLanguageFilesInsteadOfOverwritingThem(@TempDir Path dir) throws Exception {
        Path a = zip(dir.resolve("a.zip"), Map.of(
                "pack.mcmeta", mcmeta(75, 88),
                "assets/a/lang/en_us.json", "{\"item.a.one\":\"One\"}",
                "assets/a/textures/item/one.png", "A"));
        Path b = zip(dir.resolve("b.zip"), Map.of(
                "pack.mcmeta", mcmeta(80, 107),
                "assets/a/lang/en_us.json", "{\"item.b.two\":\"Two\"}",
                "assets/b/textures/item/two.png", "B"));

        PackMerger.Result result = merger.merge(List.of(a, b), dir.resolve("out"), "combined");

        assertThat(entry(result.file(), "assets/a/lang/en_us.json"))
                .contains("item.a.one")
                .contains("item.b.two");
        assertThat(entry(result.file(), "assets/a/textures/item/one.png")).isEqualTo("A");
        assertThat(entry(result.file(), "assets/b/textures/item/two.png")).isEqualTo("B");
        assertThat(result.conflicts()).isEmpty();
    }

    @Test
    void narrowsThePackFormatToWhatEveryPackSupports(@TempDir Path dir) throws Exception {
        Path a = zip(dir.resolve("a.zip"), Map.of("pack.mcmeta", mcmeta(75, 88),
                "assets/a/x.txt", "a"));
        Path b = zip(dir.resolve("b.zip"), Map.of("pack.mcmeta", mcmeta(80, 107),
                "assets/b/x.txt", "b"));

        PackMerger.Result result = merger.merge(List.of(a, b), dir.resolve("out"), "combined");

        assertThat(result.minFormat()).isEqualTo(80);
        assertThat(result.maxFormat()).isEqualTo(88);
        assertThat(entry(result.file(), "pack.mcmeta"))
                .contains("\"min_format\":80")
                .contains("\"max_format\":88");
    }

    @Test
    void refusesPacksWithNoCommonFormat(@TempDir Path dir) throws Exception {
        Path a = zip(dir.resolve("a.zip"), Map.of("pack.mcmeta", mcmeta(9, 15),
                "assets/a/x.txt", "a"));
        Path b = zip(dir.resolve("b.zip"), Map.of("pack.mcmeta", mcmeta(80, 107),
                "assets/b/x.txt", "b"));

        assertThatThrownBy(() -> merger.merge(List.of(a, b), dir.resolve("out"), "combined"))
                .isInstanceOf(PackMerger.MergeException.class)
                .hasMessageContaining("do not share a single pack format");
    }

    @Test
    void reportsOverlappingFilesAndLetsTheLastPackWin(@TempDir Path dir) throws Exception {
        Path a = zip(dir.resolve("a.zip"), Map.of("pack.mcmeta", mcmeta(75, 88),
                "assets/x/textures/item/disc.png", "first"));
        Path b = zip(dir.resolve("b.zip"), Map.of("pack.mcmeta", mcmeta(75, 88),
                "assets/x/textures/item/disc.png", "second"));

        PackMerger.Result result = merger.merge(List.of(a, b), dir.resolve("out"), "combined");

        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0)).contains("assets/x/textures/item/disc.png");
        assertThat(entry(result.file(), "assets/x/textures/item/disc.png")).isEqualTo("second");
    }

    @Test
    void producesTheSameHashForTheSameInput(@TempDir Path dir) throws Exception {
        Path a = zip(dir.resolve("a.zip"), Map.of("pack.mcmeta", mcmeta(75, 88),
                "assets/a/x.txt", "a"));
        Path b = zip(dir.resolve("b.zip"), Map.of("pack.mcmeta", mcmeta(75, 88),
                "assets/b/x.txt", "b"));

        PackMerger.Result first = merger.merge(List.of(a, b), dir.resolve("out1"), "combined");
        PackMerger.Result second = merger.merge(List.of(a, b), dir.resolve("out2"), "combined");

        assertThat(second.sha1()).isEqualTo(first.sha1());
        assertThat(Hashes.sha1(second.file())).isEqualTo(first.sha1());
    }

    @Test
    void keepsOnlyTheCurrentCombinationInTheOutputFolder(@TempDir Path dir) throws Exception {
        Path a = zip(dir.resolve("a.zip"), Map.of("pack.mcmeta", mcmeta(75, 88),
                "assets/a/x.txt", "a"));
        Path b = zip(dir.resolve("b.zip"), Map.of("pack.mcmeta", mcmeta(75, 88),
                "assets/b/x.txt", "b"));
        Path c = zip(dir.resolve("c.zip"), Map.of("pack.mcmeta", mcmeta(75, 88),
                "assets/c/x.txt", "c"));
        Path out = dir.resolve("out");

        merger.merge(List.of(a, b), out, "combined");
        PackMerger.Result second = merger.merge(List.of(a, c), out, "combined");

        try (var stream = Files.list(out)) {
            assertThat(stream.map(path -> path.getFileName().toString()))
                    .containsExactly(second.file().getFileName().toString());
        }
    }

    // --- helpers ---------------------------------------------------------------------------

    private static String mcmeta(int min, int max) {
        return "{\"pack\":{\"description\":\"t\",\"min_format\":" + min
                + ",\"max_format\":" + max + "}}";
    }

    private static Path zip(Path target, Map<String, String> content) throws IOException {
        Map<String, String> sorted = new LinkedHashMap<>(content);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            for (Map.Entry<String, String> entry : sorted.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return target;
    }

    private static String entry(Path zipFile, String name) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            ZipEntry entry = zip.getEntry(name);
            assertThat(entry).as("entry %s", name).isNotNull();
            try (var in = zip.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
