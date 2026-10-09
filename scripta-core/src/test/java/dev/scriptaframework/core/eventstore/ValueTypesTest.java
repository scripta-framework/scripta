package dev.scriptaframework.core.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.scriptaframework.core.DomainEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Validation of the small records the event store API is made of. */
class ValueTypesTest {

    record Happened() implements DomainEvent {}

    private static final StreamId STREAM = StreamId.of("account", "1");

    @Nested
    class ExpectedVersions {

        @Test
        void exactAcceptsZero() {
            assertThat(ExpectedVersion.exact(0)).isEqualTo(new ExpectedVersion.Exact(0));
        }

        @Test
        void exactRejectsNegativeVersions() {
            assertThatThrownBy(() -> ExpectedVersion.exact(-1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class NewEvents {

        @Test
        void getsARandomIdAndEmptyMetadataByDefault() {
            var first = new NewEvent("Happened", new Happened());
            var second = new NewEvent("Happened", new Happened());

            assertThat(first.eventId()).isNotEqualTo(second.eventId());
            assertThat(first.metadata()).isEqualTo(EventMetadata.empty());
        }

        @Test
        void rejectsABlankEventType() {
            assertThatThrownBy(() -> new NewEvent(" ", new Happened())).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNulls() {
            var payload = new Happened();
            var metadata = EventMetadata.empty();

            assertThatThrownBy(() -> new NewEvent(null, "Happened", payload, metadata))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new NewEvent(null, payload)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new NewEvent("Happened", null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new NewEvent("Happened", payload, null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class RecordedEvents {

        @Test
        void rejectsANegativeStreamVersion() {
            assertThatThrownBy(() -> recordedEvent(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsANegativeGlobalPosition() {
            assertThatThrownBy(() -> recordedEvent(0, -1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsABlankEventType() {
            assertThatThrownBy(() -> new RecordedEvent(UUID.randomUUID(), STREAM, 0, 0, "",
                    Instant.EPOCH, EventMetadata.empty(), new Happened()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNulls() {
            assertThatThrownBy(() -> new RecordedEvent(UUID.randomUUID(), null, 0, 0, "Happened",
                    Instant.EPOCH, EventMetadata.empty(), new Happened()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new RecordedEvent(UUID.randomUUID(), STREAM, 0, 0, "Happened",
                    null, EventMetadata.empty(), new Happened()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new RecordedEvent(UUID.randomUUID(), STREAM, 0, 0, "Happened",
                    Instant.EPOCH, EventMetadata.empty(), null))
                    .isInstanceOf(NullPointerException.class);
        }

        private static RecordedEvent recordedEvent(long streamVersion, long globalPosition) {
            return new RecordedEvent(UUID.randomUUID(), STREAM, streamVersion, globalPosition, "Happened",
                    Instant.EPOCH, EventMetadata.empty(), new Happened());
        }
    }

    @Nested
    class AppendResults {

        @Test
        void rejectsANegativeStreamVersion() {
            assertThatThrownBy(() -> new AppendResult(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsANegativeGlobalPosition() {
            assertThatThrownBy(() -> new AppendResult(0, -1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ConcurrencyExceptions {

        @Test
        void explainsAnExactVersionMismatch() {
            var exception = new ConcurrencyException(STREAM, ExpectedVersion.exact(3), 5);

            assertThat(exception).hasMessage("Stream account-1 was expected at version 3 but is at version 5");
        }

        @Test
        void explainsAStreamThatShouldNotExist() {
            var exception = new ConcurrencyException(STREAM, ExpectedVersion.noStream(), 2);

            assertThat(exception).hasMessage("Stream account-1 was expected not to exist but is at version 2");
        }

        @Test
        void explainsAMissingStream() {
            var exception = new ConcurrencyException(STREAM, ExpectedVersion.exact(0), -1);

            assertThat(exception).hasMessage("Stream account-1 was expected at version 0 but does not exist");
        }
    }
}
