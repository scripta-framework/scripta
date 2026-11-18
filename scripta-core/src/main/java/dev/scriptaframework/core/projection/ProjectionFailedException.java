package dev.scriptaframework.core.projection;

import dev.scriptaframework.core.ScriptaException;
import dev.scriptaframework.core.eventstore.RecordedEvent;

/**
 * A {@link ProjectionRunner} stopped because something failed: the projection threw while
 * handling an event, or reading events or saving the checkpoint failed. The cause is attached.
 * The checkpoint never moves past an event that was not handled successfully.
 */
public final class ProjectionFailedException extends ScriptaException {

    private final String projectionName;

    private ProjectionFailedException(String projectionName, String message, Throwable cause) {
        super(message, cause);
        this.projectionName = projectionName;
    }

    static ProjectionFailedException handling(String projectionName, RecordedEvent event, Throwable cause) {
        return new ProjectionFailedException(projectionName, "Projection " + projectionName
                + " failed to handle the event at global position " + event.globalPosition()
                + " (stored as '" + event.eventType() + "' in stream " + event.streamId().value() + ")", cause);
    }

    static ProjectionFailedException processing(String projectionName, Throwable cause) {
        return new ProjectionFailedException(projectionName, "Projection " + projectionName
                + " stopped because reading events or saving its checkpoint failed", cause);
    }

    /** The name of the projection that failed. */
    public String projectionName() {
        return projectionName;
    }
}
