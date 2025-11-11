package ru.tinkoff.kora.aws.s3.model.rq;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjectsV2.html">ListObjectsV2</a>
 */
public class ListObjectsArgs implements Cloneable {
    /**
     * ContinuationToken indicates to Amazon S3 that the list is being continued on this bucket with a token.
     * ContinuationToken is obfuscated and is not a real key. You can use this ContinuationToken for pagination of the list results.
     */
    @Nullable
    public String continuationToken;

    /**
     * A delimiter is a character that you use to group keys.<br />
     * CommonPrefixes is filtered out from results if it is not lexicographically greater than the StartAfter value.
     */
    @Nullable
    public String delimiter;

    /**
     * The owner field is not present in ListObjectsV2 by default. If you want to return the owner field with each key in the result, then set the FetchOwner field to true.
     */
    @Nullable
    public String fetchOwner;

    /**
     * Sets the maximum number of keys returned in the response. By default, the action returns up to 1,000 key names. The response might contain fewer keys but will never contain more.
     */
    @Nullable
    public Integer maxKeys;

    /**
     * Limits the response to keys that begin with the specified prefix.
     */
    @Nullable
    public String prefix;

    /**
     * StartAfter is where you want Amazon S3 to start listing from. Amazon S3 starts listing after this specified key. StartAfter can be any key in the bucket.
     */
    @Nullable
    public String startAfter;

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
     * Specifies the optional fields that you want returned in the response. Fields that you do not specify are not returned.
     */
    @Nullable
    public String optionalObjectAttributes;


    public StringBuilder toQueryString() {
        var sb = new StringBuilder();
        if (this.continuationToken != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("continuation-token=").append(URLEncoder.encode(this.continuationToken, StandardCharsets.UTF_8));
        }
        if (this.delimiter != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("delimiter=").append(URLEncoder.encode(this.delimiter, StandardCharsets.UTF_8));
        }
        if (this.fetchOwner != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("fetch-owner=").append(this.fetchOwner);
        }
        if (this.maxKeys != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("max-keys=").append(this.maxKeys);
        }
        if (this.prefix != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("prefix=").append(URLEncoder.encode(this.prefix, StandardCharsets.UTF_8));
        }
        if (this.startAfter != null) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append("start-after=").append(URLEncoder.encode(this.startAfter, StandardCharsets.UTF_8));
        }
        return sb;
    }

    public SortedMap<String, String> toQueryMap() {
        var map = new TreeMap<String, String>();
        if (this.continuationToken != null) {
            map.put("continuation-token", URLEncoder.encode(this.continuationToken, StandardCharsets.UTF_8));
        }
        if (this.delimiter != null) {
            map.put("delimiter", URLEncoder.encode(this.delimiter, StandardCharsets.UTF_8));
        }
        if (this.fetchOwner != null) {
            map.put("fetch-owner", this.fetchOwner);
        }
        if (this.maxKeys != null) {
            map.put("max-keys", this.maxKeys.toString());
        }
        if (this.prefix != null) {
            map.put("prefix", URLEncoder.encode(this.prefix, StandardCharsets.UTF_8));
        }
        if (this.startAfter != null) {
            map.put("start-after", URLEncoder.encode(this.startAfter, StandardCharsets.UTF_8));
        }
        return map;
    }

    public void writeHeadersMap(Map<String, String> headers) {
        if (this.requestPayer != null) {
            headers.put("x-amz-request-payer", this.requestPayer);
        }
        if (this.expectedBucketOwner != null) {
            headers.put("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
        if (this.optionalObjectAttributes != null) {
            headers.put("x-amz-optional-object-attributes", this.optionalObjectAttributes);
        }
    }

    public void writeHeaders(MutableHttpHeaders headers) {
        if (this.requestPayer != null) {
            headers.add("x-amz-request-payer", this.requestPayer);
        }
        if (this.expectedBucketOwner != null) {
            headers.add("x-amz-expected-bucket-owner", this.expectedBucketOwner);
        }
        if (this.optionalObjectAttributes != null) {
            headers.add("x-amz-optional-object-attributes", this.optionalObjectAttributes);
        }
    }

    public ListObjectsArgs setContinuationToken(@Nullable String continuationToken) {
        this.continuationToken = continuationToken;
        return this;
    }

    public ListObjectsArgs setDelimiter(@Nullable String delimiter) {
        this.delimiter = delimiter;
        return this;
    }

    public ListObjectsArgs setExpectedBucketOwner(@Nullable String expectedBucketOwner) {
        this.expectedBucketOwner = expectedBucketOwner;
        return this;
    }

    public ListObjectsArgs setFetchOwner(@Nullable String fetchOwner) {
        this.fetchOwner = fetchOwner;
        return this;
    }

    public ListObjectsArgs setMaxKeys(@Nullable Integer maxKeys) {
        this.maxKeys = maxKeys;
        return this;
    }

    public ListObjectsArgs setOptionalObjectAttributes(@Nullable String optionalObjectAttributes) {
        this.optionalObjectAttributes = optionalObjectAttributes;
        return this;
    }

    public ListObjectsArgs setPrefix(@Nullable String prefix) {
        this.prefix = prefix;
        return this;
    }

    public ListObjectsArgs setRequestPayer(@Nullable String requestPayer) {
        this.requestPayer = requestPayer;
        return this;
    }

    public ListObjectsArgs setStartAfter(@Nullable String startAfter) {
        this.startAfter = startAfter;
        return this;
    }

    @Override
    public ListObjectsArgs clone() {
        try {
            return (ListObjectsArgs) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
