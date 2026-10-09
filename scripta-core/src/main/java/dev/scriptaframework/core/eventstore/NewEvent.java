package dev.scriptaframework.core.eventstore;

import dev.scriptaframework.core.DomainEvent;
import java.util.Objects;
import java.util.UUID;

/**
 * An event to {@link EventStore#append(StreamId, ExpectedVersion, java.util.List) append}.
 *
 * <p>The event type is the stable name the event is stored under, for example
 * {@code "AccountOpened"}. It is given explicitly rather than derived from the payload's class,
 * so renaming a class does not change what is in the store.
 *
 * @param eventId   unique id of this event
 * @param eventType the stable, non-blank name of the event type
 * @param payload   the event itself
 * @param metadata  metadata stored alongside the event
 */
public record NewEvent(UUID eventId, String eventType, DomainEvent payload, EventMetadata metadata) {

    /**
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if {@code eventType} is blank
     */
    public NewEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("Event type must not be blank");
        }
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(metadata, "metadata");
    }

    /** An event with a random id and no metadata. */
    public NewEvent(String eventType, DomainEvent payload) {
        this(eventType, payload, EventMetadata.empty());
    }

    /** An event with a random id. */
    public NewEvent(String eventType, DomainEvent payload, EventMetadata metadata) {
        this(UUID.randomUUID(), eventType, payload, metadata);
    }
}
