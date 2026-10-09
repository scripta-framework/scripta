package dev.scriptaframework.core.eventstore;

import dev.scriptaframework.core.ScriptaException;
import java.util.Objects;

/**
 * An append was rejected because the stream was not at the {@link ExpectedVersion expected
 * version}: someone else changed it since it was read. Nothing from the rejected append was
 * written. The usual response is to read the stream again and retry the command.
 */
public final class ConcurrencyException extends ScriptaException {

    private final StreamId streamId;
    private final ExpectedVersion expectedVersion;
    private final long actualVersion;

    /**
     * @param streamId        the stream the append targeted
     * @param expectedVersion the version the append expected
     * @param actualVersion   the stream's version at the time, {@code -1} if it had no events
     */
    public ConcurrencyException(StreamId streamId, ExpectedVersion expectedVersion, long actualVersion) {
        super(message(streamId, expectedVersion, actualVersion));
        this.streamId = streamId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public StreamId streamId() {
        return streamId;
    }

    public ExpectedVersion expectedVersion() {
        return expectedVersion;
    }

    /** The stream's version when the append was rejected, {@code -1} if it had no events. */
    public long actualVersion() {
        return actualVersion;
    }

    private static String message(StreamId streamId, ExpectedVersion expectedVersion, long actualVersion) {
        Objects.requireNonNull(streamId, "streamId");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        String expected = switch (expectedVersion) {
            case ExpectedVersion.Exact exact -> "expected at version " + exact.version();
            case ExpectedVersion.NoStream noStream -> "expected not to exist";
            case ExpectedVersion.Any any -> "expected at any version";
        };
        String actual = actualVersion == -1 ? "does not exist" : "is at version " + actualVersion;
        return "Stream " + streamId.value() + " was " + expected + " but " + actual;
    }
}
