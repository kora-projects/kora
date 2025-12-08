package ru.tinkoff.kora.aws.s3.impl;

import ru.tinkoff.kora.aws.s3.AwsCredentials;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class AwsRequestSigner implements AwsCredentials {
    public static final String EMPTY_PAYLOAD_SHA256_HEX = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    public static final SortedMap<String, String> EMPTY_QUERY = Collections.unmodifiableSortedMap(new TreeMap<>());
    public static final ZoneId UTC = ZoneId.of("Z");
    public static final DateTimeFormatter SIGNER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US).withZone(UTC);
    public static final DateTimeFormatter AMZ_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US).withZone(UTC);
    public static final byte[] CLRF_BYTES = "\r\n".getBytes(StandardCharsets.US_ASCII);

    private final String accessKey;
    private final String secretKey;
    private final Mac secretKeyMac;

    public AwsRequestSigner(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;

        var secretKeySpec = new SecretKeySpec(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        try {
            this.secretKeyMac = Mac.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // never gonna happen
        }
        try {
            this.secretKeyMac.init(secretKeySpec);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public String accessKey() {
        return this.accessKey;
    }

    @Override
    public String secretKey() {
        return this.secretKey;
    }

    public record SignRequestResult(String amzDate, String authorization, String signature) {

    }

    public SignRequestResult processRequest(String region, String service, String method, URI uri, SortedMap<String, String> queryParameters, Map<String, String> additionalHeaders, String payloadSha256Hex) {
        var date = ZonedDateTime.now(UTC);
        var amzDate = date.format(AMZ_DATE_FORMAT);

        final CharSequence signedHeaders;
        final CharSequence canonicalHeadersStr;
        if (additionalHeaders.isEmpty()) {
            signedHeaders = "host;x-amz-content-sha256;x-amz-date";
            canonicalHeadersStr = "host:" + uri.getAuthority() + "\n"
                + "x-amz-content-sha256:" + payloadSha256Hex + "\n"
                + "x-amz-date:" + amzDate + "\n";
        } else {
            var headers = new TreeMap<String, String>();
            headers.put("host", uri.getAuthority());
            headers.put("x-amz-date", amzDate);
            headers.put("x-amz-content-sha256", payloadSha256Hex);
            for (var entry : additionalHeaders.entrySet()) {
                var headerName = entry.getKey().toLowerCase();
                headers.put(headerName, entry.getValue());
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
        var canonicalRequest = method + "\n"
            + uri.getPath() + "\n"
            + canonicalQueryStr + "\n"
            + canonicalHeadersStr + "\n"
            + signedHeaders + "\n"
            + payloadSha256Hex;


        var signerDate = date.format(SIGNER_DATE_FORMAT);
        var scope = signerDate + "/" + region + "/" + service + "/aws4_request";
        var canonicalRequestHash = DigestUtils.sha256(canonicalRequest).hex();

        var stringToSign = "AWS4-HMAC-SHA256" + "\n"
            + amzDate + "\n"
            + scope + "\n"
            + canonicalRequestHash;

        var signature = this.awsSign(region, signerDate, stringToSign);
        var authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        return new SignRequestResult(amzDate, authorization, signature);
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

    public String awsSign(String region, String signerDate, String stringToSign) {
        var dateKey = DigestUtils.sumHmac(secretKeyMac, signerDate.getBytes(StandardCharsets.US_ASCII));
        var dateRegionKey = DigestUtils.sumHmac(dateKey, region.getBytes(StandardCharsets.US_ASCII));
        var dateRegionServiceKey = DigestUtils.sumHmac(dateRegionKey, "s3".getBytes(StandardCharsets.US_ASCII));
        var signingKey = DigestUtils.sumHmac(dateRegionServiceKey, "aws4_request".getBytes(StandardCharsets.US_ASCII));
        var digest = DigestUtils.sumHmac(signingKey, stringToSign.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
