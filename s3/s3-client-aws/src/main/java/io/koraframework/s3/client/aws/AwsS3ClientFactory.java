package io.koraframework.s3.client.aws;

import software.amazon.awssdk.services.s3.S3Client;

public interface AwsS3ClientFactory {

    S3Client create(AwsS3Config config);
}
