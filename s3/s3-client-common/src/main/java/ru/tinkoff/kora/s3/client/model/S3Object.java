package ru.tinkoff.kora.s3.client.model;

import java.time.Instant;

public interface S3Object {

    String key();

    Instant modified();

    long size();

    S3Body body();
}
