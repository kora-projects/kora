package ru.tinkoff.kora.s3.client.model.request;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;

import java.util.Map;

/**
 * @see <a href="UploadPart">https://docs.aws.amazon.com/AmazonS3/latest/API/API_UploadPart.html</a>
 */
public class UploadPartArgs {
    /**
     * Specifies the algorithm to use when encrypting the object (for example, AES256).
     */
    @Nullable
    public String sseCustomerAlgorithm;

    /**
     * Specifies the customer-provided encryption key for Amazon S3 to use in encrypting data.
     * This value is used to store the object and then it is discarded; Amazon S3 does not store the encryption key.
     * The key must be appropriate for use with the algorithm specified in the x-amz-server-side-encryption-customer-algorithm header.
     */
    @Nullable
    public String sseCustomerKey;

    /**
     * Specifies the 128-bit MD5 digest of the encryption key according to RFC 1321.
     * Amazon S3 uses this header for a message integrity check to ensure that the encryption key was transmitted without error.
     */
    @Nullable
    public String sseCustomerKeyMD5;

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

    public void writeHeaders(MutableHttpHeaders headers) {
        if (this.sseCustomerAlgorithm != null) {
            headers.add("x-amz-server-side-encryption-customer-algorithm", this.sseCustomerAlgorithm);
        }
        if (this.sseCustomerKey != null) {
            headers.add("x-amz-server-side-encryption-customer-key", this.sseCustomerKey);
        }
        if (this.sseCustomerKeyMD5 != null) {
            headers.add("x-amz-server-side-encryption-customer-key-md5", this.sseCustomerKeyMD5);
        }
        if (this.requestPayer != null) {
            headers.add("x-amz-request-payer", this.requestPayer);
        }
        if (this.expectedBucketOwner != null) {
            headers.add("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
    }

    public void writeHeadersMap(Map<String, String> headers) {
        if (this.sseCustomerAlgorithm != null) {
            headers.put("x-amz-server-side-encryption-customer-algorithm", this.sseCustomerAlgorithm);
        }
        if (this.sseCustomerKey != null) {
            headers.put("x-amz-server-side-encryption-customer-key", this.sseCustomerKey);
        }
        if (this.sseCustomerKeyMD5 != null) {
            headers.put("x-amz-server-side-encryption-customer-key-md5", this.sseCustomerKeyMD5);
        }
        if (this.requestPayer != null) {
            headers.put("x-amz-request-payer", this.requestPayer);
        }
        if (this.expectedBucketOwner != null) {
            headers.put("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
    }

    public UploadPartArgs setExpectedBucketOwner(@Nullable String expectedBucketOwner) {
        this.expectedBucketOwner = expectedBucketOwner;
        return this;
    }

    public UploadPartArgs setRequestPayer(@Nullable String requestPayer) {
        this.requestPayer = requestPayer;
        return this;
    }

    public UploadPartArgs setSseCustomerAlgorithm(@Nullable String sseCustomerAlgorithm) {
        this.sseCustomerAlgorithm = sseCustomerAlgorithm;
        return this;
    }

    public UploadPartArgs setSseCustomerKey(@Nullable String sseCustomerKey) {
        this.sseCustomerKey = sseCustomerKey;
        return this;
    }

    public UploadPartArgs setSseCustomerKeyMD5(@Nullable String sseCustomerKeyMD5) {
        this.sseCustomerKeyMD5 = sseCustomerKeyMD5;
        return this;
    }
}
