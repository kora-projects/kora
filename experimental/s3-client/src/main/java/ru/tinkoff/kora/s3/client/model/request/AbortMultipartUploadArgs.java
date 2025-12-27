package ru.tinkoff.kora.s3.client.model.request;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;

import java.util.Map;


/**
 * @see <a href="AbortMultipartUpload">https://docs.aws.amazon.com/AmazonS3/latest/API/API_AbortMultipartUpload.html</a>
 */
public class AbortMultipartUploadArgs {
    /**
     * Confirms that the requester knows that they will be charged for the request. Bucket owners need not specify this parameter in their requests.
     * If either the source or destination S3 bucket has Requester Pays enabled, the requester will pay for corresponding charges to copy the object.
     * For information about downloading objects from Requester Pays buckets, see Downloading Objects in Requester Pays Buckets in the Amazon S3 User Guide.
     */
    @Nullable
    public String requestPayer;

    /**
     * The account ID of the expected bucket owner.
     * If the account ID that you provide does not match the actual owner of the bucket, the request fails with the HTTP status code 403 Forbidden (access denied).
     */
    @Nullable
    public String expectedBucketOwner;

    /**
     * If present, this header aborts an in progress multipart upload only if it was initiated on the provided timestamp.
     * If the initiated timestamp of the multipart upload does not match the provided value, the operation returns a 412 Precondition Failed error.
     * If the initiated timestamp matches or if the multipart upload doesn’t exist, the operation returns a 204 Success (No Content) response.
     */
    @Nullable
    public String ifMatchInitiatedTime;


    public void writeHeaders(MutableHttpHeaders headers) {
        if (this.requestPayer != null) {
            headers.add("x-amz-request-payer", this.requestPayer);
        }
        if (this.expectedBucketOwner != null) {
            headers.add("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
        if (this.ifMatchInitiatedTime != null) {
            headers.add("x-amz-if-match-initiated-time", this.ifMatchInitiatedTime);
        }
    }

    public void writeHeadersMap(Map<String, String> headers) {
        if (this.requestPayer != null) {
            headers.put("x-amz-request-payer", this.requestPayer);
        }
        if (this.expectedBucketOwner != null) {
            headers.put("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
        if (this.ifMatchInitiatedTime != null) {
            headers.put("x-amz-if-match-initiated-time", this.ifMatchInitiatedTime);
        }
    }

    public AbortMultipartUploadArgs setExpectedBucketOwner(@Nullable String expectedBucketOwner) {
        this.expectedBucketOwner = expectedBucketOwner;
        return this;
    }

    public AbortMultipartUploadArgs setIfMatchInitiatedTime(@Nullable String ifMatchInitiatedTime) {
        this.ifMatchInitiatedTime = ifMatchInitiatedTime;
        return this;
    }

    public AbortMultipartUploadArgs setRequestPayer(@Nullable String requestPayer) {
        this.requestPayer = requestPayer;
        return this;
    }
}
