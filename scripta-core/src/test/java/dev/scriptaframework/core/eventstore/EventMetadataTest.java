package dev.scriptaframework.core.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventMetadataTest {

    @Test
    void emptyHasNoEntries() {
        assertThat(EventMetadata.empty().entries()).isEmpty();
    }

    @Test
    void withReturnsANewInstanceAndLeavesTheOriginalUnchanged() {
        var original = EventMetadata.empty();

        var changed = original.with("tenant", "acme");

        assertThat(changed.get("tenant")).contains("acme");
        assertThat(original.get("tenant")).isEmpty();
    }

    @Test
    void withReplacesAnExistingValue() {
        var metadata = EventMetadata.empty().with("tenant", "acme").with("tenant", "globex");

        assertThat(metadata.get("tenant")).contains("globex");
    }

    @Test
    void correlationAndCausationIdsHaveTypedAccessors() {
        var metadata = EventMetadata.empty()
                .with(EventMetadata.CORRELATION_ID, "request-7")
                .with(EventMetadata.CAUSATION_ID, "command-3");

        assertThat(metadata.correlationId()).contains("request-7");
        assertThat(metadata.causationId()).contains("command-3");
    }

    @Test
    void missingIdsAreEmpty() {
        assertThat(EventMetadata.empty().correlationId()).isEmpty();
        assertThat(EventMetadata.empty().causationId()).isEmpty();
    }

    @Test
    void theGivenMapIsCopied() {
        var source = new HashMap<String, String>();
        source.put("tenant", "acme");

        var metadata = new EventMetadata(source);
        source.put("tenant", "globex");

        assertThat(metadata.get("tenant")).contains("acme");
        assertThatThrownBy(() -> metadata.entries().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBlankKeys() {
        assertThatThrownBy(() -> new EventMetadata(Map.of(" ", "value")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventMetadata.empty().with("", "value"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNulls() {
        var nullValue = new HashMap<String, String>();
        nullValue.put("tenant", null);

        assertThatThrownBy(() -> new EventMetadata(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EventMetadata(nullValue)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> EventMetadata.empty().with(null, "value")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> EventMetadata.empty().with("tenant", null)).isInstanceOf(NullPointerException.class);
    }
}
