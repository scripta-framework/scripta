package dev.scriptaframework.core.eventstore;

/**
 * Where a successful append left the stream and the store.
 *
 * @param streamVersion  the stream's new version: the version of the last appended event
 * @param globalPosition the global position of the last appended event
 */
public record AppendResult(long streamVersion, long globalPosition) {

    /** @throws IllegalArgumentException if the version or position is negative */
    public AppendResult {
        if (streamVersion < 0) {
            throw new IllegalArgumentException("Stream version must not be negative, but was " + streamVersion);
        }
        if (globalPosition < 0) {
            throw new IllegalArgumentException("Global position must not be negative, but was " + globalPosition);
        }
    }
}
