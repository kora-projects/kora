package ru.tinkoff.kora.s3.client.minio;

import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.s3.client.model.S3ObjectUpload;

@ApiStatus.Experimental
record MinioS3ObjectUpload(String versionId) implements S3ObjectUpload { }
