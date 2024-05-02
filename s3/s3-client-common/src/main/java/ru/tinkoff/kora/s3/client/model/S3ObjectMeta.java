package ru.tinkoff.kora.s3.client.model;

import java.time.Instant;

public interface S3ObjectMeta {

    String key();

    Instant modified();

    long size();
}
