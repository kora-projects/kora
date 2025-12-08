package ru.tinkoff.kora.aws.s3.impl;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.aws.s3.AwsCredentials;
import ru.tinkoff.kora.aws.s3.S3Client;
import ru.tinkoff.kora.aws.s3.S3ClientConfig;
import ru.tinkoff.kora.aws.s3.exception.*;
import ru.tinkoff.kora.aws.s3.impl.xml.*;
import ru.tinkoff.kora.aws.s3.model.request.*;
import ru.tinkoff.kora.aws.s3.model.response.*;
import ru.tinkoff.kora.aws.s3.model.response.ListBucketResult;
import ru.tinkoff.kora.aws.s3.model.response.ListMultipartUploadsResult;
import ru.tinkoff.kora.aws.s3.model.response.ListMultipartUploadsResult.Upload;
import ru.tinkoff.kora.aws.s3.model.response.ListPartsResult;
import ru.tinkoff.kora.aws.s3.telemetry.S3ClientTelemetry;
import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class S3ClientImpl implements S3Client {
    private final S3ClientTelemetry telemetry;
    private final HttpClient httpClient;
    private final S3ClientConfig config;
    private final UriHelper uriHelper;

    public S3ClientImpl(HttpClient httpClient, S3ClientConfig config, S3ClientTelemetry telemetry) {
        this.httpClient = httpClient;
        this.config = config;
        this.uriHelper = new UriHelper(config);
        this.telemetry = telemetry;
    }

    @Nullable
    @Override
    public HeadObjectResult headObject(AwsCredentials credentials, String bucket, String key, @Nullable HeadObjectArgs args, boolean required) throws S3ClientException {
        var observation = this.telemetry.observe("HeadObject", bucket);
        observation.observeKey(key);
        return Observation.scoped(observation)
            .call(() -> {
                var headers = HttpHeaders.of();
                var headersMap = new HashMap<String, String>();
                var queryString = (CharSequence) "";
                var queryMap = Collections.<String, String>emptySortedMap();
                if (args != null) {
                    queryString = args.toQueryString();
                    queryMap = args.toQueryMap();
                    args.writeHeadersMap(headersMap);
                    args.writeHeaders(headers);
                }
                var uri = this.uriHelper.uri(bucket, key, queryString);
                var signer = credentials instanceof AwsRequestSigner s
                    ? s
                    : new AwsRequestSigner(credentials.accessKey(), credentials.secretKey());
                var signature = signer.processRequest(this.config.region(), "s3", "HEAD", uri, queryMap, headersMap, AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);

                headers.set("x-amz-date", signature.amzDate());
                headers.set("authorization", signature.authorization());
                headers.set("host", uri.getAuthority());
                headers.set("x-amz-content-sha256", AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);

                var request = HttpClientRequest.of("HEAD", uri, "/{bucket}/{object}", headers, HttpBody.empty(), this.config.requestTimeout());
                try (var rs = this.httpClient.execute(request)) {
                    var amxRequestId = rs.headers().getFirst("X-Amz-Request-Id");
                    observation.observeAwsRequestId(amxRequestId);
                    observation.observeAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                    if (rs.code() == HttpURLConnection.HTTP_OK) {
                        var contentLength = rs.headers().getFirst("content-length");
                        var contentLengthLong = contentLength == null
                            ? 0L
                            : Long.parseLong(contentLength);
                        return new HeadObjectResult(bucket, key, contentLengthLong, headers);
                    }
                    if (rs.code() == HttpURLConnection.HTTP_NOT_FOUND) {
                        if (required) {
                            throw new S3ClientNoSuchKeyException(rs.code(), "NoSuchKey", "Object does not exist", amxRequestId);
                        } else {
                            return null;
                        }
                    }
                    // HEAD response cannot have body
                    try (var _ = rs.body()) {
                        throw new S3ClientResponseException("Unexpected response from s3: code=%s".formatted(rs.code()), rs.code());
                    }
                } catch (S3ClientException e) {
                    observation.observeError(e);
                    throw e;
                } catch (Exception e) {
                    observation.observeError(e);
                    throw new S3ClientUnknownException(e);
                } finally {
                    observation.end();
                }
            });
    }

    @Nullable
    @Override
    public GetObjectResult getObject(AwsCredentials credentials, String bucket, String key, @Nullable GetObjectArgs args, boolean required) {
        var observation = this.telemetry.observe("GetObject", bucket);
        observation.observeKey(key);
        return Observation.scoped(observation)
            .call(() -> {
                var headers = HttpHeaders.of();
                var headersMap = new HashMap<String, String>();
                var queryString = (CharSequence) "";
                var queryMap = Collections.<String, String>emptySortedMap();
                if (args != null) {
                    queryString = args.toQueryString();
                    queryMap = args.toQueryMap();
                    args.writeHeadersMap(headersMap);
                    args.writeHeaders(headers);
                }
                var uri = this.uriHelper.uri(bucket, key, queryString);
                var signer = credentials instanceof AwsRequestSigner s
                    ? s
                    : new AwsRequestSigner(credentials.accessKey(), credentials.secretKey());
                var signature = signer.processRequest(this.config.region(), "s3", "GET", uri, queryMap, headersMap, AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);

                headers.set("x-amz-date", signature.amzDate());
                headers.set("authorization", signature.authorization());
                headers.set("host", uri.getAuthority());
                headers.set("x-amz-content-sha256", AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);

                var request = HttpClientRequest.of("GET", uri, "/{bucket}/{object}", headers, HttpBody.empty(), this.config.requestTimeout());
                try {
                    var rs = this.httpClient.execute(request);
                    var amxRequestId = rs.headers().getFirst("X-Amz-Request-Id");
                    observation.observeAwsRequestId(amxRequestId);
                    observation.observeAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                    if (rs.code() == HttpURLConnection.HTTP_OK) {
                        return new GetObjectResultImpl(rs);
                    }
                    if (rs.code() == HttpURLConnection.HTTP_PARTIAL && args != null && args.range != null) {
                        return new GetObjectResultImpl(rs);
                    }
                    try (rs) {
                        if (rs.code() == HttpURLConnection.HTTP_NOT_FOUND && !required) {
                            return null;
                        }
                        try (var body = rs.body()) {
                            throw parseS3Exception(rs, body);
                        }
                    }
                } catch (S3ClientException e) {
                    observation.observeError(e);
                    throw e;
                } catch (Exception e) {
                    observation.observeError(e);
                    throw new S3ClientUnknownException(e);
                } finally {
                    observation.end();
                }
            });
    }


    @Override
    public void deleteObject(AwsCredentials credentials, String bucket, String key, @Nullable DeleteObjectArgs args) throws S3ClientException {
        var observation = this.telemetry.observe("DeleteObject", bucket);
        observation.observeKey(key);
        Observation.scoped(observation)
            .run(() -> {
                var headers = HttpHeaders.of();
                var headersMap = new HashMap<String, String>();
                var queryString = (CharSequence) "";
                var queryMap = Collections.<String, String>emptySortedMap();
                if (args != null) {
                    queryString = args.toQueryString();
                    queryMap = args.toQueryMap();
                    args.writeHeadersMap(headersMap);
                    args.writeHeaders(headers);
                }
                var uri = this.uriHelper.uri(bucket, key, queryString);
                var signer = credentials instanceof AwsRequestSigner s
                    ? s
                    : new AwsRequestSigner(credentials.accessKey(), credentials.secretKey());
                var signature = signer.processRequest(this.config.region(), "s3", "DELETE", uri, queryMap, headersMap, AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);

                headers.set("x-amz-date", signature.amzDate());
                headers.set("authorization", signature.authorization());
                headers.set("host", uri.getAuthority());
                headers.set("x-amz-content-sha256", AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);

                var request = HttpClientRequest.of("DELETE", uri, "/{bucket}/{key}", headers, HttpBody.empty(), this.config.requestTimeout());
                try (var rs = this.httpClient.execute(request);
                     var body = rs.body()) {
                    observation.observeAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                    observation.observeAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                    if (rs.code() == 204) {
                        return;
                    }
                    if (rs.code() == 404) { // no such bucket
                        return;
                    }
                    throw parseS3Exception(rs, body);
                } catch (S3ClientException e) {
                    observation.observeError(e);
                    throw e;
                } catch (Throwable e) {
                    observation.observeError(e);
                    throw new S3ClientUnknownException(e);
                } finally {
                    observation.end();
                }
            });
    }

    @Override
    public void deleteObjects(AwsCredentials credentials, String bucket, List<String> keys) throws S3ClientException {
        var observation = this.telemetry.observe("DeleteObjects", bucket);
        Observation.scoped(observation)
            .run(() -> {
                var xml = DeleteObjectsRequest.toXml(keys.stream().map(DeleteObjectsRequest.S3Object::new)::iterator);
                var payloadSha256 = DigestUtils.sha256(xml, 0, xml.length).hex();
                var bodyMd5 = DigestUtils.md5(xml, 0, xml.length).base64();
                var headers = HttpHeaders.of(
                    "content-md5", bodyMd5
                );
                var uri = this.uriHelper.uri(bucket, "/", "delete=true");
                var signer = credentials instanceof AwsRequestSigner s
                    ? s
                    : new AwsRequestSigner(credentials.accessKey(), credentials.secretKey());

                var signature = signer.processRequest(this.config.region(), "s3", "POST", uri, new TreeMap<>(Map.of("delete", "true")), Map.of("content-md5", bodyMd5), payloadSha256);

                headers.set("x-amz-date", signature.amzDate());
                headers.set("authorization", signature.authorization());
                headers.set("host", uri.getAuthority());
                headers.set("x-amz-content-sha256", payloadSha256);

                var request = HttpClientRequest.of("POST", uri, "/{bucket}", headers, HttpBody.of(xml), this.config.requestTimeout());

                try (var rs = this.httpClient.execute(request);
                     var body = rs.body()) {
                    observation.observeAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                    observation.observeAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                    if (rs.code() == HttpURLConnection.HTTP_OK) {
                        var result = DeleteObjectsResult.fromXml(body.asInputStream());
                        if (!result.errors().isEmpty()) {
                            throw new S3ClientDeleteException(result.errors());
                        }
                        return;
                    }
                    if (rs.code() == HttpURLConnection.HTTP_NOT_FOUND) { // no such bucket
                        return;
                    }
                    throw parseS3Exception(rs, body);
                } catch (S3ClientException e) {
                    observation.observeError(e);
                    throw e;
                } catch (Throwable e) {
                    observation.observeError(e);
                    throw new S3ClientUnknownException(e);
                } finally {
                    observation.end();
                }
            });
    }

    @Override
    public String putObject(AwsCredentials credentials, String bucket, String key, @Nullable PutObjectArgs args, byte[] data, int off, int len) throws S3ClientException {
        var observation = this.telemetry.observe("PutObject", bucket);
        observation.observeKey(key);
        return Observation.scoped(observation)
            .call(() -> {
                var sha256 = DigestUtils.sha256(data, 0, len);
                var sha256Hex = sha256.hex();
                var sha256Base64 = sha256.base64();
                var md5 = DigestUtils.md5(data, 0, len).base64();
                var headersMap = new HashMap<String, String>();
                headersMap.put("content-length", Integer.toString(len));
                headersMap.put("content-md5", md5);
                headersMap.put("x-amz-checksum-sha256", sha256Base64);
                var headers = HttpHeaders.of();
                if (args != null) {
                    args.writeHeadersMap(headersMap);
                    args.writeHeaders(headers);
                }
                var uri = this.uriHelper.uri(bucket, key, null);
                var httpBody = HttpBody.of(ByteBuffer.wrap(data, off, len));
                var signer = credentials instanceof AwsRequestSigner s
                    ? s
                    : new AwsRequestSigner(credentials.accessKey(), credentials.secretKey());
                var signature = signer.processRequest(this.config.region(), "s3", "PUT", uri, AwsRequestSigner.EMPTY_QUERY, headersMap, sha256Hex);

                headers.set("x-amz-date", signature.amzDate());
                headers.set("authorization", signature.authorization());
                headers.set("host", uri.getAuthority());
                headers.set("x-amz-content-sha256", sha256Hex);
                headers.set("content-md5", md5);
                headers.set("x-amz-checksum-sha256", sha256Base64);

                var request = HttpClientRequest.of("PUT", uri, "/{bucket}/{key}", headers, httpBody, this.config.requestTimeout());
                try (var rs = this.httpClient.execute(request);
                     var body = rs.body()) {
                    observation.observeAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                    observation.observeAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                    if (rs.code() == HttpURLConnection.HTTP_OK) {
                        return rs.headers().getFirst("ETag");
                    }
                    throw S3ClientImpl.parseS3Exception(rs, body);
                } catch (S3ClientException e) {
                    observation.observeError(e);
                    throw e;
                } catch (Throwable e) {
                    observation.observeError(e);
                    throw new S3ClientUnknownException(e);
                } finally {
                    observation.end();
                }
            });
    }

    @Override
    public String putObject(AwsCredentials credentials, String bucket, String key, @Nullable PutObjectArgs args, ContentWriter contentWriter) throws S3ClientException {
        var observation = this.telemetry.observe("PutObject", bucket);
        observation.observeKey(key);
        return Observation.scoped(observation)
            .call(() -> {
                var length = contentWriter.length();
                var payloadSha256Hex = "STREAMING-AWS4-HMAC-SHA256-PAYLOAD-TRAILER";
                var headersMap = Map.of(
                    "x-amz-trailer", "x-amz-checksum-sha256",
                    "x-amz-decoded-content-length", Long.toString(length),
                    "expect", "100-continue",
                    "content-encoding", "aws-chunked"
                );
                var headers = HttpHeaders.of();
                headers.set("x-amz-trailer", "x-amz-checksum-sha256");
                headers.set("x-amz-decoded-content-length", Long.toString(length));
                headers.set("expect", "100-continue");
                headers.set("content-encoding", "aws-chunked");
                if (args != null) {
                    args.writeHeadersMap(headersMap);
                    args.writeHeaders(headers);
                }
                var uri = this.uriHelper.uri(bucket, key, null);
                var signer = credentials instanceof AwsRequestSigner s
                    ? s
                    : new AwsRequestSigner(credentials.accessKey(), credentials.secretKey());

                var signature = signer.processRequest(this.config.region(), "s3", "PUT", uri, AwsRequestSigner.EMPTY_QUERY, headersMap, payloadSha256Hex);

                headers.set("x-amz-date", signature.amzDate());
                headers.set("authorization", signature.authorization());
                headers.set("host", uri.getAuthority());
                headers.set("x-amz-content-sha256", payloadSha256Hex);

                var httpBody = new KnownSizeAwsChunkedHttpBody(
                    signer,
                    this.config.region(),
                    (int) this.config.upload().chunkSize().toBytes(),
                    "application/octet-stream",
                    signature.signature(),
                    contentWriter,
                    null
                );
                var request = HttpClientRequest.of("PUT", uri, "/{bucket}/{key}", headers, httpBody, this.config.requestTimeout());
                try (var rs = this.httpClient.execute(request);
                     var body = rs.body()) {
                    observation.observeAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                    observation.observeAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                    if (rs.code() == HttpURLConnection.HTTP_OK) {
                        return rs.headers().getFirst("ETag");
                    }
                    throw S3ClientImpl.parseS3Exception(rs, body);
                } catch (S3ClientException e) {
                    observation.observeError(e);
                    throw e;
                } catch (Throwable e) {
                    observation.observeError(e);
                    throw new S3ClientUnknownException(e);
                } finally {
                    observation.end();
                }
            });
    }

    @Override
    public ListBucketResult listObjectsV2(AwsCredentials credentials, String bucket, @Nullable ListObjectsArgs args) {
        return this.listInternal(credentials, bucket, args);
    }

    @Override
    public Iterator<ListBucketResult.ListBucketItem> listObjectsV2Iterator(AwsCredentials credentials, String bucket, @Nullable ListObjectsArgs args) {
        if (args == null) {
            args = new ListObjectsArgs();
            args.maxKeys = 1000;
        }
        var finalArgs = args.clone();
        if (finalArgs.maxKeys == null || finalArgs.maxKeys > 1000 || finalArgs.maxKeys < 0) {
            finalArgs.maxKeys = 1000;
        }
        var first = this.listInternal(credentials, bucket, finalArgs);
        if (first.nextContinuationToken() == null) {
            return first.items().iterator();
        }
        return new Iterator<>() {
            private final ListObjectsArgs args = finalArgs;
            private ListBucketResult currentList = first;
            private Iterator<ListBucketResult.ListBucketItem> currentIterator = first.items().iterator();

            @Override
            public boolean hasNext() {
                if (currentIterator.hasNext()) {
                    return true;
                }
                if (currentList.nextContinuationToken() == null) {
                    return false;
                }
                args.continuationToken = currentList.nextContinuationToken();
                currentList = listInternal(credentials, bucket, args);
                currentIterator = currentList.items().iterator();
                return currentIterator.hasNext();
            }

            @Override
            public ListBucketResult.ListBucketItem next() {
                if (hasNext()) {
                    return currentIterator.next();
                }
                throw new NoSuchElementException();
            }
        };
    }


    private ListBucketResult listInternal(AwsCredentials credentials, String bucket, @Nullable ListObjectsArgs args) {
        var observation = this.telemetry.observe("ListObjectsV2", bucket);
        return Observation.scoped(observation)
            .call(() -> {
                try {
                    var pathBuilder = new StringBuilder("list-type=2");
                    var queryParameters = new TreeMap<String, String>();
                    queryParameters.put("list-type", "2");
                    var headers = HttpHeaders.of();
                    var headersMap = new HashMap<String, String>();
                    if (args != null) {
                        args.writeHeadersMap(headersMap);
                        args.writeHeaders(headers);
                        queryParameters.putAll(args.toQueryMap());
                        var query = args.toQueryString();
                        if (!query.isEmpty()) {
                            pathBuilder.append('&').append(query);
                        }
                    }

                    var uri = this.uriHelper.uri(bucket, "", pathBuilder.toString());
                    var signer = credentials instanceof AwsRequestSigner s
                        ? s
                        : new AwsRequestSigner(credentials.accessKey(), credentials.secretKey());

                    var signature = signer.processRequest(this.config.region(), "s3", "GET", uri, queryParameters, headersMap, AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);
                    headers.set("x-amz-date", signature.amzDate());
                    headers.set("authorization", signature.authorization());
                    headers.set("host", uri.getAuthority());
                    headers.set("x-amz-content-sha256", AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);

                    var request = HttpClientRequest.of("GET", uri, "/{bucket}?list", headers, HttpBody.empty(), this.config.requestTimeout());

                    try (var rs = this.httpClient.execute(request);
                         var body = rs.body()) {
                        observation.observeAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                        observation.observeAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                        if (rs.code() == 200) {
                            try (var is = body.asInputStream()) {
                                var bytes = is.readAllBytes();
                                var listResult = ru.tinkoff.kora.aws.s3.impl.xml.ListBucketResult.fromXml(new ByteArrayInputStream(bytes));
                                var result = new ArrayList<ListBucketResult.ListBucketItem>(listResult.contents() == null ? 0 : listResult.contents().size());
                                if (listResult.contents() != null) for (var content : listResult.contents()) {
                                    var accessor = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(content.lastModified());
                                    var modified = Instant.from(accessor);
                                    var meta = new ListBucketResult.ListBucketItem(
                                        bucket,
                                        content.key(),
                                        content.eTag(),
                                        content.checksumType(),
                                        content.checksumAlgorithm(),
                                        modified,
                                        content.size(),
                                        content.storageClass(),
                                        content.owner() == null ? null : new ListBucketResult.ListBucketItemOwner(content.owner().displayName(), content.owner().id())
                                    );
                                    result.add(meta);
                                }
                                return new ListBucketResult(
                                    listResult.commonPrefixes(),
                                    listResult.keyCount(),
                                    listResult.nextContinuationToken(),
                                    result
                                );
                            }
                        }
                        throw parseS3Exception(rs, body);
                    }
                } catch (S3ClientException e) {
                    observation.observeError(e);
                    throw e;
                } catch (Throwable e) {
                    observation.observeError(e);
                    throw new S3ClientUnknownException(e);
                } finally {
                    observation.end();
                }
            });
    }


    @Override
    public String createMultipartUpload(AwsCredentials credentials, String bucket, String key, @Nullable CreateMultipartUploadArgs args) throws S3ClientException {
        var observation = this.telemetry.observe("CreateMultipartUpload", bucket);
        observation.observeKey(key);
        return Observation.scoped(observation)
            .call(() -> {
                var headers = HttpHeaders.of();
                var headersMap = new HashMap<String, String>();
                if (args != null) {
                    args.writeHeadersMap(headersMap);
                    args.writeHeaders(headers);
                }
                var uri = this.uriHelper.uri(bucket, key, "uploads=true");
                var signer = credentials instanceof AwsRequestSigner s
                    ? s
                    : new AwsRequestSigner(credentials.accessKey(), credentials.secretKey());
                var signature = signer.processRequest(this.config.region(), "s3", "POST", uri, new TreeMap<>(Map.of("uploads", "true")), headersMap, AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);

                headers.add("x-amz-checksum-algorithm", "SHA256");
                headers.add("x-amz-checksum-type", "COMPOSITE");

                headers.set("x-amz-date", signature.amzDate());
                headers.set("authorization", signature.authorization());
                headers.set("host", uri.getAuthority());
                headers.set("x-amz-content-sha256", AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);

                var request = HttpClientRequest.of("POST", uri, "/{bucket}?uploads=true", headers, HttpBody.empty(), this.config.requestTimeout());
                try (var rs = this.httpClient.execute(request);
                     var body = rs.body()) {
                    observation.observeAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                    observation.observeAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                    if (rs.code() == HttpURLConnection.HTTP_OK) {
                        var result = InitiateMultipartUploadResult.fromXml(body.asInputStream());
                        observation.observeUploadId(result.uploadId());
                        return result.uploadId();
                    }
                    throw S3ClientImpl.parseS3Exception(rs, body);
                } catch (S3ClientException e) {
                    observation.observeError(e);
                    throw e;
                } catch (Throwable e) {
                    observation.observeError(e);
                    throw new S3ClientUnknownException(e);
                } finally {
                    observation.end();
                }
            });
    }

    @Override
    public void abortMultipartUpload(AwsCredentials credentials, String bucket, String key, String uploadId, @Nullable AbortMultipartUploadArgs args) throws S3ClientException {
        var observation = this.telemetry.observe("AbortMultipartUpload", bucket);
        observation.observeKey(key);
        Observation.scoped(observation)
            .run(() -> {
                var uri = this.uriHelper.uri(bucket, key, "uploadId=" + uploadId);
                var signer = credentials instanceof AwsRequestSigner s
                    ? s
                    : new AwsRequestSigner(credentials.accessKey(), credentials.secretKey());

                var headersMap = new HashMap<String, String>();
                var headers = HttpHeaders.of();
                if (args != null) {
                    args.writeHeaders(headers);
                    args.writeHeaders(headers);
                }
                var signature = signer.processRequest(this.config.region(), "s3", "DELETE", uri, new TreeMap<>(Map.of("uploadId", uploadId)), headersMap, AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);
                headers.set("x-amz-date", signature.amzDate());
                headers.set("authorization", signature.authorization());
                headers.set("host", uri.getAuthority());
                headers.set("x-amz-content-sha256", AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);

                var request = HttpClientRequest.of("DELETE", uri, "/{bucket}/{key}?uploadId={uploadId}", headers, HttpBody.empty(), this.config.requestTimeout());
                try (var rs = this.httpClient.execute(request);
                     var body = rs.body()) {
                    observation.observeAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                    observation.observeAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                    if (rs.code() == HttpURLConnection.HTTP_NO_CONTENT) {
                        return;
                    }
                    throw S3ClientImpl.parseS3Exception(rs, body);
                } catch (S3ClientException e) {
                    observation.observeError(e);
                    throw e;
                } catch (Throwable e) {
                    observation.observeError(e);
                    throw new S3ClientUnknownException(e);
                } finally {
                    observation.end();
                }
            });
    }

    @Override
    public String completeMultipartUpload(AwsCredentials credentials, String bucket, String key, String uploadId, List<UploadedPart> parts, @Nullable CompleteMultipartUploadArgs args) throws S3ClientException {
        var observation = this.telemetry.observe("CompleteMultipartUpload", bucket);
        observation.observeKey(key);
        return Observation.scoped(observation)
            .call(() -> {
                var rqParts = new ArrayList<CompleteMultipartUploadRequest.Part>();
                for (var part : parts) {
                    rqParts.add(new CompleteMultipartUploadRequest.Part(
                        part.checksumCRC32(),
                        part.checksumCRC32C(),
                        part.checksumCRC64NVME(),
                        part.checksumSHA1(),
                        part.checksumSHA256(),
                        part.eTag(),
                        part.partNumber()
                    ));
                }
                var completeRequest = new CompleteMultipartUploadRequest(rqParts);
                var xml = completeRequest.toXml();
                var sha256 = DigestUtils.sha256(xml, 0, xml.length).hex();
                var uri = this.uriHelper.uri(bucket, key, "uploadId=" + uploadId);
                var signer = credentials instanceof AwsRequestSigner s
                    ? s
                    : new AwsRequestSigner(credentials.accessKey(), credentials.secretKey());
                var completeSize = Long.toString(parts.stream().mapToLong(UploadedPart::size).sum());
                var headers = HttpHeaders.of();
                var headersMap = new HashMap<String, String>();
                headersMap.put("x-amz-mp-object-size", completeSize);
                if (args != null) {
                    args.writeHeaders(headers);
                    args.writeHeadersMap(headersMap);
                }

                var signature = signer.processRequest(this.config.region(), "s3", "POST", uri, new TreeMap<>(Map.of("uploadId", uploadId)), headersMap, sha256);

                headers.set("x-amz-date", signature.amzDate());
                headers.set("authorization", signature.authorization());
                headers.set("host", uri.getAuthority());
                headers.set("x-amz-content-sha256", sha256);
                headers.set("x-amz-mp-object-size", completeSize);

                var request = HttpClientRequest.of("POST", uri, "/{bucket}/{key}?uploadId={uploadId}", headers, HttpBody.of("text/xml", xml), this.config.requestTimeout());
                try (var rs = this.httpClient.execute(request);
                     var body = rs.body()) {
                    observation.observeAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                    observation.observeAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                    if (rs.code() == HttpURLConnection.HTTP_OK) {
                        var result = CompleteMultipartUploadResult.fromXml(body.asInputStream());
                        return result.etag();
                    }
                    throw S3ClientImpl.parseS3Exception(rs, body);
                } catch (S3ClientException e) {
                    observation.observeError(e);
                    throw e;
                } catch (Throwable e) {
                    observation.observeError(e);
                    throw new S3ClientUnknownException(e);
                } finally {
                    observation.end();
                }
            });
    }

    @Override
    public ListMultipartUploadsResult listMultipartUploads(AwsCredentials credentials, String bucket, @Nullable ListMultipartUploadsArgs args) throws S3ClientException {
        var observation = this.telemetry.observe("ListMultipartUploads", bucket);
        return Observation.scoped(observation)
            .call(() -> {
                var headers = HttpHeaders.of();
                var headersMap = new HashMap<String, String>();
                var query = new StringBuilder("uploads=true");
                var queryMap = new TreeMap<String, String>();
                queryMap.put("uploads", "true");
                if (args != null) {
                    args.writeHeaders(headers);
                    args.writeHeadersMap(headersMap);
                    var argsQuery = args.toQueryString();
                    if (!argsQuery.isEmpty()) {
                        query.append("&").append(argsQuery);
                    }
                    queryMap.putAll(args.toQueryMap());
                }
                var uri = this.uriHelper.uri(bucket, "", query.toString());
                var signer = credentials instanceof AwsRequestSigner s
                    ? s
                    : new AwsRequestSigner(credentials.accessKey(), credentials.secretKey());
                var signature = signer.processRequest(this.config.region(), "s3", "GET", uri, queryMap, Map.of(), AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);

                headers.set("x-amz-date", signature.amzDate());
                headers.set("authorization", signature.authorization());
                headers.set("host", uri.getAuthority());
                headers.set("x-amz-content-sha256", AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);

                var request = HttpClientRequest.of("GET", uri, "/{bucket}/{object}", headers, HttpBody.empty(), this.config.requestTimeout());
                try (var rs = this.httpClient.execute(request)) {
                    var amxRequestId = rs.headers().getFirst("X-Amz-Request-Id");
                    observation.observeAwsRequestId(amxRequestId);
                    observation.observeAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                    if (rs.code() == HttpURLConnection.HTTP_OK) {
                        var xml = ru.tinkoff.kora.aws.s3.impl.xml.ListMultipartUploadsResult.fromXml(rs.body().asInputStream());
                        var uploads = new ArrayList<Upload>();
                        for (var upload : xml.uploads()) {
                            uploads.add(new Upload(
                                upload.key(),
                                upload.uploadId(),
                                upload.initiated()
                            ));
                        }
                        return new ListMultipartUploadsResult(
                            xml.nextKeyMarker(),
                            xml.nextUploadIdMarker(),
                            uploads
                        );
                    }
                    try (var body = rs.body()) {
                        throw S3ClientImpl.parseS3Exception(rs, body);
                    }
                } catch (S3ClientException e) {
                    observation.observeError(e);
                    throw e;
                } catch (Exception e) {
                    observation.observeError(e);
                    throw new S3ClientUnknownException(e);
                } finally {
                    observation.end();
                }
            });

    }

    @Override
    public UploadedPart uploadPart(AwsCredentials credentials, String bucket, String key, String uploadId, int partNumber, UploadPartArgs args, byte[] data, int off, int len) throws S3ClientException {
        var observation = this.telemetry.observe("UploadPart", bucket);
        observation.observeKey(key);
        return Observation.scoped(observation)
            .call(() -> {
                var sha256 = DigestUtils.sha256(data, 0, len);
                var sha256Hex = sha256.hex();
                var sha256Base64 = sha256.base64();
                var md5 = DigestUtils.md5(data, 0, len).base64();
                var headersMap = new HashMap<String, String>();
                headersMap.put("content-length", Integer.toString(len));
                headersMap.put("content-md5", md5);
                headersMap.put("x-amz-checksum-sha256", sha256Base64);
                var headers = HttpHeaders.of();
                if (args != null) {
                    args.writeHeadersMap(headersMap);
                    args.writeHeaders(headers);
                }
                var queryParams = new TreeMap<String, String>();
                queryParams.put("partNumber", Integer.toString(partNumber));
                queryParams.put("uploadId", uploadId);
                var uri = this.uriHelper.uri(bucket, key, "partNumber=" + partNumber + "&uploadId=" + uploadId);
                var httpBody = HttpBody.of(ByteBuffer.wrap(data, off, len));
                var signer = credentials instanceof AwsRequestSigner s
                    ? s
                    : new AwsRequestSigner(credentials.accessKey(), credentials.secretKey());
                var signature = signer.processRequest(this.config.region(), "s3", "PUT", uri, queryParams, headersMap, sha256Hex);

                headers.set("x-amz-date", signature.amzDate());
                headers.set("authorization", signature.authorization());
                headers.set("host", uri.getAuthority());
                headers.set("x-amz-content-sha256", sha256Hex);
                headers.set("content-md5", md5);
                headers.set("x-amz-checksum-sha256", sha256Base64);

                var request = HttpClientRequest.of("PUT", uri, "/{bucket}/{key}?partNumber={partNumber}&uploadId={uploadId}", headers, httpBody, this.config.requestTimeout());
                try (var rs = this.httpClient.execute(request);
                     var body = rs.body()) {
                    observation.observeAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                    observation.observeAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                    if (rs.code() == HttpURLConnection.HTTP_OK) {
                        var etag = rs.headers().getFirst("ETag");
                        return new UploadedPart(
                            null, null, null, null, sha256Base64, etag, partNumber, len
                        );
                    }
                    throw S3ClientImpl.parseS3Exception(rs, body);
                } catch (S3ClientException e) {
                    observation.observeError(e);
                    throw e;
                } catch (Throwable e) {
                    observation.observeError(e);
                    throw new S3ClientUnknownException(e);
                } finally {
                    observation.end();
                }
            });
    }

    @Override
    public UploadedPart uploadPart(AwsCredentials credentials, String bucket, String key, String uploadId, int partNumber, UploadPartArgs args, ContentWriter contentWriter) throws S3ClientException {
        var observation = this.telemetry.observe("UploadPart", bucket);
        observation.observeKey(key);
        return Observation.scoped(observation)
            .call(() -> {
                var len = contentWriter.length();
                var payloadSha256Hex = "STREAMING-AWS4-HMAC-SHA256-PAYLOAD-TRAILER";
                var headersMap = new HashMap<String, String>();
                headersMap.put("x-amz-trailer", "x-amz-checksum-sha256");
                headersMap.put("x-amz-decoded-content-length", Long.toString(len));
                headersMap.put("expect", "100-continue");
                headersMap.put("content-encoding", "aws-chunked");
                var headers = HttpHeaders.of();
                headers.set("x-amz-trailer", "x-amz-checksum-sha256");
                headers.set("x-amz-decoded-content-length", Long.toString(len));
                headers.set("expect", "100-continue");
                headers.set("content-encoding", "aws-chunked");
                if (args != null) {
                    args.writeHeadersMap(headersMap);
                    args.writeHeaders(headers);
                }
                var queryParams = new TreeMap<String, String>();
                queryParams.put("partNumber", Integer.toString(partNumber));
                queryParams.put("uploadId", uploadId);
                var uri = this.uriHelper.uri(bucket, key, "partNumber=" + partNumber + "&uploadId=" + uploadId);
                var signer = credentials instanceof AwsRequestSigner s
                    ? s
                    : new AwsRequestSigner(credentials.accessKey(), credentials.secretKey());

                var signature = signer.processRequest(this.config.region(), "s3", "PUT", uri, queryParams, headersMap, payloadSha256Hex);

                headers.set("x-amz-date", signature.amzDate());
                headers.set("authorization", signature.authorization());
                headers.set("host", uri.getAuthority());
                headers.set("x-amz-content-sha256", payloadSha256Hex);

                var httpBody = new KnownSizeAwsChunkedHttpBody(
                    signer,
                    this.config.region(),
                    (int) this.config.upload().chunkSize().toBytes(),
                    "application/octet-stream",
                    signature.signature(),
                    contentWriter,
                    null
                );
                var request = HttpClientRequest.of("PUT", uri, "/{bucket}/{key}?partNumber={partNumber}&uploadId={uploadId}", headers, httpBody, this.config.requestTimeout());
                try (var rs = this.httpClient.execute(request);
                     var body = rs.body()) {
                    observation.observeAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                    observation.observeAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                    if (rs.code() == HttpURLConnection.HTTP_OK) {
                        var etag = rs.headers().getFirst("ETag");
                        return new UploadedPart(
                            null, null, null, null, httpBody.sha256(), etag, partNumber, len
                        );
                    }
                    throw S3ClientImpl.parseS3Exception(rs, body);
                } catch (S3ClientException e) {
                    observation.observeError(e);
                    throw e;
                } catch (Throwable e) {
                    observation.observeError(e);
                    throw new S3ClientUnknownException(e);
                } finally {
                    observation.end();
                }
            });
    }

    @Override
    public ListPartsResult listParts(AwsCredentials credentials, String bucket, String key, String uploadId, @Nullable ListPartsArgs args) throws S3ClientException {
        var observation = this.telemetry.observe("ListParts", bucket);
        observation.observeKey(key);
        return Observation.scoped(observation)
            .call(() -> {
                var headers = HttpHeaders.of();
                var headersMap = new HashMap<String, String>();
                var queryParams = new TreeMap<String, String>();
                queryParams.put("uploadId", uploadId);
                var query = new StringBuilder("uploadId=").append(uploadId);
                if (args != null) {
                    args.writeHeaders(headers);
                    args.writeHeadersMap(headersMap);
                    query.append("&");
                    query.append(args.toQueryString());
                    queryParams.putAll(args.toQueryMap());
                }
                var uri = this.uriHelper.uri(bucket, key, query.toString());
                var signer = credentials instanceof AwsRequestSigner s
                    ? s
                    : new AwsRequestSigner(credentials.accessKey(), credentials.secretKey());
                var signature = signer.processRequest(this.config.region(), "s3", "GET", uri, queryParams, headersMap, AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);

                headers.set("x-amz-date", signature.amzDate());
                headers.set("authorization", signature.authorization());
                headers.set("host", uri.getAuthority());
                headers.set("x-amz-content-sha256", AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX);

                var request = HttpClientRequest.of("GET", uri, "/{bucket}/{key}?uploadId={uploadId}", headers, HttpBody.empty(), this.config.requestTimeout());
                try (var rs = this.httpClient.execute(request);
                     var body = rs.body()) {
                    observation.observeAwsRequestId(rs.headers().getFirst("X-Amz-Request-Id"));
                    observation.observeAwsExtendedId(rs.headers().getFirst("x-amz-id-2"));
                    if (rs.code() == HttpURLConnection.HTTP_OK) {
                        var listPartsResult = ru.tinkoff.kora.aws.s3.impl.xml.ListPartsResult.fromXml(body.asInputStream());
                        observation.observeUploadId(listPartsResult.uploadId());
                        var parts = new ArrayList<UploadedPart>(listPartsResult.parts().size());
                        for (var part : listPartsResult.parts()) {
                            parts.add(new UploadedPart(
                                part.checksumCRC32(),
                                part.checksumCRC32C(),
                                part.checksumCRC64NVME(),
                                part.checksumSHA1(),
                                part.checksumSHA256(),
                                part.eTag(),
                                part.partNumber(),
                                part.size()
                            ));
                        }
                        return new ListPartsResult(
                            listPartsResult.partNumberMarker(),
                            listPartsResult.nextPartNumberMarker(),
                            listPartsResult.isTruncated(),
                            parts
                        );
                    }
                    throw S3ClientImpl.parseS3Exception(rs, body);
                } catch (S3ClientException e) {
                    observation.observeError(e);
                    throw e;
                } catch (Throwable e) {
                    observation.observeError(e);
                    throw new S3ClientUnknownException(e);
                } finally {
                    observation.end();
                }
            });
    }

    static S3ClientException parseS3Exception(HttpClientResponse rs, HttpBodyInput body) {
        try (var is = body.asInputStream()) {
            var bytes = is.readAllBytes();
            try {
                var s3Error = S3Error.fromXml(new ByteArrayInputStream(bytes));
                if ("NoSuchKey".equals(s3Error.code())) {
                    throw new S3ClientNoSuchKeyException(rs.code(), s3Error.code(), s3Error.message(), s3Error.requestId());
                }
                throw new S3ClientErrorException(rs.code(), s3Error.code(), s3Error.message(), s3Error.requestId());
            } catch (S3ClientException e) {
                throw e;
            } catch (Exception e) {
                throw new S3ClientResponseException("Unexpected response from s3: code=%s, body=%s".formatted(rs.code(), new String(bytes, StandardCharsets.UTF_8)), e, rs.code());
            }
        } catch (IOException e) {
            throw new S3ClientUnknownException(e);
        }
    }
}
