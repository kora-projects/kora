package ru.tinkoff.kora.s3.client.model;

public interface S3Object extends S3ObjectMeta {

    S3Body body();
}
