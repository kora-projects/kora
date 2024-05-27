package ru.tinkoff.kora.s3.client.minio;

import ru.tinkoff.kora.s3.client.model.S3ObjectUpload;

record MinioS3ObjectUpload(String versionId) implements S3ObjectUpload { }
