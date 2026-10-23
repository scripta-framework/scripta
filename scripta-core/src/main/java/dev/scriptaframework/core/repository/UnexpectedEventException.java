package dev.scriptaframework.core.repository;

import dev.scriptaframework.core.ScriptaException;
import dev.scriptaframework.core.eventstore.RecordedEvent;
import dev.scriptaframework.core.eventstore.StreamId;

/**
 * An aggregate's stream holds an event that is not of the aggregate's event type, so the
 * aggregate cannot be loaded. This usually means two kinds of aggregate share a stream category,
 * or events were written to the stream by other means.
 */
public final class UnexpectedEventException extends ScriptaException {

    private final StreamId streamId;

    UnexpectedEventException(StreamId streamId, RecordedEvent event, Class<?> expectedType) {
        super("Stream " + streamId.value() + " contains an event of class " + event.payload().getClass().getName()
                + " (stored as '" + event.eventType() + "', version " + event.streamVersion()
                + "), which is not an instance of " + expectedType.getSimpleName());
        this.streamId = streamId;
    }

    /** The stream holding the unexpected event. */
    public StreamId streamId() {
        return streamId;
    }
}
