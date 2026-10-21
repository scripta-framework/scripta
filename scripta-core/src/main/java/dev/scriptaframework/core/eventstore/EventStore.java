package dev.scriptaframework.core.eventstore;

import java.util.List;

/**
 * Append-only storage of events, organised in streams.
 *
 * <h2>Streams and versions</h2>
 * Each event belongs to one stream. Within a stream, events are numbered by a 0-based
 * <em>stream version</em> with no gaps: the first event is version 0, the next 1, and so on. A
 * stream's version is the version of its last event, or {@code -1} if it has no events. A stream
 * exists exactly when it has at least one event; there are no empty streams.
 *
 * <h2>Appending</h2>
 * {@link #append(StreamId, ExpectedVersion, List) append} first checks the stream's current
 * version against the {@link ExpectedVersion}:
 * <ul>
 *   <li>{@link ExpectedVersion.Any Any} always passes;</li>
 *   <li>{@link ExpectedVersion.NoStream NoStream} passes only if the stream has no events;</li>
 *   <li>{@link ExpectedVersion.Exact Exact(v)} passes only if the stream is at version
 *       {@code v}.</li>
 * </ul>
 * If the check fails, the append throws {@link ConcurrencyException}. The check and the write
 * are one atomic step: of several appends racing with the same expected version, at most one
 * succeeds.
 *
 * <p>A batch is written atomically, all or nothing. Its events take consecutive stream versions
 * in list order, and no other append's events are interleaved with them in the stream.
 *
 * <p>Appending an empty list is rejected with {@link IllegalArgumentException}: there would be
 * no result to report and nothing for the version check to protect.
 *
 * <p>Behaviour for an event whose {@link NewEvent#eventId() eventId} is already in the store is
 * currently unspecified.
 *
 * <h2>Global positions</h2>
 * Every event also has a 0-based <em>global position</em> across the whole store. Positions
 * strictly increase in the order events were appended, but may have gaps; for example, a
 * database sequence can skip numbers after a rolled-back transaction.
 *
 * <p>Visibility guarantee: once {@link #readAll(long, int) readAll} has returned an event at
 * position {@code p}, no later read returns a newly visible event at a position lower than
 * {@code p}. A reader can therefore resume from its last position + 1 and never miss an event.
 * Every implementation must honour this; a store that hands out positions before its writes
 * become visible has to hold readers back until the positions below are settled.
 *
 * <h2>Reading</h2>
 * Both read methods take an inclusive starting point and return events in order: by stream
 * version for {@link #readStream(StreamId, long) readStream}, by global position for
 * {@code readAll}. A starting point past the end gives an empty list.
 *
 * <p>Implementations are thread-safe.
 */
public interface EventStore {

    /**
     * Appends events to a stream if it is at the expected version.
     *
     * @param stream   the stream to append to; created by the first append
     * @param expected the version the stream must be at
     * @param events   the events to append, in order; not empty
     * @return the stream's new version and the global position of the last appended event
     * @throws ConcurrencyException     if the stream is not at the expected version; nothing is
     *                                  written
     * @throws IllegalArgumentException if {@code events} is empty
     * @throws NullPointerException     if any argument or event is null
     */
    AppendResult append(StreamId stream, ExpectedVersion expected, List<NewEvent> events);

    /**
     * Reads a stream's events from a version onwards.
     *
     * @param stream      the stream to read
     * @param fromVersion the first version to return, inclusive; 0 for the whole stream
     * @return the events with a stream version of at least {@code fromVersion}, in version order;
     *         empty if the stream does not exist
     * @throws IllegalArgumentException if {@code fromVersion} is negative
     */
    List<RecordedEvent> readStream(StreamId stream, long fromVersion);

    /**
     * Reads events across all streams from a global position onwards. See the visibility
     * guarantee in the class description for resuming reads.
     *
     * @param fromGlobalPosition the first global position to return, inclusive; 0 for the
     *                           beginning
     * @param maxCount           the maximum number of events to return, at least 1
     * @return up to {@code maxCount} events with a global position of at least
     *         {@code fromGlobalPosition}, in global position order
     * @throws IllegalArgumentException if {@code fromGlobalPosition} is negative or
     *                                  {@code maxCount} is less than 1
     */
    List<RecordedEvent> readAll(long fromGlobalPosition, int maxCount);
}
