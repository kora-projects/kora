package ru.tinkoff.kora.aws.s3.model.request;


import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @see <a href="ListMultipartUploads">https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListMultipartUploads.html</a>
 */
public class ListMultipartUploadsArgs {

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
     * Character you use to group keys.<br />
     * All keys that contain the same string between the prefix, if specified, and the first occurrence of the delimiter after the prefix are grouped under a single result element, CommonPrefixes.
     * If you don't specify the prefix parameter, then the substring starts at the beginning of the key.
     * The keys that are grouped under CommonPrefixes result element are not returned elsewhere in the response.<br/>
     * CommonPrefixes is filtered out from results if it is not lexicographically greater than the key-marker.
     */
    @Nullable
    public String delimiter;

    /**
     * Specifies the multipart upload after which listing should begin.
     */
    @Nullable
    public String keyMarker;

    /**
     * Sets the maximum number of multipart uploads, from 1 to 1,000, to return in the response body. 1,000 is the maximum number of uploads that can be returned in a response.
     */
    @Nullable
    public Integer maxUploads;

    /**
     * Lists in-progress uploads only for those keys that begin with the specified prefix. You can use prefixes to separate a bucket into different grouping of keys.
     * (You can think of using prefix to make groups in the same way that you'd use a folder in a file system.)
     */
    @Nullable
    public String prefix;

    /**
     * Together with key-marker, specifies the multipart upload after which listing should begin.
     * If key-marker is not specified, the upload-id-marker parameter is ignored.
     * Otherwise, any multipart uploads for a key equal to the key-marker might be included in the list only if they have an upload ID lexicographically greater than the specified upload-id-marker.
     */
    @Nullable
    public String uploadIdMarker;


    public void writeHeaders(MutableHttpHeaders headers) {
        if (this.requestPayer != null) {
            headers.add("x-amz-request-payer", this.requestPayer);
        }
        if (this.expectedBucketOwner != null) {
            headers.add("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
    }

    public void writeHeadersMap(Map<String, String> headers) {
        if (this.requestPayer != null) {
            headers.put("x-amz-request-payer", this.requestPayer);
        }
        if (this.expectedBucketOwner != null) {
            headers.put("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
    }

    public StringBuilder toQueryString() {
        var sb = new StringBuilder();
        if (this.delimiter != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("delimiter=").append(this.delimiter);
        }
        if (this.keyMarker != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("key-marker").append(this.keyMarker);
        }
        if (this.maxUploads != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("max-uploads").append(this.maxUploads);
        }
        if (this.prefix != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("prefix").append(this.prefix);
        }
        if (this.uploadIdMarker != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("upload-id-marker").append(this.uploadIdMarker);
        }
        return sb;
    }

    public SortedMap<String, String> toQueryMap() {
        var map = new TreeMap<String, String>();
        if (this.delimiter != null) {
            map.put("delimiter", this.delimiter);
        }
        if (this.keyMarker != null) {
            map.put("key-marker", this.keyMarker);
        }
        if (this.maxUploads != null) {
            map.put("max-uploads", String.valueOf(this.maxUploads));
        }
        if (this.prefix != null) {
            map.put("prefix", this.prefix);
        }
        if (this.uploadIdMarker != null) {
            map.put("upload-id-marker", this.uploadIdMarker);
        }
        return map;
    }

    public ListMultipartUploadsArgs setDelimiter(@Nullable String delimiter) {
        this.delimiter = delimiter;
        return this;
    }

    public ListMultipartUploadsArgs setExpectedBucketOwner(@Nullable String expectedBucketOwner) {
        this.expectedBucketOwner = expectedBucketOwner;
        return this;
    }

    public ListMultipartUploadsArgs setKeyMarker(@Nullable String keyMarker) {
        this.keyMarker = keyMarker;
        return this;
    }

    public ListMultipartUploadsArgs setMaxUploads(@Nullable Integer maxUploads) {
        this.maxUploads = maxUploads;
        return this;
    }

    public ListMultipartUploadsArgs setPrefix(@Nullable String prefix) {
        this.prefix = prefix;
        return this;
    }

    public ListMultipartUploadsArgs setRequestPayer(@Nullable String requestPayer) {
        this.requestPayer = requestPayer;
        return this;
    }

    public ListMultipartUploadsArgs setUploadIdMarker(@Nullable String uploadIdMarker) {
        this.uploadIdMarker = uploadIdMarker;
        return this;
    }
}
