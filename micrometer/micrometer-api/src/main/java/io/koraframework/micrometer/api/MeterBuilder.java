package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.jspecify.annotations.Nullable;

public interface MeterBuilder<T extends Meter> {

    /**
     * @param tags Must be an even number of arguments representing key/value pairs of
     *             tags.
     * @return The gauge builder with added tags.
     */
    MeterBuilder<T> tags(@Nullable String... tags);

    /**
     * @param tags Must be an even number of arguments representing key/value pairs of
     *             tags.
     * @return The gauge builder with added tags.
     */
    MeterBuilder<T> tags(@Nullable Tag... tags);

    /**
     * @param tags Tags to add to the eventual
     * @return The gauge builder with added tags.
     */
    MeterBuilder<T> tags(@Nullable Iterable<Tag> tags);

    /**
     * @param key   The tag key.
     * @param value The tag value.
     * @return The gauge builder with a single added tag.
     */
    MeterBuilder<T> tag(@Nullable String key, @Nullable String value);

    Tags getTags();

    T build();
}
