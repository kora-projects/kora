package ru.tinkoff.kora.s3.client.model;

import java.util.List;

public interface S3ObjectMetaList {

    String prefix();

    List<S3ObjectMeta> metas();
}