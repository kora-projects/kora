package ru.tinkoff.kora.aws.s3.model.request;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;

import java.util.Map;

/**
 * @see <a href="CreateMultipartUpload">https://docs.aws.amazon.com/AmazonS3/latest/API/API_CreateMultipartUpload.html</a>
 */
public class CreateMultipartUploadArgs {
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

    public CreateMultipartUploadArgs setAcl(@Nullable String acl) {
        this.acl = acl;
        return this;
    }

    public CreateMultipartUploadArgs setBucketKeyEnabled(@Nullable String bucketKeyEnabled) {
        this.bucketKeyEnabled = bucketKeyEnabled;
        return this;
    }

    public CreateMultipartUploadArgs setCacheControl(@Nullable String cacheControl) {
        this.cacheControl = cacheControl;
        return this;
    }

    public CreateMultipartUploadArgs setContentDisposition(@Nullable String contentDisposition) {
        this.contentDisposition = contentDisposition;
        return this;
    }

    public CreateMultipartUploadArgs setContentEncoding(@Nullable String contentEncoding) {
        this.contentEncoding = contentEncoding;
        return this;
    }

    public CreateMultipartUploadArgs setContentLanguage(@Nullable String contentLanguage) {
        this.contentLanguage = contentLanguage;
        return this;
    }

    public CreateMultipartUploadArgs setContentType(@Nullable String contentType) {
        this.contentType = contentType;
        return this;
    }

    public CreateMultipartUploadArgs setExpectedBucketOwner(@Nullable String expectedBucketOwner) {
        this.expectedBucketOwner = expectedBucketOwner;
        return this;
    }

    public CreateMultipartUploadArgs setExpires(@Nullable String expires) {
        this.expires = expires;
        return this;
    }

    public CreateMultipartUploadArgs setGrantFullControl(@Nullable String grantFullControl) {
        this.grantFullControl = grantFullControl;
        return this;
    }

    public CreateMultipartUploadArgs setGrantRead(@Nullable String grantRead) {
        this.grantRead = grantRead;
        return this;
    }

    public CreateMultipartUploadArgs setGrantReadAcp(@Nullable String grantReadAcp) {
        this.grantReadAcp = grantReadAcp;
        return this;
    }

    public CreateMultipartUploadArgs setGrantWriteAcp(@Nullable String grantWriteAcp) {
        this.grantWriteAcp = grantWriteAcp;
        return this;
    }

    public CreateMultipartUploadArgs setObjectLockLegalHoldStatus(@Nullable String objectLockLegalHoldStatus) {
        this.objectLockLegalHoldStatus = objectLockLegalHoldStatus;
        return this;
    }

    public CreateMultipartUploadArgs setObjectLockMode(@Nullable String objectLockMode) {
        this.objectLockMode = objectLockMode;
        return this;
    }

    public CreateMultipartUploadArgs setObjectLockRetainUntilDate(@Nullable String objectLockRetainUntilDate) {
        this.objectLockRetainUntilDate = objectLockRetainUntilDate;
        return this;
    }

    public CreateMultipartUploadArgs setRequestPayer(@Nullable String requestPayer) {
        this.requestPayer = requestPayer;
        return this;
    }

    public CreateMultipartUploadArgs setServerSideEncryption(@Nullable String serverSideEncryption) {
        this.serverSideEncryption = serverSideEncryption;
        return this;
    }

    public CreateMultipartUploadArgs setSseCustomerAlgorithm(@Nullable String sseCustomerAlgorithm) {
        this.sseCustomerAlgorithm = sseCustomerAlgorithm;
        return this;
    }

    public CreateMultipartUploadArgs setSseCustomerKey(@Nullable String sseCustomerKey) {
        this.sseCustomerKey = sseCustomerKey;
        return this;
    }

    public CreateMultipartUploadArgs setSseCustomerKeyMD5(@Nullable String sseCustomerKeyMD5) {
        this.sseCustomerKeyMD5 = sseCustomerKeyMD5;
        return this;
    }

    public CreateMultipartUploadArgs setSseKmsEncryptionContext(@Nullable String sseKmsEncryptionContext) {
        this.sseKmsEncryptionContext = sseKmsEncryptionContext;
        return this;
    }

    public CreateMultipartUploadArgs setSseKmsKeyId(@Nullable String sseKmsKeyId) {
        this.sseKmsKeyId = sseKmsKeyId;
        return this;
    }

    public CreateMultipartUploadArgs setStorageClass(@Nullable String storageClass) {
        this.storageClass = storageClass;
        return this;
    }

    public CreateMultipartUploadArgs setTagging(@Nullable String tagging) {
        this.tagging = tagging;
        return this;
    }

    public CreateMultipartUploadArgs setWebsiteRedirectLocation(@Nullable String websiteRedirectLocation) {
        this.websiteRedirectLocation = websiteRedirectLocation;
        return this;
    }
}
