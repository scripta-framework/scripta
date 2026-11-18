package dev.scriptaframework.core.projection;

import dev.scriptaframework.core.eventstore.RecordedEvent;

/**
 * Builds something from committed events, typically a read model. A {@link ProjectionRunner}
 * passes it every event in the store, in global order; it simply ignores the events it does not
 * care about.
 *
 * <p><strong>Handling must be idempotent.</strong> Delivery is at-least-once: if the process stops
 * after an event was handled but before the checkpoint was saved, the event is delivered again.
 * A common way to cope is to remember, per stream, the last {@link RecordedEvent#streamVersion()
 * stream version} applied and skip anything at or below it.
 *
 * <pre>{@code
 * final class AccountBalances implements Projection {
 *
 *     public String name() {
 *         return "account-balances";
 *     }
 *
 *     public void handle(RecordedEvent event) {
 *         if (event.payload() instanceof MoneyDeposited deposited) {
 *             // update the read model, skipping events already applied
 *         }
 *     }
 * }
 * }</pre>
 */
public interface Projection {

    /**
     * The name the projection's checkpoint is stored under, such as {@code "account-balances"}.
     * It must stay stable: under a new name the projection starts again from the first event.
     */
    String name();

    /**
     * Applies one event. Throwing stops the runner without advancing past this event; see
     * {@link ProjectionRunner}.
     */
    void handle(RecordedEvent event);
}
