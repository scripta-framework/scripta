package dev.scriptaframework.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Placeholder so the build has a test to run; remove once real tests exist.
class PlaceholderTest {

    @Test
    void buildIsWired() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(21);
    }
}
