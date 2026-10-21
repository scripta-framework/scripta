package dev.scriptaframework.core.eventstore;

import dev.scriptaframework.core.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An event as the store holds it: the appended {@link NewEvent} plus where and when it was
 * stored.
 *
 * @param eventId        unique id of the event
 * @param streamId       the stream the event belongs to
 * @param streamVersion  0-based position of the event in its stream, as in
 *                       {@link dev.scriptaframework.core.AggregateRoot#version()}
 * @param globalPosition position of the event across the whole store; see {@link EventStore}
 * @param eventType      the stable name of the event type
 * @param recordedAt     when the store recorded the event
 * @param metadata       metadata stored alongside the event
 * @param payload        the event itself
 */
public record RecordedEvent(
        UUID eventId,
        StreamId streamId,
        long streamVersion,
        long globalPosition,
        String eventType,
        Instant recordedAt,
        EventMetadata metadata,
        DomainEvent payload) {

    /**
     * @throws NullPointerException     if any reference argument is null
     * @throws IllegalArgumentException if a version or position is negative, or the event type is
     *                                  blank
     */
    public RecordedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(streamId, "streamId");
        if (streamVersion < 0) {
            throw new IllegalArgumentException("Stream version must not be negative, but was " + streamVersion);
        }
        if (globalPosition < 0) {
            throw new IllegalArgumentException("Global position must not be negative, but was " + globalPosition);
        }
        Objects.requireNonNull(eventType, "eventType");
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("Event type must not be blank");
        }
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(payload, "payload");
    }
}
