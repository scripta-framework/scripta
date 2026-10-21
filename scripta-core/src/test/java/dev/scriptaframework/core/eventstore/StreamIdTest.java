package dev.scriptaframework.core.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StreamIdTest {

    @Test
    void joinsCategoryAndIdWithADash() {
        assertThat(StreamId.of("account", "42").value()).isEqualTo("account-42");
    }

    @Test
    void categoryIsThePartBeforeTheFirstDash() {
        assertThat(StreamId.of("account-42").category()).isEqualTo("account");
    }

    @Test
    void idMayContainDashes() {
        var streamId = StreamId.of("account", "6f1c2e0a-93d4-4b8e-a2f1-0c6d1e7b5a90");

        assertThat(streamId.value()).isEqualTo("account-6f1c2e0a-93d4-4b8e-a2f1-0c6d1e7b5a90");
        assertThat(streamId.category()).isEqualTo("account");
    }

    @Test
    void aFormedNameEqualsTheSameNameBuiltFromItsParts() {
        assertThat(StreamId.of("account-42")).isEqualTo(StreamId.of("account", "42"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "account-with-dash"})
    void rejectsABlankCategoryOrOneContainingADash(String category) {
        assertThatThrownBy(() -> StreamId.of(category, "42")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void rejectsABlankId(String id) {
        assertThatThrownBy(() -> StreamId.of("account", id)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "account", "-42", " -42", "account-", "account- "})
    void rejectsANameThatIsNotCategoryDashId(String value) {
        assertThatThrownBy(() -> StreamId.of(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> StreamId.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StreamId.of(null, "42")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StreamId.of("account", null)).isInstanceOf(NullPointerException.class);
    }
}
