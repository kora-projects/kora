package ru.tinkoff.kora.micrometer.module.http.server.tag;

import io.micrometer.core.instrument.Tag;

public interface MicrometerHttpServerTagsProvider {

    Iterable<Tag> getActiveRequestsTags(ActiveRequestsKey key);

    Iterable<Tag> getDurationTags(DurationKey key);

}
