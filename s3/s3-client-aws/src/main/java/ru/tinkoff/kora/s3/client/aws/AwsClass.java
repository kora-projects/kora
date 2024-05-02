package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.http.client.common.HttpClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.auth.aws.internal.signer.io.ResettableContentStreamProvider;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.util.List;

public class AwsClass {

    private void setup(HttpClient httpClient,
                       List<ExecutionInterceptor> interceptors) {
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .checksumValidationEnabled(false)
                .chunkedEncodingEnabled(true)
                .build();

        String accessKeyId = "";
        String secretAccessKey = "";

        AwsCredentialsProvider credentialsProvider = () -> AwsBasicCredentials.create(accessKeyId, secretAccessKey);

        S3Client client = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .httpClient(new KoraAwsSdkHttpClient(httpClient))
                .serviceConfiguration(serviceConfiguration)
                .overrideConfiguration(b -> interceptors.forEach(b::addExecutionInterceptor))
                .build();

        S3AsyncClient asyncClient = S3AsyncClient.builder()
                .credentialsProvider(credentialsProvider)
                .httpClient(new KoraAwsSdkHttpClient(httpClient))
                .serviceConfiguration(serviceConfiguration)
                .overrideConfiguration(b -> interceptors.forEach(b::addExecutionInterceptor))
                .build();


        // 4
        final String bucket = "b";
        final String key1 = "k1";

        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key1)
                .build());
        client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder()
                        .objects(ObjectIdentifier.builder()
                                .key(key1)
                                .build())
                        .build())
                .build());
        // 3
        client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key1)
                .build()).response();
        // 2
        client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix("k")
                .maxKeys(10)
                .continuationToken("cont-token")
                .build());
        // 3
        client.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key1)
                .acl(ObjectCannedACL.PRIVATE)
                .build(), RequestBody.fromContentProvider(new ResettableContentStreamProvider(null), ""));
    }
}
