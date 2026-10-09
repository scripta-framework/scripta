package dev.scriptaframework.core.eventstore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Text metadata stored alongside an event, such as the ids that tie it to the request that
 * caused it. Keys are non-blank and values non-null. The map is copied on construction, so an
 * instance never changes; {@link #with(String, String) with} returns a new one.
 *
 * @param entries the metadata entries
 */
public record EventMetadata(Map<String, String> entries) {

    /** Key of the id shared by all events that stem from the same original request. */
    public static final String CORRELATION_ID = "correlationId";

    /** Key of the id of the message or event that directly caused this one. */
    public static final String CAUSATION_ID = "causationId";

    private static final EventMetadata EMPTY = new EventMetadata(Map.of());

    /**
     * @throws NullPointerException     if the map, a key or a value is null
     * @throws IllegalArgumentException if a key is blank
     */
    public EventMetadata {
        Objects.requireNonNull(entries, "entries");
        entries.forEach((key, value) -> {
            Objects.requireNonNull(key, "metadata key");
            if (key.isBlank()) {
                throw new IllegalArgumentException("Metadata keys must not be blank");
            }
            Objects.requireNonNull(value, () -> "value of metadata key '" + key + "'");
        });
        entries = Map.copyOf(entries);
    }

    /** Metadata with no entries. */
    public static EventMetadata empty() {
        return EMPTY;
    }

    /**
     * Returns a copy of this metadata with the entry added, replacing any existing value for the
     * key.
     *
     * @throws NullPointerException     if the key or value is null
     * @throws IllegalArgumentException if the key is blank
     */
    public EventMetadata with(String key, String value) {
        var copy = new HashMap<>(entries);
        copy.put(key, value);
        return new EventMetadata(copy);
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    public Optional<String> correlationId() {
        return get(CORRELATION_ID);
    }

    public Optional<String> causationId() {
        return get(CAUSATION_ID);
    }
}
