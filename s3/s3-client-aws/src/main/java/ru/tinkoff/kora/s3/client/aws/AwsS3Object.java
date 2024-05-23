package ru.tinkoff.kora.s3.client.aws;

import org.jetbrains.annotations.ApiStatus.Internal;
import reactor.adapter.JdkFlowAdapter;
import ru.tinkoff.kora.s3.client.model.S3Body;
import ru.tinkoff.kora.s3.client.model.S3Object;
import ru.tinkoff.kora.s3.client.model.S3ObjectMeta;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.ResponsePublisher;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.time.Instant;
import java.util.Objects;

@Internal
public final class AwsS3Object implements S3Object, S3ObjectMeta {

    private final S3Body body;
    private final S3ObjectMeta meta;
    private final GetObjectResponse response;

    public AwsS3Object(String key, ResponseInputStream<GetObjectResponse> response) {
        GetObjectResponse res = response.response();
        long size = res.contentLength() == null ? -1 : res.contentLength();
        this.body = new AwsS3BodySync(res.contentEncoding(), res.contentType(), size, response);
        this.meta = new AwsS3ObjectMeta(key, res);
        this.response = res;
    }

    public AwsS3Object(String key, ResponsePublisher<GetObjectResponse> response) {
        GetObjectResponse res = response.response();
        long size = res.contentLength() == null ? -1 : res.contentLength();
        this.body = new AwsS3BodyAsync(res.contentEncoding(), res.contentType(), size, JdkFlowAdapter.publisherToFlowPublisher(response));
        this.meta = new AwsS3ObjectMeta(key, res);
        this.response = res;
    }

    @Override
    public String key() {
        return meta.key();
    }

    @Override
    public Instant modified() {
        return meta.modified();
    }

    @Override
    public long size() {
        return meta.size();
    }

    @Override
    public S3Body body() {
        return body;
    }

    public GetObjectResponse response() {
        return response;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        AwsS3Object s3Object = (AwsS3Object) object;
        return Objects.equals(meta, s3Object.meta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meta);
    }

    @Override
    public String toString() {
        return body.toString();
    }
}
