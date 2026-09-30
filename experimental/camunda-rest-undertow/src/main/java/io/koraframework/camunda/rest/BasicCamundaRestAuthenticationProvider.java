package io.koraframework.camunda.rest;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HTTP Basic authentication against the engine {@link org.camunda.bpm.engine.IdentityService}.
 */
public final class BasicCamundaRestAuthenticationProvider implements CamundaRestAuthenticationProvider {

    private static final String BASIC_PREFIX = "Basic ";

    private final String challenge;

    public BasicCamundaRestAuthenticationProvider(String realm) {
        this.challenge = "Basic realm=\"" + realm + "\"";
    }

    @Override
    public AuthenticationResult authenticate(HttpServerExchange exchange, ProcessEngine processEngine) {
        var header = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
            return AuthenticationResult.unsuccessful();
        }

        final String credentials;
        try {
            credentials = new String(Base64.getDecoder().decode(header.substring(BASIC_PREFIX.length()).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return AuthenticationResult.unsuccessful();
        }

        var separator = credentials.indexOf(':');
        if (separator <= 0) {
            return AuthenticationResult.unsuccessful();
        }

        var userId = credentials.substring(0, separator);
        var password = credentials.substring(separator + 1);
        return processEngine.getIdentityService().checkPassword(userId, password)
            ? AuthenticationResult.successful(userId)
            : AuthenticationResult.unsuccessful(userId);
    }

    @Override
    public String challenge() {
        return challenge;
    }
}
