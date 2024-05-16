package ru.tinkoff.kora.s3.client.minio;

import io.minio.*;
import io.minio.credentials.Provider;
import io.minio.credentials.StaticProvider;
import io.minio.errors.*;
import io.minio.http.Method;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public final class Minio {

    private Minio() {}

    void check() {

        Provider credProvider = new StaticProvider("key", "secret", null);

        MinioClient minioClient = MinioClient.builder()
            .endpoint("http://127.0.0.1:9000")
            .credentials("minioadmin", "minioadmin")
            .build();

        try {
            String presignedObjectUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket("bucket")
                .object("key")
                .region("region")
                .build());

            GetObjectResponse object = minioClient.getObject(GetObjectArgs.builder()
                .bucket("bucket")
                .object("key")
                .region("region")
                .build());

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket("bucket")
                    .object("key")
                    .region("region")
                    .contentType("type")
                    .userMetadata(Map.of("m1", "m2"))
                    .stream(null, 1, -1)
                .build());

            minioClient.uploadObject(UploadObjectArgs.builder()
                .build());

            object.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
