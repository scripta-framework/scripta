package dev.scriptaframework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mechanics of the base class, independent of any realistic domain. */
class AggregateRootTest {

    sealed interface CounterEvent extends DomainEvent {
        record Created(String id) implements CounterEvent {}

        record Incremented() implements CounterEvent {}

        record CreatedWithoutId() implements CounterEvent {}

        /** Partially mutates state, then fails. */
        record Faulty() implements CounterEvent {}

        /** Assigns the id, then fails. */
        record FaultyCreated(String id) implements CounterEvent {}

        record AssignsIdAgain(String id) implements CounterEvent {}
    }

    static final class Counter extends AggregateRoot<String, CounterEvent> {

        final List<String> log = new ArrayList<>();
        int count;

        void handle(CounterEvent event) {
            raise(event);
        }

        void tryAssignId(String id) {
            assignId(id);
        }

        @Override
        protected void apply(CounterEvent event) {
            switch (event) {
                case CounterEvent.Created e -> assignId(e.id());
                case CounterEvent.Incremented e -> count++;
                case CounterEvent.CreatedWithoutId e -> { }
                case CounterEvent.Faulty e -> {
                    log.add("partially applied");
                    throw new IllegalStateException("boom");
                }
                case CounterEvent.FaultyCreated e -> {
                    assignId(e.id());
                    throw new IllegalStateException("boom");
                }
                case CounterEvent.AssignsIdAgain e -> assignId(e.id());
            }
        }
    }

    private static Counter created() {
        var counter = new Counter();
        counter.handle(new CounterEvent.Created("c-1"));
        counter.markCommitted();
        return counter;
    }

    @Test
    void newAggregateHasNoIdNoVersionAndNoEvents() {
        var counter = new Counter();

        assertThat(counter.id()).isNull();
        assertThat(counter.version()).isEqualTo(-1);
        assertThat(counter.uncommittedEvents()).isEmpty();
    }

    @Test
    void raiseIsAtomicWhenApplyThrows() {
        var counter = created();
        counter.handle(new CounterEvent.Incremented());

        assertThatThrownBy(() -> counter.handle(new CounterEvent.Faulty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(counter.uncommittedEvents()).containsExactly(new CounterEvent.Incremented());
        assertThat(counter.version()).isEqualTo(1);
        // The subclass's own state is not rolled back, as documented.
        assertThat(counter.log).containsExactly("partially applied");
    }

    @Test
    void failedFirstEventLeavesAggregateNew() {
        var counter = new Counter();

        assertThatThrownBy(() -> counter.handle(new CounterEvent.FaultyCreated("c-1")))
                .hasMessage("boom");

        assertThat(counter.id()).isNull();
        assertThat(counter.version()).isEqualTo(-1);
        assertThat(counter.uncommittedEvents()).isEmpty();
    }

    @Test
    void firstEventCanBeRetriedAfterFailing() {
        var counter = new Counter();
        assertThatThrownBy(() -> counter.handle(new CounterEvent.FaultyCreated("c-1")))
                .hasMessage("boom");

        counter.handle(new CounterEvent.Created("c-2"));

        assertThat(counter.id()).isEqualTo("c-2");
        assertThat(counter.version()).isZero();
        assertThat(counter.uncommittedEvents()).containsExactly(new CounterEvent.Created("c-2"));
    }

    @Test
    void firstEventMustAssignId() {
        var counter = new Counter();

        assertThatThrownBy(() -> counter.handle(new CounterEvent.CreatedWithoutId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CreatedWithoutId")
                .hasMessageContaining("did not assign an aggregate id");
        assertThat(counter.version()).isEqualTo(-1);
        assertThat(counter.uncommittedEvents()).isEmpty();
    }

    @Test
    void idCanOnlyBeAssignedWhileApplyingFirstEvent() {
        var counter = created();

        assertThatThrownBy(() -> counter.handle(new CounterEvent.AssignsIdAgain("c-2")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> counter.tryAssignId("c-2"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new Counter().tryAssignId("c-2"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(counter.id()).isEqualTo("c-1");
        assertThat(counter.version()).isZero();
    }

    @Test
    void uncommittedEventsIsAnUnmodifiableSnapshot() {
        var counter = created();
        counter.handle(new CounterEvent.Incremented());

        var events = counter.uncommittedEvents();
        counter.handle(new CounterEvent.Incremented());

        assertThat(events).hasSize(1);
        assertThatThrownBy(() -> events.add(new CounterEvent.Incremented()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyHistoryLeavesAggregateNew() {
        var counter = new Counter();

        counter.rehydrate(List.of());

        assertThat(counter.id()).isNull();
        assertThat(counter.version()).isEqualTo(-1);
    }

    @Test
    void cannotRehydrateAggregateWithState() {
        var counter = created();

        assertThatThrownBy(() -> counter.rehydrate(List.of(new CounterEvent.Incremented())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(counter.count).isZero();
        assertThat(counter.version()).isZero();
    }

    @Test
    void cannotRehydrateAggregateWithUncommittedEvents() {
        var counter = new Counter();
        counter.handle(new CounterEvent.Created("c-1"));

        assertThatThrownBy(() -> counter.rehydrate(List.of(new CounterEvent.Created("c-1"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotRehydrateTwice() {
        var counter = new Counter();
        counter.rehydrate(List.of(new CounterEvent.Created("c-1")));

        assertThatThrownBy(() -> counter.rehydrate(List.of(new CounterEvent.Created("c-1"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNulls() {
        var counter = new Counter();

        assertThatThrownBy(() -> counter.handle(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> counter.rehydrate(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> counter.handle(new CounterEvent.Created(null)))
                .isInstanceOf(NullPointerException.class);
        assertThat(counter.version()).isEqualTo(-1);
    }
}
