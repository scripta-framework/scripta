package dev.scriptaframework.core.eventstore;

import static dev.scriptaframework.core.eventstore.ExpectedVersion.any;
import static dev.scriptaframework.core.eventstore.ExpectedVersion.exact;
import static dev.scriptaframework.core.eventstore.ExpectedVersion.noStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.scriptaframework.core.DomainEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The behaviour every {@link EventStore} must have. Extend it and implement
 * {@link #createStore()} to check an implementation; each test gets a fresh, empty store.
 */
public abstract class EventStoreContractTest {

    /** Payload used throughout the contract. */
    protected record Happened(String what) implements DomainEvent {}

    /** Returns a new, empty store. */
    protected abstract EventStore createStore();

    private final StreamId stream = StreamId.of("account", "1");
    private final StreamId otherStream = StreamId.of("account", "2");
    private EventStore store;

    @BeforeEach
    void setUp() {
        store = createStore();
    }

    @Nested
    class Appending {

        @Test
        void noStreamCreatesANewStream() {
            var result = store.append(stream, noStream(), List.of(event("opened")));

            assertThat(result.streamVersion()).isZero();
            assertThat(payloads(store.readStream(stream, 0))).containsExactly("opened");
        }

        @Test
        void anyCreatesANewStream() {
            var result = store.append(stream, any(), List.of(event("opened")));

            assertThat(result.streamVersion()).isZero();
            assertThat(payloads(store.readStream(stream, 0))).containsExactly("opened");
        }

        @Test
        void anyAppendsToAnExistingStream() {
            store.append(stream, noStream(), List.of(event("opened")));

            var result = store.append(stream, any(), List.of(event("deposited")));

            assertThat(result.streamVersion()).isEqualTo(1);
        }

        @Test
        void exactAppendsWhenTheVersionMatches() {
            store.append(stream, noStream(), List.of(event("opened")));

            var result = store.append(stream, exact(0), List.of(event("deposited")));

            assertThat(result.streamVersion()).isEqualTo(1);
            assertThat(payloads(store.readStream(stream, 0))).containsExactly("opened", "deposited");
        }

        @Test
        void exactIsRejectedWhenTheVersionDoesNotMatch() {
            store.append(stream, noStream(), List.of(event("opened"), event("deposited")));

            var conflict = catchThrowableOfType(ConcurrencyException.class,
                    () -> store.append(stream, exact(0), List.of(event("withdrawn"))));

            assertThat(conflict.streamId()).isEqualTo(stream);
            assertThat(conflict.expectedVersion()).isEqualTo(exact(0));
            assertThat(conflict.actualVersion()).isEqualTo(1);
            assertThat(payloads(store.readStream(stream, 0))).containsExactly("opened", "deposited");
        }

        @Test
        void exactIsRejectedForAMissingStream() {
            var conflict = catchThrowableOfType(ConcurrencyException.class,
                    () -> store.append(stream, exact(0), List.of(event("opened"))));

            assertThat(conflict.actualVersion()).isEqualTo(-1);
            assertThat(store.readStream(stream, 0)).isEmpty();
        }

        @Test
        void noStreamIsRejectedForAnExistingStream() {
            store.append(stream, noStream(), List.of(event("opened")));

            var conflict = catchThrowableOfType(ConcurrencyException.class,
                    () -> store.append(stream, noStream(), List.of(event("opened again"))));

            assertThat(conflict.expectedVersion()).isEqualTo(noStream());
            assertThat(conflict.actualVersion()).isZero();
            assertThat(payloads(store.readStream(stream, 0))).containsExactly("opened");
        }

        @Test
        void anEmptyListIsRejected() {
            assertThatThrownBy(() -> store.append(stream, any(), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(store.readAll(0, 10)).isEmpty();
        }

        @Test
        void aBatchTakesConsecutiveVersionsInListOrder() {
            var result = store.append(stream, noStream(),
                    List.of(event("opened"), event("deposited"), event("withdrawn")));

            var recorded = store.readStream(stream, 0);
            assertThat(payloads(recorded)).containsExactly("opened", "deposited", "withdrawn");
            assertThat(recorded).extracting(RecordedEvent::streamVersion).containsExactly(0L, 1L, 2L);
            assertThat(result.streamVersion()).isEqualTo(2);
            assertThat(result.globalPosition()).isEqualTo(recorded.getLast().globalPosition());
        }

        @Test
        void aConflictingBatchWritesNothing() {
            store.append(stream, noStream(), List.of(event("opened")));

            assertThatThrownBy(() -> store.append(stream, exact(5),
                    List.of(event("deposited"), event("withdrawn"), event("closed"))))
                    .isInstanceOf(ConcurrencyException.class);

            assertThat(payloads(store.readStream(stream, 0))).containsExactly("opened");
            assertThat(payloads(store.readAll(0, 10))).containsExactly("opened");
        }

        @Test
        void theRecordedEventKeepsWhatWasAppended() {
            var metadata = EventMetadata.empty().with(EventMetadata.CORRELATION_ID, "request-7");
            var appended = new NewEvent("Happened", new Happened("opened"), metadata);

            store.append(stream, noStream(), List.of(appended));

            var recorded = store.readStream(stream, 0).getFirst();
            assertThat(recorded.eventId()).isEqualTo(appended.eventId());
            assertThat(recorded.streamId()).isEqualTo(stream);
            assertThat(recorded.eventType()).isEqualTo("Happened");
            assertThat(recorded.metadata()).isEqualTo(metadata);
            assertThat(recorded.payload()).isEqualTo(new Happened("opened"));
        }
    }

    @Nested
    class ReadingAStream {

        @Test
        void aMissingStreamReadsAsEmpty() {
            assertThat(store.readStream(stream, 0)).isEmpty();
        }

        @Test
        void eventsComeBackInVersionOrderAcrossAppends() {
            store.append(stream, noStream(), List.of(event("opened")));
            store.append(stream, exact(0), List.of(event("deposited"), event("withdrawn")));
            store.append(stream, exact(2), List.of(event("closed")));

            var recorded = store.readStream(stream, 0);

            assertThat(payloads(recorded)).containsExactly("opened", "deposited", "withdrawn", "closed");
            assertThat(recorded).extracting(RecordedEvent::streamVersion).containsExactly(0L, 1L, 2L, 3L);
        }

        @Test
        void fromVersionIsInclusive() {
            store.append(stream, noStream(), List.of(event("opened"), event("deposited"), event("withdrawn")));

            var recorded = store.readStream(stream, 1);

            assertThat(payloads(recorded)).containsExactly("deposited", "withdrawn");
        }

        @Test
        void readingPastTheEndGivesNothing() {
            store.append(stream, noStream(), List.of(event("opened")));

            assertThat(store.readStream(stream, 1)).isEmpty();
        }

        @Test
        void onlyTheRequestedStreamIsReturned() {
            store.append(stream, noStream(), List.of(event("mine")));
            store.append(otherStream, noStream(), List.of(event("theirs")));
            store.append(stream, exact(0), List.of(event("mine again")));

            assertThat(payloads(store.readStream(stream, 0))).containsExactly("mine", "mine again");
        }

        @Test
        void aNegativeFromVersionIsRejected() {
            assertThatThrownBy(() -> store.readStream(stream, -1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ReadingAll {

        @Test
        void anEmptyStoreReadsAsEmpty() {
            assertThat(store.readAll(0, 10)).isEmpty();
        }

        @Test
        void eventsFromAllStreamsComeBackInAppendOrder() {
            store.append(stream, noStream(), List.of(event("first")));
            store.append(otherStream, noStream(), List.of(event("second"), event("third")));
            store.append(stream, exact(0), List.of(event("fourth")));

            var recorded = store.readAll(0, 10);

            assertThat(payloads(recorded)).containsExactly("first", "second", "third", "fourth");
            assertThat(recorded).isSortedAccordingTo(Comparator.comparingLong(RecordedEvent::globalPosition));
            assertThat(recorded).extracting(RecordedEvent::globalPosition).doesNotHaveDuplicates();
        }

        @Test
        void fromGlobalPositionIsInclusive() {
            store.append(stream, noStream(), List.of(event("first"), event("second")));
            store.append(otherStream, noStream(), List.of(event("third")));
            var third = store.readAll(0, 10).get(2);

            var recorded = store.readAll(third.globalPosition(), 10);

            assertThat(recorded).containsExactly(third);
        }

        @Test
        void atMostMaxCountEventsAreReturned() {
            store.append(stream, noStream(), List.of(event("first"), event("second")));
            store.append(otherStream, noStream(), List.of(event("third")));

            var recorded = store.readAll(0, 2);

            assertThat(payloads(recorded)).containsExactly("first", "second");
        }

        @Test
        void resumingAfterTheLastPositionContinuesWhereTheReadStopped() {
            store.append(stream, noStream(), List.of(event("first"), event("second")));
            store.append(otherStream, noStream(), List.of(event("third")));
            var firstPage = store.readAll(0, 2);

            var secondPage = store.readAll(firstPage.getLast().globalPosition() + 1, 2);

            assertThat(payloads(secondPage)).containsExactly("third");
        }

        @Test
        void aNegativeFromGlobalPositionIsRejected() {
            assertThatThrownBy(() -> store.readAll(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void aMaxCountBelowOneIsRejected() {
            assertThatThrownBy(() -> store.readAll(0, 0)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Concurrency {

        private static final int WRITERS = 8;
        private static final int ROUNDS = 25;
        private static final int EVENTS_PER_WRITER = 200;
        private static final int PAGE_SIZE = 50;

        @Test
        void ofManyAppendsAtTheSameExactVersionExactlyOneSucceeds() throws Exception {
            store.append(stream, noStream(), List.of(event("opened")));

            List<Integer> winnersPerRound = new ArrayList<>();
            for (long version = 0; version < ROUNDS; version++) {
                winnersPerRound.add(raceAppendsAt(exact(version)));
            }

            assertThat(winnersPerRound).hasSize(ROUNDS).containsOnly(1);
            assertThat(store.readStream(stream, 0))
                    .extracting(RecordedEvent::streamVersion)
                    .containsExactlyElementsOf(LongStream.rangeClosed(0, ROUNDS).boxed().toList());
        }

        @Test
        void aReaderResumingAfterItsLastPositionSeesEveryEventOnceAndInOrder() throws Exception {
            try (ExecutorService executor = Executors.newFixedThreadPool(WRITERS)) {
                CompletableFuture<Void> writing = startWriters(executor);

                List<RecordedEvent> seen = readAllUntilDone(writing);

                assertThat(seen)
                        .hasSize(WRITERS * EVENTS_PER_WRITER)
                        .containsExactlyElementsOf(store.readAll(0, Integer.MAX_VALUE))
                        .isSortedAccordingTo(Comparator.comparingLong(RecordedEvent::globalPosition));
            }
        }

        /** Lets all writers append at the same expected version at once; returns how many succeeded. */
        private int raceAppendsAt(ExpectedVersion expected) throws Exception {
            var start = new CountDownLatch(1);
            var winners = new AtomicInteger();
            try (ExecutorService executor = Executors.newFixedThreadPool(WRITERS)) {
                List<Future<?>> attempts = new ArrayList<>();
                for (int writer = 0; writer < WRITERS; writer++) {
                    attempts.add(executor.submit(() -> {
                        start.await();
                        try {
                            store.append(stream, expected, List.of(event("raced")));
                            winners.incrementAndGet();
                        } catch (ConcurrencyException lost) {
                            // Losing the race is the expected outcome for all but one writer.
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> attempt : attempts) {
                    attempt.get(); // rethrows anything other than a lost race
                }
            }
            return winners.get();
        }

        /** Each writer appends single events to its own stream, as fast as it can. */
        private CompletableFuture<Void> startWriters(ExecutorService executor) {
            var writers = new ArrayList<CompletableFuture<Void>>();
            for (int writer = 0; writer < WRITERS; writer++) {
                var writerStream = StreamId.of("writer", String.valueOf(writer));
                writers.add(CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < EVENTS_PER_WRITER; i++) {
                        store.append(writerStream, any(), List.of(event(writerStream.value() + "/" + i)));
                    }
                }, executor));
            }
            return CompletableFuture.allOf(writers.toArray(CompletableFuture[]::new));
        }

        /** Polls readAll from the last seen position + 1 until the writers are done and nothing is left. */
        private List<RecordedEvent> readAllUntilDone(CompletableFuture<Void> writing) {
            List<RecordedEvent> seen = new ArrayList<>();
            long next = 0;
            while (true) {
                boolean writersDone = writing.isDone();
                List<RecordedEvent> page = store.readAll(next, PAGE_SIZE);
                if (page.isEmpty() && writersDone) {
                    writing.join(); // surfaces any writer failure
                    return seen;
                }
                seen.addAll(page);
                if (!page.isEmpty()) {
                    next = page.getLast().globalPosition() + 1;
                }
            }
        }
    }

    private static NewEvent event(String what) {
        return new NewEvent("Happened", new Happened(what));
    }

    private static List<String> payloads(List<RecordedEvent> events) {
        return events.stream().map(event -> ((Happened) event.payload()).what()).toList();
    }
}
