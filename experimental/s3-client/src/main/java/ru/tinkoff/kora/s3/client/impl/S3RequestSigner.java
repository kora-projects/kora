package ru.tinkoff.kora.s3.client.impl;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class S3RequestSigner {
    public static final String EMPTY_PAYLOAD_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    public static final SortedMap<String, String> EMPTY_QUERY = Collections.unmodifiableSortedMap(new TreeMap<>());
    private static final ZoneId UTC = ZoneId.of("Z");
    private static final DateTimeFormatter SIGNER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US).withZone(UTC);
    private static final DateTimeFormatter AMZ_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US).withZone(UTC);
    private static final byte[] CLRF_BYTES = "\r\n".getBytes(StandardCharsets.US_ASCII);

    private final String accessKey;
    private final Mac secretKey;
    private final String region;

    public S3RequestSigner(String accessKey, String secretKey, String region) {
        this.accessKey = accessKey;
        this.region = region;
        var secretKeySpec = new SecretKeySpec(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        try {
            this.secretKey = Mac.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // never gonna happen
        }
        try {
            this.secretKey.init(secretKeySpec);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException(e);
        }
    }


    public void processRequest(HttpClientRequest request, SortedMap<String, String> queryParameters, Map<String, String> additionalHeaders, String payloadSha256) {
        var date = ZonedDateTime.now(ZoneOffset.UTC);
        var amzDate = date.format(AMZ_DATE_FORMAT);

        final CharSequence signedHeaders;
        final CharSequence canonicalHeadersStr;
        if (additionalHeaders.isEmpty()) {
            signedHeaders = "host;x-amz-content-sha256;x-amz-date";
            canonicalHeadersStr = "host:" + request.uri().getAuthority() + "\n"
                + "x-amz-content-sha256:" + payloadSha256 + "\n"
                + "x-amz-date:" + amzDate + "\n";
        } else {
            var headers = new TreeMap<String, String>();
            headers.put("host", request.uri().getAuthority());
            headers.put("x-amz-date", amzDate);
            for (var entry : additionalHeaders.entrySet()) {
                var headerName = entry.getKey().toLowerCase();
                headers.put(headerName, entry.getValue());
                request.headers().set(headerName, entry.getValue());
            }
            var signedHeadersSb = new StringBuilder();
            var canonicalHeadersStrSb = new StringBuilder();
            for (var it = headers.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                var header = entry.getKey();
                var value = entry.getValue();
                signedHeadersSb.append(header);
                if (it.hasNext()) {
                    signedHeadersSb.append(';');
                }
                canonicalHeadersStrSb.append(header).append(':').append(value).append('\n');
            }
            signedHeaders = signedHeadersSb;
            canonicalHeadersStr = canonicalHeadersStrSb;
        }


        var canonicalQueryStr = getCanonicalizedQueryString(queryParameters);
        // CanonicalRequest =
        //   HTTPRequestMethod + '\n' +
        //   CanonicalURI + '\n' +
        //   CanonicalQueryString + '\n' +
        //   CanonicalHeaders + '\n' +
        //   SignedHeaders + '\n' +
        //   HexEncode(Hash(RequestPayload))
        var canonicalRequest = request.method() + "\n"
            + request.uri().getPath() + "\n"
            + canonicalQueryStr + "\n"
            + canonicalHeadersStr + "\n"
            + signedHeaders + "\n"
            + payloadSha256;


        var signerDate = date.format(SIGNER_DATE_FORMAT);
        var scope = signerDate + "/" + this.region + "/s3/aws4_request";
        var canonicalRequestHash = DigestUtils.sha256(canonicalRequest).hex();

        var stringToSign = "AWS4-HMAC-SHA256" + "\n"
            + amzDate + "\n"
            + scope + "\n"
            + canonicalRequestHash;

        var signature = this.awsSign(signerDate, stringToSign);
        var authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        request.headers().set("host", request.uri().getAuthority());
        request.headers().set("x-amz-date", amzDate);
        request.headers().set("authorization", authorization);
        request.headers().set("x-amz-content-sha256", payloadSha256);
    }

    public String processRequestChunked(ZonedDateTime date, HttpClientRequest request, SortedMap<String, String> queryParameters, long bodyLength, long contentLength, @Nullable String contentEncoding, @Nullable String sha256Base64) {
        var amzDate = date.format(AMZ_DATE_FORMAT);
        if (contentEncoding == null) {
            contentEncoding = "aws-chunked";
        } else {
            contentEncoding = "aws-chunked, " + contentEncoding;
        }

        var signedHeaders = sha256Base64 == null
            ? "content-encoding;content-length;expect;host;x-amz-content-sha256;x-amz-date;x-amz-decoded-content-length;x-amz-trailer"
            : "content-encoding;content-length;expect;host;x-amz-checksum-sha256;x-amz-content-sha256;x-amz-date;x-amz-decoded-content-length";
        var canonicalHeadersStr = "content-encoding:" + contentEncoding + "\n"
            + "content-length:" + contentLength + "\n"
            + "expect:100-continue\n"
            + "host:" + request.uri().getAuthority() + "\n"
            + (sha256Base64 == null ? "" : "x-amz-checksum-sha256:" + sha256Base64 + "\n")
            + (sha256Base64 == null ? "x-amz-content-sha256:STREAMING-AWS4-HMAC-SHA256-PAYLOAD-TRAILER" : "x-amz-content-sha256:STREAMING-AWS4-HMAC-SHA256-PAYLOAD") + "\n"
            + "x-amz-date:" + amzDate + "\n"
            + "x-amz-decoded-content-length:" + bodyLength + "\n"
            + (sha256Base64 != null ? "" : "x-amz-trailer:x-amz-checksum-sha256\n");

        var canonicalQueryStr = getCanonicalizedQueryString(queryParameters);
        var canonicalRequest = request.method() + "\n"
            + request.uri().getPath() + "\n"
            + canonicalQueryStr + "\n"
            + canonicalHeadersStr + "\n"
            + signedHeaders + "\n"
            + (sha256Base64 != null ? "STREAMING-AWS4-HMAC-SHA256-PAYLOAD" : "STREAMING-AWS4-HMAC-SHA256-PAYLOAD-TRAILER");
        var canonicalRequestHash = DigestUtils.sha256(canonicalRequest).hex();

        var signerDate = date.format(SIGNER_DATE_FORMAT);
        var scope = signerDate + "/" + this.region + "/s3/aws4_request";

        var stringToSign = "AWS4-HMAC-SHA256" + "\n"
            + amzDate + "\n"
            + scope + "\n"
            + canonicalRequestHash;

        var signature = this.awsSign(signerDate, stringToSign);
        var authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope + ",SignedHeaders=" + signedHeaders + ",Signature=" + signature;

        request.headers().set("content-encoding", contentEncoding);
        request.headers().set("host", request.uri().getAuthority());
        if (sha256Base64 != null) {
            request.headers().set("x-amz-checksum-sha256", sha256Base64);
            request.headers().set("x-amz-content-sha256", "STREAMING-AWS4-HMAC-SHA256-PAYLOAD");
        } else {
            request.headers().set("x-amz-content-sha256", "STREAMING-AWS4-HMAC-SHA256-PAYLOAD-TRAILER");
            request.headers().set("x-amz-trailer", "x-amz-checksum-sha256");
        }
        request.headers().set("x-amz-date", amzDate);
        request.headers().set("x-amz-decoded-content-length", bodyLength);
        request.headers().set("authorization", authorization);
        request.headers().set("expect", "100-continue");

        return signature;
    }

    public String processChunk(ZonedDateTime date, String previousSignature, byte[] buf, int read, OutputStream os) throws IOException {
        var amzDate = date.format(AMZ_DATE_FORMAT);
        var signerDate = date.format(SIGNER_DATE_FORMAT);

        var scope = signerDate + "/" + this.region + "/s3/aws4_request";
        var chunkContentSha = DigestUtils.sha256(buf, 0, read).hex();

        var stringToSign = "AWS4-HMAC-SHA256-PAYLOAD" + "\n"
            + amzDate + "\n"
            + scope + "\n"
            + previousSignature + "\n"
            + EMPTY_PAYLOAD_SHA256 + "\n"
            + chunkContentSha;

        var signature = this.awsSign(signerDate, stringToSign);

        os.write(Integer.toHexString(read).getBytes(StandardCharsets.US_ASCII));
        os.write(";chunk-signature=".getBytes(StandardCharsets.US_ASCII));
        os.write(signature.getBytes(StandardCharsets.US_ASCII));
        os.write(CLRF_BYTES);
        os.write(buf, 0, read);
        os.write(CLRF_BYTES);

        return signature;
    }

    public String processFinalChunk(ZonedDateTime date, String previousSignature, OutputStream os) throws IOException {
        var amzDate = date.format(AMZ_DATE_FORMAT);
        var signerDate = date.format(SIGNER_DATE_FORMAT);
        var scope = signerDate + "/" + this.region + "/s3/aws4_request";

        var stringToSign = "AWS4-HMAC-SHA256-PAYLOAD" + "\n"
            + amzDate + "\n"
            + scope + "\n"
            + previousSignature + "\n"
            + EMPTY_PAYLOAD_SHA256 + "\n"
            + EMPTY_PAYLOAD_SHA256;

        var signature = this.awsSign(signerDate, stringToSign);

        os.write("0".getBytes(StandardCharsets.US_ASCII));
        os.write(";chunk-signature=".getBytes(StandardCharsets.US_ASCII));
        os.write(signature.getBytes(StandardCharsets.US_ASCII));
        os.write(CLRF_BYTES);

        return signature;
    }

    public String processTrailer(ZonedDateTime date, String previousSignature, String sha256, OutputStream os) throws IOException {
        var amzDate = date.format(AMZ_DATE_FORMAT);
        var signerDate = date.format(SIGNER_DATE_FORMAT);

        var scope = signerDate + "/" + this.region + "/s3/aws4_request";
        var trailerBytes = ("x-amz-checksum-sha256:" + sha256 + "\n").getBytes(StandardCharsets.US_ASCII);
        var chunkContentSha = DigestUtils.sha256(trailerBytes, 0, trailerBytes.length).hex();

        var stringToSign = "AWS4-HMAC-SHA256-TRAILER" + "\n"
            + amzDate + "\n"
            + scope + "\n"
            + previousSignature + "\n"
            + chunkContentSha;

        var signature = this.awsSign(signerDate, stringToSign);
        os.write(trailerBytes);
        os.write(CLRF_BYTES);
        os.write("x-amz-trailer-signature:".getBytes(StandardCharsets.US_ASCII));
        os.write(signature.getBytes(StandardCharsets.US_ASCII));
        os.write(CLRF_BYTES);
        os.write(CLRF_BYTES);

        return signature;
    }

    private String awsSign(String signerDate, String stringToSign) {
        var dateKey = DigestUtils.sumHmac(secretKey, signerDate.getBytes(StandardCharsets.US_ASCII));
        var dateRegionKey = DigestUtils.sumHmac(dateKey, region.getBytes(StandardCharsets.US_ASCII));
        var dateRegionServiceKey = DigestUtils.sumHmac(dateRegionKey, "s3".getBytes(StandardCharsets.US_ASCII));
        var signingKey = DigestUtils.sumHmac(dateRegionServiceKey, "aws4_request".getBytes(StandardCharsets.US_ASCII));
        var digest = DigestUtils.sumHmac(signingKey, stringToSign.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }


    private static String getCanonicalizedQueryString(SortedMap<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }

        var builder = new StringBuilder();
        for (var it = parameters.entrySet().iterator(); it.hasNext(); ) {
            var pair = it.next();
            builder.append(pair.getKey());
            builder.append("=");
            builder.append(pair.getValue());
            if (it.hasNext()) {
                builder.append("&");
            }
        }
        return builder.toString();
    }
}
