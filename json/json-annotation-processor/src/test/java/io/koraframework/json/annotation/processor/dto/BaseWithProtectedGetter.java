package io.koraframework.json.annotation.processor.dto;

public class BaseWithProtectedGetter {
    private final String secret = "s";

    protected String getSecret() {
        return secret;
    }
}
