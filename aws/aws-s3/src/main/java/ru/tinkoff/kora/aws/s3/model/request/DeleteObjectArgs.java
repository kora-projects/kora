package ru.tinkoff.kora.aws.s3.model.request;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @see <a href="DeleteObject">https://docs.aws.amazon.com/AmazonS3/latest/API/API_DeleteObject.html</a>
 */
public class DeleteObjectArgs {
    /**
     * Version ID used to reference a specific version of the object.
     */
    @Nullable
    public String versionId;

    /**
     * The concatenation of the authentication device's serial number, a space, and the value that is displayed on your authentication device.
     * Required to permanently delete a versioned object if versioning is configured with MFA delete enabled.
     */
    @Nullable
    public String mfa;

    /**
     * Confirms that the requester knows that they will be charged for the request. Bucket owners need not specify this parameter in their requests.
     * If either the source or destination S3 bucket has Requester Pays enabled, the requester will pay for corresponding charges to copy the object.
     * For information about downloading objects from Requester Pays buckets, see Downloading Objects in Requester Pays Buckets in the Amazon S3 User Guide.
     */
    @Nullable
    public String requestPayer;

    /**
     * Indicates whether S3 Object Lock should bypass Governance-mode restrictions to process this operation.
     * To use this header, you must have the s3:BypassGovernanceRetention permission.
     */
    @Nullable
    public String bypassGovernanceRetention;

    /**
     * The account ID of the expected bucket owner.
     * If the account ID that you provide does not match the actual owner of the bucket, the request fails with the HTTP status code 403 Forbidden (access denied).
     */
    @Nullable
    public String expectedBucketOwner;

    /**
     * Deletes the object if the ETag (entity tag) value provided during the delete operation matches the ETag of the object in S3.
     * If the ETag values do not match, the operation returns a 412 Precondition Failed error.<br />
     * Expects the ETag value as a string. If-Match does accept a string value of an '*' (asterisk) character to denote a match of any ETag.<br />
     * For more information about conditional requests, see <a href="https://datatracker.ietf.org/doc/html/rfc7232">RFC 7232</a>
     */
    @Nullable
    public String ifMatch;

    /**
     * If present, the object is deleted only if its modification times matches the provided Timestamp.
     * If the Timestamp values do not match, the operation returns a 412 Precondition Failed error.
     * If the Timestamp matches or if the object doesn’t exist, the operation returns a 204 Success (No Content) response.
     */
    @Nullable
    public String ifMatchLastModifiedTime;

    /**
     * If present, the object is deleted only if its size matches the provided size in bytes.
     * If the Size value does not match, the operation returns a 412 Precondition Failed error.
     * If the Size matches or if the object doesn’t exist, the operation returns a 204 Success (No Content) response.
     */
    @Nullable
    public String ifMatchSize;


    public void writeHeaders(MutableHttpHeaders headers) {
        if (this.mfa != null) {
            headers.add("x-amz-mfa", this.mfa);
        }
        if (this.requestPayer != null) {
            headers.add("x-amz-request-payer", this.requestPayer);
        }
        if (this.bypassGovernanceRetention != null) {
            headers.add("x-amz-bypass-governance-retention", this.requestPayer);
        }
        if (this.expectedBucketOwner != null) {
            headers.add("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
        if (this.ifMatch != null) {
            headers.add("if-match", this.ifMatch);
        }
        if (this.ifMatchLastModifiedTime != null) {
            headers.add("x-amz-if-match-last-modified-time", this.ifMatchLastModifiedTime);
        }
        if (this.ifMatchSize != null) {
            headers.add("x-amz-if-match-size", this.ifMatchSize);
        }
    }

    public void writeHeadersMap(Map<String, String> headers) {
        if (this.mfa != null) {
            headers.put("x-amz-mfa", this.mfa);
        }
        if (this.requestPayer != null) {
            headers.put("x-amz-request-payer", this.requestPayer);
        }
        if (this.bypassGovernanceRetention != null) {
            headers.put("x-amz-bypass-governance-retention", this.requestPayer);
        }
        if (this.expectedBucketOwner != null) {
            headers.put("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
        if (this.ifMatch != null) {
            headers.put("if-match", this.ifMatch);
        }
        if (this.ifMatchLastModifiedTime != null) {
            headers.put("x-amz-if-match-last-modified-time", this.ifMatchLastModifiedTime);
        }
        if (this.ifMatchSize != null) {
            headers.put("x-amz-if-match-size", this.ifMatchSize);
        }
    }

    public CharSequence toQueryString() {
        if (this.versionId != null) {
            return "versionId=" + this.versionId;
        }
        return "";
    }

    public SortedMap<String, String> toQueryMap() {
        var map = new TreeMap<String, String>();
        if (this.versionId != null) {
            map.put("versionId", this.versionId);
        }
        return map;
    }

    public DeleteObjectArgs setBypassGovernanceRetention(@Nullable String bypassGovernanceRetention) {
        this.bypassGovernanceRetention = bypassGovernanceRetention;
        return this;
    }

    public DeleteObjectArgs setExpectedBucketOwner(@Nullable String expectedBucketOwner) {
        this.expectedBucketOwner = expectedBucketOwner;
        return this;
    }

    public DeleteObjectArgs setIfMatch(@Nullable String ifMatch) {
        this.ifMatch = ifMatch;
        return this;
    }

    public DeleteObjectArgs setIfMatchLastModifiedTime(@Nullable String ifMatchLastModifiedTime) {
        this.ifMatchLastModifiedTime = ifMatchLastModifiedTime;
        return this;
    }

    public DeleteObjectArgs setIfMatchSize(@Nullable String ifMatchSize) {
        this.ifMatchSize = ifMatchSize;
        return this;
    }

    public DeleteObjectArgs setMfa(@Nullable String mfa) {
        this.mfa = mfa;
        return this;
    }

    public DeleteObjectArgs setRequestPayer(@Nullable String requestPayer) {
        this.requestPayer = requestPayer;
        return this;
    }

    public DeleteObjectArgs setVersionId(@Nullable String versionId) {
        this.versionId = versionId;
        return this;
    }
}
