package ru.tinkoff.kora.micrometer.module.http.client.tag;

import io.micrometer.core.instrument.Tag;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.List;

public interface MicrometerHttpClientTagsProvider {

    List<Tag> getDurationTags(DurationKey key, HttpResultCode resultCode, HttpHeaders headers);
}
