package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.jspecify.annotations.Nullable;

public class DefaultMeterBuilder<T extends Meter> implements MeterBuilder<T> {

    private final MeterProvider<T> provider;

    @Nullable
    private Tags tags;

    public DefaultMeterBuilder(MeterProvider<T> provider) {
        this.provider = provider;
        this.tags = null;
    }

    @Override
    public DefaultMeterBuilder<T> tags(@Nullable String... tags) {
        if (tags == null || tags.length == 0 || (tags.length == 1 && tags[0] == null)) {
            return this;
        }

        if (this.tags == null) {
            this.tags = Tags.of(tags);
        } else {
            for (int i = 0; i < tags.length; i += 2) {
                var key = tags[i];
                var value = tags[i + 1];
                if (key != null && value != null) {
                    this.tags.and(Tag.of(key, value));
                }
            }
        }

        return this;
    }

    @Override
    public DefaultMeterBuilder<T> tags(@Nullable Tag... tags) {
        if(tags == null || tags.length == 0 || (tags.length == 1 && tags[0] == null)) {
            return this;
        }

        if(this.tags == null) {
            this.tags = Tags.of(tags);
        } else {
            for (@Nullable Tag tag : tags) {
                if(tag != null) {
                    this.tags.and(tag);
                }
            }
        }

        return this;
    }

    @Override
    public DefaultMeterBuilder<T> tags(@Nullable Iterable<Tag> tags) {
        if (tags == null || !tags.iterator().hasNext()) {
            return this;
        }

        if (this.tags == null) {
            this.tags = Tags.of(tags);
        } else {
            for (Tag tag : tags) {
                if (tag != null) {
                    this.tags.and(tag);
                }
            }
        }

        return this;
    }

    @Override
    public DefaultMeterBuilder<T> tag(@Nullable String key, @Nullable String value) {
        if (key == null || value == null) {
            return this;
        }

        if (this.tags == null) {
            this.tags = Tags.of(key, value);
        } else {
            this.tags.and(Tag.of(key, value));
        }
        return this;
    }

    public Tags getTags() {
        return (tags == null) ? Tags.empty() : tags;
    }

    @Override
    public T build() {
        return provider.get(getTags());
    }

    @Override
    public String toString() {
        return "MeterBuilder{tags=" + tags + '}';
    }
}
