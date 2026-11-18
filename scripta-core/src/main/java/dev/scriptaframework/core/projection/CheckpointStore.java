package dev.scriptaframework.core.projection;

/**
 * Remembers, per projection name, the global position of the last event the projection handled.
 * {@code -1} means it has handled nothing yet.
 *
 * <p>Implementations are thread-safe.
 */
public interface CheckpointStore {

    /**
     * @return the last handled global position for the projection, or {@code -1} if none was
     *         saved
     */
    long load(String projectionName);

    /**
     * Records the last handled global position for the projection, replacing any earlier one.
     * Saving {@code -1} makes the projection start again from the first event.
     *
     * @throws IllegalArgumentException if {@code position} is less than {@code -1}
     */
    void save(String projectionName, long position);
}
