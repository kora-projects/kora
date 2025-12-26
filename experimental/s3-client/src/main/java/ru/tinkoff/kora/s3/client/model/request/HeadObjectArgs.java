package ru.tinkoff.kora.s3.client.model.request;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;
import ru.tinkoff.kora.s3.client.model.Range;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @see <a href="HeadObject">https://docs.aws.amazon.com/AmazonS3/latest/API/API_HeadObject.html</a>
 */
public class HeadObjectArgs {
    /**
     * Part number of the object being read.
     * This is a positive integer between 1 and 10,000.
     * Effectively performs a 'ranged' HEAD request for the part specified.
     * Useful querying about the size of the part and the number of parts in this object.
     */
    @Nullable
    public Integer partNumber;

    /**
     * Sets the Cache-Control header of the response.
     */
    @Nullable
    public String responseCacheControl;

    /**
     * Sets the Content-Disposition header of the response.
     */
    @Nullable
    public String responseContentDisposition;

    /**
     * Sets the Content-Encoding header of the response.
     */
    @Nullable
    public String responseContentEncoding;

    /**
     * Sets the Content-Language header of the response.
     */
    @Nullable
    public String responseContentLanguage;

    /**
     * Sets the Content-Type header of the response.
     */
    @Nullable
    public String responseContentType;

    /**
     * Sets the Expires header of the response.
     */
    @Nullable
    public String responseExpires;

    /**
     * Version ID used to reference a specific version of the object.
     */
    @Nullable
    public String versionId;

    /**
     * Return the object only if its entity tag (ETag) is the same as the one specified in this header; otherwise, return a 412 Precondition Failed error.<br />
     * If both of the If-Match and If-Unmodified-Since headers are present in the request as follows: If-Match condition evaluates to true, and; If-Unmodified-Since condition evaluates to false;
     * then, S3 returns 200 OK and the data requested.<br />
     * For more information about conditional requests, see <a href="https://datatracker.ietf.org/doc/html/rfc7232">RFC 7232</a>
     */
    @Nullable
    public String ifMatch;

    /**
     * Return the object only if it has been modified since the specified time; otherwise, return a 304 Not Modified error. <br />
     * If both of the If-None-Match and If-Modified-Since headers are present in the request as follows: If-None-Match condition evaluates to false, and; If-Modified-Since condition evaluates to true;
     * then, S3 returns 304 Not Modified status code.<br />
     * For more information about conditional requests, see <a href="https://datatracker.ietf.org/doc/html/rfc7232">RFC 7232</a>
     */
    @Nullable
    public String ifModifiedSince;

    /**
     * Return the object only if its entity tag (ETag) is different from the one specified in this header; otherwise, return a 304 Not Modified error.<br />
     * If both of the If-None-Match and If-Modified-Since headers are present in the request as follows: If-None-Match condition evaluates to false, and; If-Modified-Since condition evaluates to true;
     * then, S3 returns 304 Not Modified HTTP status code.<br />
     * For more information about conditional requests, see <a href="https://datatracker.ietf.org/doc/html/rfc7232">RFC 7232</a>
     */
    @Nullable
    public String ifNoneMatch;

    /**
     * Return the object only if it has not been modified since the specified time; otherwise, return a 412 Precondition Failed error.<br />
     * If both of the If-Match and If-Unmodified-Since headers are present in the request as follows: If-Match condition evaluates to true, and; If-Unmodified-Since condition evaluates to false;
     * then, S3 returns 200 OK and the data requested.<br />
     * For more information about conditional requests, see <a href="https://datatracker.ietf.org/doc/html/rfc7232">RFC 7232</a>
     */
    @Nullable
    public String ifUnmodifiedSince;

    /**
     * HeadObject returns only the metadata for an object. If the Range is satisfiable, only the ContentLength is affected in the response.
     * If the Range is not satisfiable, S3 returns a 416 - Requested Range Not Satisfiable error.
     */
    @Nullable
    public Range range;

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

    /**
     * To retrieve the checksum, this parameter must be enabled.
     */
    @Nullable
    public String checksumMode;

