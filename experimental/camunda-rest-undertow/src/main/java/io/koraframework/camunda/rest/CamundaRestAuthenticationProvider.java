package io.koraframework.camunda.rest;

import io.undertow.server.HttpServerExchange;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;
import org.jspecify.annotations.Nullable;

/**
 * Authenticates Camunda REST API requests when {@code camunda.rest.auth.enabled} is set.
 * <p>
 * The authenticated user, groups and tenants are set to {@link org.camunda.bpm.engine.IdentityService#setAuthentication}
 * for the duration of the request, so engine authorization checks apply when {@code camunda.engine.bpmn.authorizationEnabled} is set.
 * When {@link AuthenticationResult#getGroups()} or {@link AuthenticationResult#getTenants()} is null,
 * they are resolved through the engine {@link org.camunda.bpm.engine.IdentityService}.
 * <p>
 * HTTP Basic authentication against the engine {@link org.camunda.bpm.engine.IdentityService} is used by default.
 */
public interface CamundaRestAuthenticationProvider {

    /**
     * @return Authentication result, {@link AuthenticationResult#unsuccessful()} when credentials are missing or invalid.
     */
    AuthenticationResult authenticate(HttpServerExchange exchange, ProcessEngine processEngine);

    /**
     * @return {@code WWW-Authenticate} header value sent with the 401 response, no header is sent when null.
     */
    @Nullable
    default String challenge() {
        return null;
    }
}
