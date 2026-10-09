package dev.scriptaframework.core.eventstore;

import java.util.Objects;

/**
 * Name of an event stream, in the form {@code <category>-<id>}, for example
 * {@code account-6f1c2e0a-93d4-4b8e-a2f1-0c6d1e7b5a90}.
 *
 * <p>The category is everything before the first {@code '-'}, so it cannot contain one; the id
 * after it may. Both are non-blank. Ids are taken as text on purpose: how an id becomes part of
 * a stream name is the caller's decision, not {@code toString()}'s.
 *
 * @param value the full stream name
 */
public record StreamId(String value) {

    private static final char SEPARATOR = '-';

    /**
     * @throws IllegalArgumentException if {@code value} is not of the form {@code <category>-<id>}
     *                                  with a non-blank category and id
     */
    public StreamId {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(SEPARATOR);
        if (separator == -1
                || value.substring(0, separator).isBlank()
                || value.substring(separator + 1).isBlank()) {
            throw new IllegalArgumentException(
                    "Stream id must have the form <category>-<id>, but was '" + value + "'");
        }
    }

    /**
     * Builds the stream name {@code <category>-<id>}.
     *
     * @throws IllegalArgumentException if the category is blank or contains {@code '-'}, or the
     *                                  id is blank
     */
    public static StreamId of(String category, String id) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(id, "id");
        if (category.isBlank() || category.indexOf(SEPARATOR) != -1) {
            throw new IllegalArgumentException(
                    "Category must be non-blank and must not contain '-', but was '" + category + "'");
        }
        return new StreamId(category + SEPARATOR + id);
    }

    /**
     * Wraps an already formed stream name.
     *
     * @throws IllegalArgumentException if {@code value} is not of the form {@code <category>-<id>}
     */
    public static StreamId of(String value) {
        return new StreamId(value);
    }

    /** The part of the name before the first {@code '-'}. */
    public String category() {
        return value.substring(0, value.indexOf(SEPARATOR));
    }
}
