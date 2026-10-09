package dev.scriptaframework.core.eventstore;

/**
 * The version a stream must be at for an append to go ahead: the optimistic concurrency check
 * of {@link EventStore#append(StreamId, ExpectedVersion, java.util.List) append}.
 *
 * <p>A stream's version is the 0-based version of its last event, or {@code -1} if it has no
 * events.
 */
public sealed interface ExpectedVersion {

    /** No check: the append goes ahead whatever the stream's version, creating it if needed. */
    static ExpectedVersion any() {
        return new Any();
    }

    /** The stream must have no events yet. */
    static ExpectedVersion noStream() {
        return new NoStream();
    }

    /**
     * The stream must be at exactly this version.
     *
     * @throws IllegalArgumentException if {@code version} is negative; use {@link #noStream()}
     *                                  for a stream that must not exist
     */
    static ExpectedVersion exact(long version) {
        return new Exact(version);
    }

    /** See {@link ExpectedVersion#any()}. */
    record Any() implements ExpectedVersion {}

    /** See {@link ExpectedVersion#noStream()}. */
    record NoStream() implements ExpectedVersion {}

    /**
     * See {@link ExpectedVersion#exact(long)}.
     *
     * @param version the version the stream must be at, zero or more
     */
    record Exact(long version) implements ExpectedVersion {

        public Exact {
            if (version < 0) {
                throw new IllegalArgumentException(
                        "Expected version must not be negative, but was " + version);
            }
        }
    }
}
