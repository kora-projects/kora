package ru.tinkoff.kora.aws.s3.impl;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.aws.s3.S3Config;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class UriHelper {
    private final S3Config.AddressStyle addressStyle;
    private final String scheme;
    private final String endpoint;

    public UriHelper(S3Config config) {
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
            endpoint += "/" + uri.getRawPath();
        }
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        this.endpoint = endpoint;
    }

    public URI uri(String bucket, String key, @Nullable CharSequence query) {
        key = encodePath(key);
        var path = "/" + key + (query == null || query.isEmpty() ? "" : "?" + query);
        if (this.addressStyle == S3Config.AddressStyle.PATH) {
            return URI.create(this.scheme + "://" + this.endpoint + "/" + bucket + path);
        }
        if (this.addressStyle == S3Config.AddressStyle.VIRTUAL_HOSTED) {
            return URI.create(this.scheme + "://" + bucket + "." + this.endpoint + path);
        }
        throw new IllegalStateException("AddressStyle is not supported: " + this.addressStyle);
    }

    private String encodePath(String path) {
        var encodedPath = new StringBuilder();
        for (var pathSegment : path.split("/")) {
            if (!pathSegment.isEmpty()) {
                if (!encodedPath.isEmpty()) {
                    encodedPath.append("/");
                }
                encodedPath.append(encode(pathSegment));
            }
        }
        if (path.startsWith("/")) {
            encodedPath.insert(0, "/");
        }
        if (path.endsWith("/")) {
            encodedPath.append("/");
        }
        return encodedPath.toString();
    }

    private String encode(String str) {
        if (str == null) {
            return "";
        }
        return URLEncoder.encode(str, StandardCharsets.UTF_8
        );
    }
}
