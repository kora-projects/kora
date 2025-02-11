package ru.tinkoff.kora.s3.client.impl;

import ru.tinkoff.kora.s3.client.S3Config;

import java.net.URI;
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

    public URI uri(String bucket, String path) {
        if (addressStyle == S3Config.AddressStyle.PATH) {
            return URI.create(scheme + "://" + endpoint + "/" + bucket + "/" + path);
        }
        if (addressStyle == S3Config.AddressStyle.VIRTUAL_HOSTED) {
            return URI.create(scheme + "://" + bucket + "." + endpoint + "/" + path);
        }
        throw new IllegalStateException("AddressStyle is not supported: " + addressStyle);
    }
}
