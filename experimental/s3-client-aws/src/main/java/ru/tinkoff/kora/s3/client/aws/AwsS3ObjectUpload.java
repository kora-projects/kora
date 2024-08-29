package ru.tinkoff.kora.s3.client.aws;

import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.s3.client.model.S3ObjectUpload;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ApiStatus.Experimental
public record AwsS3ObjectUpload(String versionId, PutObjectResponse response) implements S3ObjectUpload {

    public AwsS3ObjectUpload(PutObjectResponse response) {
        this(response.versionId(), response);
    }
}
