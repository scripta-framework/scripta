package dev.scriptaframework.core.projection;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** A {@link CheckpointStore} that keeps checkpoints in memory, for tests and prototypes. */
public final class InMemoryCheckpointStore implements CheckpointStore {

    private final Map<String, Long> positions = new ConcurrentHashMap<>();

    @Override
    public long load(String projectionName) {
        Objects.requireNonNull(projectionName, "projectionName");
        return positions.getOrDefault(projectionName, -1L);
    }

    @Override
    public void save(String projectionName, long position) {
        Objects.requireNonNull(projectionName, "projectionName");
        if (position < -1) {
            throw new IllegalArgumentException("Checkpoint position must be at least -1, but was " + position);
        }
        positions.put(projectionName, position);
    }
}
