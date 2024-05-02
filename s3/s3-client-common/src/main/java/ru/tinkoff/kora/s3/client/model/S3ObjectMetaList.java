package ru.tinkoff.kora.s3.client.model;

import java.util.List;

public interface S3ObjectMetaList {

    String name();

    String prefix();

    String delimiter();

    Integer maxKeys();

    List<String> commonPrefixes();

    List<S3ObjectMeta> objectMetas();
}
