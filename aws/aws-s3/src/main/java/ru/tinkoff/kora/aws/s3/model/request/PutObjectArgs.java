package ru.tinkoff.kora.aws.s3.model.request;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;

import java.util.Map;

/**
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_PutObject.html">PutObject</a>
 */
public class PutObjectArgs {
    /**
     * The canned ACL to apply to the object. Amazon S3 supports a set of predefined ACLs, known as canned ACLs.
     * Each canned ACL has a predefined set of grantees and permissions. For more information, see Canned ACL in the Amazon S3 User Guide.
     */
    @Nullable
    public String acl;

    /**
     * Specifies caching behavior along the request/reply chain.
     */
    @Nullable
    public String cacheControl;

    /**
     * Specifies presentational information for the object.
     */
    @Nullable
    public String contentDisposition;

    /**
     * Specifies what content encodings have been applied to the object and thus what decoding mechanisms must be applied to obtain the media-type referenced by the Content-Type header field.
     */
    @Nullable
    public String contentEncoding;

    /**
     * The language that the content is in.
     */
    @Nullable
    public String contentLanguage;

    /**
     * A standard MIME type describing the format of the object data.
     */
    @Nullable
    public String contentType;


    /**
     * The date and time at which the object is no longer cacheable.
     */
    @Nullable
    public String expires;

    /**
     * Uploads the object only if the ETag (entity tag) value provided during the WRITE operation matches the ETag of the object in S3.
     * If the ETag values do not match, the operation returns a 412 Precondition Failed error.<br />
     * If a conflicting operation occurs during the upload S3 returns a 409 ConditionalRequestConflict response. On a 409 failure you should fetch the object's ETag and retry the upload.<br />
     * Expects the ETag value as a string.
     */
    @Nullable
    public String ifMatch;

    /**
     * Uploads the object only if the object key name does not already exist in the bucket specified. Otherwise, Amazon S3 returns a 412 Precondition Failed error.<br />
     * If a conflicting operation occurs during the upload S3 returns a 409 ConditionalRequestConflict response. On a 409 failure you should retry the upload.<br />
     * Expects the '*' (asterisk) character.
     */
    @Nullable
    public String ifNoneMatch;

    /**
     * Specify access permissions explicitly to give the grantee READ, READ_ACP, and WRITE_ACP permissions on the object.
     */
    @Nullable
    public String grantFullControl;

    /**
     * Specify access permissions explicitly to allow grantee to read the object data and its metadata.
     */
    @Nullable
    public String grantRead;

    /**
     * Specify access permissions explicitly to allows grantee to read the object ACL.
     */
    @Nullable
    public String grantReadAcp;

    /**
     * Specify access permissions explicitly to allows grantee to allow grantee to write the ACL for the applicable object.
     */
    @Nullable
    public String grantWriteAcp;

    /**
     * The server-side encryption algorithm used when you store this object in Amazon S3 or Amazon FSx.
     */
    @Nullable
    public String serverSideEncryption;

    /**
     * By default, Amazon S3 uses the STANDARD Storage Class to store newly created objects. The STANDARD storage class provides high durability and high availability.
     * Depending on performance needs, you can specify a different Storage Class. For more information, see Storage Classes in the Amazon S3 User Guide.
     */
    @Nullable
    public String storageClass;

    /**
     * If the bucket is configured as a website, redirects requests for this object to another object in the same bucket or to an external URL.
     * Amazon S3 stores the value of this header in the object metadata.
     */
    @Nullable
    public String websiteRedirectLocation;

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
     * Specifies the AWS KMS key ID (Key ID, Key ARN, or Key Alias) to use for object encryption.
     * If the KMS key doesn't exist in the same account that's issuing the command, you must use the full Key ARN not the Key ID.
     */
    @Nullable
    public String sseKmsKeyId;

    /**
     * Specifies the AWS KMS Encryption Context to use for object encryption.
     * The value of this header is a Base64 encoded string of a UTF-8 encoded JSON, which contains the encryption context as key-value pairs.
     */
    @Nullable
    public String sseKmsEncryptionContext;

    /**
     * Specifies whether Amazon S3 should use an S3 Bucket Key for object encryption with server-side encryption using AWS Key Management Service (AWS KMS) keys (SSE-KMS).
     */
    @Nullable
    public String bucketKeyEnabled;