    public void writeHeaders(MutableHttpHeaders headers) {
        if (this.ifMatch != null) {
            headers.add("if-match", this.ifMatch);
        }
        if (this.ifModifiedSince != null) {
            headers.add("if-modified-since", this.ifModifiedSince);
        }
        if (this.ifNoneMatch != null) {
            headers.add("if-none-match", this.ifNoneMatch);
        }
        if (this.ifUnmodifiedSince != null) {
            headers.add("if-unmodified-since", this.ifUnmodifiedSince);
        }
        if (this.range != null) {
            headers.add("range", this.range.toRangeValue());
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
        if (this.requestPayer != null) {
            headers.add("x-amz-request-payer", this.requestPayer);
        }
        if (this.expectedBucketOwner != null) {
            headers.add("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
        if (this.checksumMode != null) {
            headers.add("x-amz-checksum-mode", this.checksumMode);
        }
    }

    public void writeHeadersMap(Map<String, String> headers) {
        if (this.ifMatch != null) {
            headers.put("if-match", this.ifMatch);
        }
        if (this.ifModifiedSince != null) {
            headers.put("if-modified-since", this.ifModifiedSince);
        }
        if (this.ifNoneMatch != null) {
            headers.put("if-none-match", this.ifNoneMatch);
        }
        if (this.ifUnmodifiedSince != null) {
            headers.put("if-unmodified-since", this.ifUnmodifiedSince);
        }
        if (this.range != null) {
            headers.put("range", this.range.toRangeValue());
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
        if (this.requestPayer != null) {
            headers.put("x-amz-request-payer", this.requestPayer);
        }
        if (this.expectedBucketOwner != null) {
            headers.put("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
        if (this.checksumMode != null) {
            headers.put("x-amz-checksum-mode", this.checksumMode);
        }
    }

    public StringBuilder toQueryString() {
        var sb = new StringBuilder();
        if (this.partNumber != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("partNumber=").append(this.partNumber);
        }
        if (this.responseCacheControl != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("response-cache-control=").append(this.responseCacheControl);
        }
        if (this.responseContentDisposition != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("response-content-disposition=").append(this.responseContentDisposition);
        }
        if (this.responseContentEncoding != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("response-content-encoding=").append(this.responseContentEncoding);
        }
        if (this.responseContentLanguage != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("response-content-language=").append(this.responseContentLanguage);
        }
        if (this.responseContentType != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("response-content-type=").append(this.responseContentType);
        }
        if (this.responseExpires != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("response-expires=").append(this.responseExpires);
        }
        if (this.versionId != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("versionId").append(this.versionId);
        }
        return sb;
    }

    public SortedMap<String, String> toQueryMap() {
        var map = new TreeMap<String, String>();
        if (this.partNumber != null) {
            map.put("partNumber", this.partNumber.toString());
        }
        if (this.responseCacheControl != null) {
            map.put("response-cache-control", this.responseCacheControl);
        }
        if (this.responseContentDisposition != null) {
            map.put("response-content-disposition", this.responseContentDisposition);
        }
        if (this.responseContentEncoding != null) {
            map.put("response-content-encoding", this.responseContentEncoding);
        }
        if (this.responseContentLanguage != null) {
            map.put("response-content-language", this.responseContentLanguage);
        }
        if (this.responseContentType != null) {
            map.put("response-content-type", this.responseContentType);
        }
        if (this.responseExpires != null) {
            map.put("response-expires", this.responseExpires);
        }
        if (this.versionId != null) {
            map.put("versionId", this.versionId);
        }
        return map;
    }


    public HeadObjectArgs setChecksumMode(@Nullable String checksumMode) {
        this.checksumMode = checksumMode;
        return this;
    }

    public HeadObjectArgs setExpectedBucketOwner(@Nullable String expectedBucketOwner) {
        this.expectedBucketOwner = expectedBucketOwner;
        return this;
    }

    public HeadObjectArgs setIfMatch(@Nullable String ifMatch) {
        this.ifMatch = ifMatch;
        return this;
    }

    public HeadObjectArgs setIfModifiedSince(@Nullable String ifModifiedSince) {
        this.ifModifiedSince = ifModifiedSince;
        return this;
    }

    public HeadObjectArgs setIfNoneMatch(@Nullable String ifNoneMatch) {
        this.ifNoneMatch = ifNoneMatch;
        return this;
    }

    public HeadObjectArgs setIfUnmodifiedSince(@Nullable String ifUnmodifiedSince) {
        this.ifUnmodifiedSince = ifUnmodifiedSince;
        return this;
    }

    public HeadObjectArgs setPartNumber(@Nullable Integer partNumber) {
        this.partNumber = partNumber;
        return this;
    }

    public HeadObjectArgs setRange(@Nullable Range range) {
        this.range = range;
        return this;
    }

    public HeadObjectArgs setRequestPayer(@Nullable String requestPayer) {
        this.requestPayer = requestPayer;
        return this;
    }

    public HeadObjectArgs setResponseCacheControl(@Nullable String responseCacheControl) {
        this.responseCacheControl = responseCacheControl;
        return this;
    }

    public HeadObjectArgs setResponseContentDisposition(@Nullable String responseContentDisposition) {
        this.responseContentDisposition = responseContentDisposition;
        return this;
    }

    public HeadObjectArgs setResponseContentEncoding(@Nullable String responseContentEncoding) {
        this.responseContentEncoding = responseContentEncoding;
        return this;
    }

    public HeadObjectArgs setResponseContentLanguage(@Nullable String responseContentLanguage) {
        this.responseContentLanguage = responseContentLanguage;
        return this;
    }

    public HeadObjectArgs setResponseContentType(@Nullable String responseContentType) {
        this.responseContentType = responseContentType;
        return this;
    }

    public HeadObjectArgs setResponseExpires(@Nullable String responseExpires) {
        this.responseExpires = responseExpires;
        return this;
    }

    public HeadObjectArgs setSseCustomerAlgorithm(@Nullable String sseCustomerAlgorithm) {
        this.sseCustomerAlgorithm = sseCustomerAlgorithm;
        return this;
    }

    public HeadObjectArgs setSseCustomerKey(@Nullable String sseCustomerKey) {
        this.sseCustomerKey = sseCustomerKey;
        return this;
    }

    public HeadObjectArgs setSseCustomerKeyMD5(@Nullable String sseCustomerKeyMD5) {
        this.sseCustomerKeyMD5 = sseCustomerKeyMD5;
        return this;
    }

    public HeadObjectArgs setVersionId(@Nullable String versionId) {
        this.versionId = versionId;
        return this;
    }
}
