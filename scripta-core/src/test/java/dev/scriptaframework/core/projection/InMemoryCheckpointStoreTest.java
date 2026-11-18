package dev.scriptaframework.core.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InMemoryCheckpointStoreTest {

    private final InMemoryCheckpointStore checkpoints = new InMemoryCheckpointStore();

    @Test
    void anUnknownProjectionIsAtMinusOne() {
        assertThat(checkpoints.load("account-balances")).isEqualTo(-1);
    }

    @Test
    void loadReturnsTheLastSavedPosition() {
        checkpoints.save("account-balances", 3);
        checkpoints.save("account-balances", 7);

        assertThat(checkpoints.load("account-balances")).isEqualTo(7);
    }

    @Test
    void projectionsHaveSeparateCheckpoints() {
        checkpoints.save("account-balances", 3);

        assertThat(checkpoints.load("account-owners")).isEqualTo(-1);
    }

    @Test
    void savingMinusOneStartsTheProjectionOver() {
        checkpoints.save("account-balances", 3);

        checkpoints.save("account-balances", -1);

        assertThat(checkpoints.load("account-balances")).isEqualTo(-1);
    }

    @Test
    void aPositionBelowMinusOneIsRejected() {
        assertThatThrownBy(() -> checkpoints.save("account-balances", -2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Checkpoint position must be at least -1, but was -2");
    }
}
