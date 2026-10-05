package io.koraframework.s3.client.kora.impl;

import io.koraframework.s3.client.kora.S3ClientConfig;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Objects;
import java.util.StringJoiner;

final class UriHelper {

    private final S3ClientConfig.AddressStyle addressStyle;
    private final String scheme;
    private final String endpoint;

    public UriHelper(S3ClientConfig config) {
        var addressStyle = config.addressStyle();
        if (addressStyle == null) {
            throw new NullPointerException("addressStyle is null");
        }
        this.addressStyle = addressStyle;
        var uri = URI.create(config.endpoint());
        this.scheme = Objects.requireNonNullElse(uri.getScheme(), "https");
        var endpoint = uri.getHost();
        if (uri.getPort() != -1) {
            endpoint += ":" + uri.getPort();
        }
        if (uri.getPath() != null && !uri.getRawPath().isBlank()) {
            endpoint += uri.getRawPath();
        }
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        this.endpoint = endpoint;
    }

    public URI uri(String bucket, String key, @Nullable CharSequence query) {
        key = encodePath(key);
        var path = "/" + key + (query == null || query.isEmpty() ? "" : "?" + query);
        if (this.addressStyle == S3ClientConfig.AddressStyle.PATH) {
            return URI.create(this.scheme + "://" + this.endpoint + "/" + bucket + path);
        }
        if (this.addressStyle == S3ClientConfig.AddressStyle.VIRTUAL_HOSTED) {
            return URI.create(this.scheme + "://" + bucket + "." + this.endpoint + path);
        }
        throw new IllegalStateException("AddressStyle is not supported: " + this.addressStyle);
    }

    private String encodePath(String path) {
        var encodedPath = new StringJoiner("/");
        for (var pathSegment : path.split("/", -1)) {
            encodedPath.add(encode(pathSegment));
        }
        return encodedPath.toString();
    }

    private String encode(String str) {
        if (str == null) {
            return "";
        }
        return S3RequestSigner.uriEncode(str);
    }
}
