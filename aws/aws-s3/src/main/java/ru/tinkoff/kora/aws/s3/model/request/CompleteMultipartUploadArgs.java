package ru.tinkoff.kora.aws.s3.model.request;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;

import java.util.Map;

/**
 * @see <a href="CompleteMultipartUpload">https://docs.aws.amazon.com/AmazonS3/latest/API/API_CompleteMultipartUpload.html</a>
 */
public class CompleteMultipartUploadArgs {
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
     * Uploads the object only if the ETag (entity tag) value provided during the WRITE operation matches the ETag of the object in S3.
     * If the ETag values do not match, the operation returns a 412 Precondition Failed error.<br />
     * If a conflicting operation occurs during the upload S3 returns a 409 ConditionalRequestConflict response.
     * On a 409 failure you should fetch the object's ETag, re-initiate the multipart upload with CreateMultipartUpload, and re-upload each part.<br />
     * Expects the ETag value as a string.<br />
     * For more information about conditional requests, see <a href="https://datatracker.ietf.org/doc/html/rfc7232">RFC 7232</a>
     * , or Conditional requests in the Amazon S3 User Guide.
     */
    @Nullable
    public String ifMatch;

    /**
     * The server-side encryption (SSE) algorithm used to encrypt the object.
     * This parameter is required only when the object was created using a checksum algorithm or if your bucket policy requires the use of SSE-C.
     * For more information, see Protecting data using SSE-C keys in the Amazon S3 User Guide.
     */
    @Nullable
    public String sseCustomerAlgorithm;

    /**
     * The server-side encryption (SSE) customer managed key.
     * This parameter is needed only when the object was created using a checksum algorithm.
     * For more information, see Protecting data using SSE-C keys in the Amazon S3 User Guide.
     *
     */
    @Nullable
    public String sseCustomerKey;

    /**
     * The MD5 server-side encryption (SSE) customer managed key.
     * This parameter is needed only when the object was created using a checksum algorithm.
     * For more information, see Protecting data using SSE-C keys in the Amazon S3 User Guide.
     */
    @Nullable
    public String sseCustomerKeyMD5;


    public void writeHeaders(MutableHttpHeaders headers) {
        if (this.requestPayer != null) {
            headers.add("x-amz-request-payer", this.requestPayer);
        }
        if (this.expectedBucketOwner != null) {
            headers.add("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
        if (this.ifMatch != null) {
            headers.add("if-match", this.ifMatch);
        }
        if (this.sseCustomerAlgorithm != null) {
            headers.add("x-amz-server-side-encryption-customer-algorithm", this.sseCustomerAlgorithm);
        }
        if (this.sseCustomerKey != null) {
            headers.add("x-amz-server-side-encryption-customer-key", this.sseCustomerKey);
        }
        if (this.sseCustomerKeyMD5 != null) {
            headers.add("x-amz-server-side-encryption-customer-key-md5", this.sseCustomerKeyMD5);
        }
    }

    public void writeHeadersMap(Map<String, String> headers) {
        if (this.requestPayer != null) {
            headers.put("x-amz-request-payer", this.requestPayer);
        }
        if (this.expectedBucketOwner != null) {
            headers.put("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
        if (this.ifMatch != null) {
            headers.put("if-match", this.ifMatch);
        }
        if (this.sseCustomerAlgorithm != null) {
            headers.put("x-amz-server-side-encryption-customer-algorithm", this.sseCustomerAlgorithm);
        }
        if (this.sseCustomerKey != null) {
            headers.put("x-amz-server-side-encryption-customer-key", this.sseCustomerKey);
        }
        if (this.sseCustomerKeyMD5 != null) {
            headers.put("x-amz-server-side-encryption-customer-key-md5", this.sseCustomerKeyMD5);
        }
    }

    public CompleteMultipartUploadArgs setExpectedBucketOwner(@Nullable String expectedBucketOwner) {
        this.expectedBucketOwner = expectedBucketOwner;
        return this;
    }

    public CompleteMultipartUploadArgs setIfMatch(@Nullable String ifMatch) {
        this.ifMatch = ifMatch;
        return this;
    }

    public CompleteMultipartUploadArgs setRequestPayer(@Nullable String requestPayer) {
        this.requestPayer = requestPayer;
        return this;
    }

    public CompleteMultipartUploadArgs setSseCustomerAlgorithm(@Nullable String sseCustomerAlgorithm) {
        this.sseCustomerAlgorithm = sseCustomerAlgorithm;
        return this;
    }

    public CompleteMultipartUploadArgs setSseCustomerKey(@Nullable String sseCustomerKey) {
        this.sseCustomerKey = sseCustomerKey;
        return this;
    }

    public CompleteMultipartUploadArgs setSseCustomerKeyMD5(@Nullable String sseCustomerKeyMD5) {
        this.sseCustomerKeyMD5 = sseCustomerKeyMD5;
        return this;
    }
}
