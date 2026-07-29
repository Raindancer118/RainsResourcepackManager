package de.raindancer.rrp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import de.raindancer.rrp.util.Hashes;
import de.raindancer.rrp.util.SafeFileName;
import org.junit.jupiter.api.Test;

class SafeFileNameTest {

    @Test
    void keepsOrdinaryNames() {
        assertThat(SafeFileName.of("yeukpack", "x")).isEqualTo("yeukpack");
        assertThat(SafeFileName.of("yeukpack-datapack.zip", "x")).isEqualTo("yeukpack-datapack.zip");
    }

    @Test
    void cannotEscapeTheFolder() {
        assertThat(SafeFileName.of("../../etc/passwd", "x")).isEqualTo("passwd");
        assertThat(SafeFileName.of("..", "fallback")).isEqualTo("fallback");
        assertThat(SafeFileName.of("/absolute/path.zip", "x")).isEqualTo("path.zip");
        assertThat(SafeFileName.of("....//..//x.zip", "x")).doesNotContain("..");
    }

    @Test
    void fallsBackWhenNothingUsableIsLeft() {
        assertThat(SafeFileName.of("", "fallback")).isEqualTo("fallback");
        assertThat(SafeFileName.of("///", "fallback")).isEqualTo("fallback");
        assertThat(SafeFileName.of(null, "fallback")).isEqualTo("fallback");
    }

    @Test
    void packIdChangesWithTheContentButNotBetweenRuns() {
        UUID first = Hashes.packId("yeukpack", "aaa");
        UUID same = Hashes.packId("yeukpack", "aaa");
        UUID afterUpdate = Hashes.packId("yeukpack", "bbb");

        assertThat(same).isEqualTo(first);
        assertThat(afterUpdate).isNotEqualTo(first);
    }
}
