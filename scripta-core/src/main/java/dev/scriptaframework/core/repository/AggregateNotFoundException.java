package dev.scriptaframework.core.repository;

import dev.scriptaframework.core.ScriptaException;
import dev.scriptaframework.core.eventstore.StreamId;

/**
 * An aggregate was required but its stream has no events, so it does not exist. Thrown by
 * {@link AggregateRepository#get(Object) get}; use {@link AggregateRepository#load(Object) load}
 * when absence is an expected outcome.
 */
public final class AggregateNotFoundException extends ScriptaException {

    private final StreamId streamId;

    AggregateNotFoundException(StreamId streamId) {
        super("No aggregate found in stream " + streamId.value());
        this.streamId = streamId;
    }

    /** The stream that has no events. */
    public StreamId streamId() {
        return streamId;
    }
}