    /**
     * Confirms that the requester knows that they will be charged for the request. Bucket owners need not specify this parameter in their requests.
     * If either the source or destination S3 bucket has Requester Pays enabled, the requester will pay for corresponding charges to copy the object.
     * For information about downloading objects from Requester Pays buckets, see Downloading Objects in Requester Pays Buckets in the Amazon S3 User Guide.
     */
    @Nullable
    public String requestPayer;

    /**
     * The tag-set for the object. The tag-set must be encoded as URL Query parameters.
     */
    @Nullable
    public String tagging;

    /**
     * Specifies the Object Lock mode that you want to apply to the uploaded object.
     */
    @Nullable
    public String objectLockMode;

    /**
     * Specifies the date and time when you want the Object Lock to expire.
     */
    @Nullable
    public String objectLockRetainUntilDate;

    /**
     * Specifies whether you want to apply a legal hold to the uploaded object.
     */
    @Nullable
    public String objectLockLegalHoldStatus;

    /**
     * The account ID of the expected bucket owner.
     * If the account ID that you provide does not match the actual owner of the bucket, the request fails with the HTTP status code 403 Forbidden (access denied).
     */
    @Nullable
    public String expectedBucketOwner;


    public void writeHeadersMap(Map<String, String> headers) {
        if (this.acl != null) {
            headers.put("x-amz-acl", this.acl);
        }
        if (this.cacheControl != null) {
            headers.put("cache-control", this.cacheControl);
        }
        if (this.contentDisposition != null) {
            headers.put("content-disposition", this.contentDisposition);
        }
        if (this.contentEncoding != null) {
            headers.put("content-encoding", this.contentEncoding);
        }
        if (this.contentLanguage != null) {
            headers.put("content-language", this.contentLanguage);
        }
        if (this.contentType != null) {
            headers.put("content-type", this.contentType);
        }
        if (this.expires != null) {
            headers.put("expires", this.expires);
        }
        if (this.ifMatch != null) {
            headers.put("if-match", this.ifMatch);
        }
        if (this.ifNoneMatch != null) {
            headers.put("if-none-match", this.ifNoneMatch);
        }
        if (this.grantFullControl != null) {
            headers.put("x-amz-grant-full-control", this.grantFullControl);
        }
        if (this.grantRead != null) {
            headers.put("x-amz-grant-read", this.grantRead);
        }
        if (this.grantReadAcp != null) {
            headers.put("x-amz-grant-read-acp", this.grantReadAcp);
        }
        if (this.grantWriteAcp != null) {
            headers.put("x-amz-grant-write-acp", this.grantWriteAcp);
        }
        if (this.serverSideEncryption != null) {
            headers.put("x-amz-server-side-encryption", this.serverSideEncryption);
        }
        if (this.storageClass != null) {
            headers.put("x-amz-storage-class", this.storageClass);
        }
        if (this.websiteRedirectLocation != null) {
            headers.put("x-amz-website-redirect-location", this.websiteRedirectLocation);
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
        if (this.sseKmsKeyId != null) {
            headers.put("x-amz-server-side-encryption-aws-kms-key-id", this.sseKmsKeyId);
        }
        if (this.sseKmsEncryptionContext != null) {
            headers.put("x-amz-server-side-encryption-context", this.sseKmsEncryptionContext);
        }
        if (this.bucketKeyEnabled != null) {
            headers.put("x-amz-server-side-encryption-bucket-key-enabled", this.bucketKeyEnabled);
        }
        if (this.requestPayer != null) {
            headers.put("x-amz-request-payer", this.requestPayer);
        }
        if (this.tagging != null) {
            headers.put("x-amz-tagging", this.tagging);
        }
        if (this.objectLockMode != null) {
            headers.put("x-amz-object-lock-mode", this.objectLockMode);
        }
        if (this.objectLockRetainUntilDate != null) {
            headers.put("x-amz-object-lock-retain-until-date", this.objectLockRetainUntilDate);
        }
        if (this.objectLockLegalHoldStatus != null) {
            headers.put("x-amz-object-lock-legal-hold", this.objectLockLegalHoldStatus);

        }
        if (this.expectedBucketOwner != null) {
            headers.put("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
    }

    public void writeHeaders(MutableHttpHeaders headers) {
        if (this.acl != null) {
            headers.add("x-amz-acl", this.acl);
        }
        if (this.cacheControl != null) {
            headers.add("cache-control", this.cacheControl);
        }
        if (this.contentDisposition != null) {
            headers.add("content-disposition", this.contentDisposition);
        }
        if (this.contentEncoding != null) {
            headers.add("content-encoding", this.contentEncoding);
        }
        if (this.contentLanguage != null) {
            headers.add("content-language", this.contentLanguage);
        }
        if (this.contentType != null) {
            headers.add("content-type", this.contentType);
        }
        if (this.expires != null) {
            headers.add("expires", this.expires);
        }
        if (this.ifMatch != null) {
            headers.add("if-match", this.ifMatch);
        }
        if (this.ifNoneMatch != null) {
            headers.add("if-none-match", this.ifNoneMatch);
        }
        if (this.grantFullControl != null) {
            headers.add("x-amz-grant-full-control", this.grantFullControl);
        }
        if (this.grantRead != null) {
            headers.add("x-amz-grant-read", this.grantRead);
        }
        if (this.grantReadAcp != null) {
            headers.add("x-amz-grant-read-acp", this.grantReadAcp);
        }
        if (this.grantWriteAcp != null) {
            headers.add("x-amz-grant-write-acp", this.grantWriteAcp);
        }
        if (this.serverSideEncryption != null) {
            headers.add("x-amz-server-side-encryption", this.serverSideEncryption);
        }
        if (this.storageClass != null) {
            headers.add("x-amz-storage-class", this.storageClass);
        }
        if (this.websiteRedirectLocation != null) {
            headers.add("x-amz-website-redirect-location", this.websiteRedirectLocation);
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
        if (this.sseKmsKeyId != null) {
            headers.add("x-amz-server-side-encryption-aws-kms-key-id", this.sseKmsKeyId);
        }
        if (this.sseKmsEncryptionContext != null) {
            headers.add("x-amz-server-side-encryption-context", this.sseKmsEncryptionContext);
        }
        if (this.bucketKeyEnabled != null) {
            headers.add("x-amz-server-side-encryption-bucket-key-enabled", this.bucketKeyEnabled);
        }
        if (this.requestPayer != null) {
            headers.add("x-amz-request-payer", this.requestPayer);
        }
        if (this.tagging != null) {
            headers.add("x-amz-tagging", this.tagging);
        }
        if (this.objectLockMode != null) {
            headers.add("x-amz-object-lock-mode", this.objectLockMode);
        }
        if (this.objectLockRetainUntilDate != null) {
            headers.add("x-amz-object-lock-retain-until-date", this.objectLockRetainUntilDate);
        }
        if (this.objectLockLegalHoldStatus != null) {
            headers.add("x-amz-object-lock-legal-hold", this.objectLockLegalHoldStatus);
        }
        if (this.expectedBucketOwner != null) {
            headers.add("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
    }
    /*
x-amz-acl: ACL
Cache-Control: CacheControl
Content-Disposition: ContentDisposition
Content-Encoding: ContentEncoding
Content-Language: ContentLanguage
Content-Length: ContentLength
Content-MD5: ContentMD5
Content-Type: ContentType

x-amz-sdk-checksum-algorithm: ChecksumAlgorithm
x-amz-checksum-crc32: ChecksumCRC32
x-amz-checksum-crc32c: ChecksumCRC32C
x-amz-checksum-crc64nvme: ChecksumCRC64NVME
x-amz-checksum-sha1: ChecksumSHA1
x-amz-checksum-sha256: ChecksumSHA256

Expires: Expires

If-Match: IfMatch
If-None-Match: IfNoneMatch

x-amz-grant-full-control: GrantFullControl
x-amz-grant-read: GrantRead
x-amz-grant-read-acp: GrantReadACP
x-amz-grant-write-acp: GrantWriteACP

x-amz-write-offset-bytes: WriteOffsetBytes

x-amz-server-side-encryption: ServerSideEncryption
x-amz-storage-class: StorageClass
x-amz-website-redirect-location: WebsiteRedirectLocation
x-amz-server-side-encryption-customer-algorithm: SSECustomerAlgorithm
x-amz-server-side-encryption-customer-key: SSECustomerKey
x-amz-server-side-encryption-customer-key-MD5: SSECustomerKeyMD5
x-amz-server-side-encryption-aws-kms-key-id: SSEKMSKeyId
x-amz-server-side-encryption-context: SSEKMSEncryptionContext
x-amz-server-side-encryption-bucket-key-enabled: BucketKeyEnabled
x-amz-request-payer: RequestPayer
x-amz-tagging: Tagging
x-amz-object-lock-mode: ObjectLockMode
x-amz-object-lock-retain-until-date: ObjectLockRetainUntilDate
x-amz-object-lock-legal-hold: ObjectLockLegalHoldStatus
x-amz-expected-bucket-owner: ExpectedBucketOwner

*/
}
