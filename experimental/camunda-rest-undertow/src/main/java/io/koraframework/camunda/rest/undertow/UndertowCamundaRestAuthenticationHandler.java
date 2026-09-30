package io.koraframework.camunda.rest.undertow;

import io.koraframework.camunda.rest.CamundaRestAuthenticationProvider;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.Tenant;

final class UndertowCamundaRestAuthenticationHandler implements HttpHandler {

    private final CamundaRestAuthenticationProvider provider;
    private final boolean corsEnabled;
    private final HttpHandler next;

    UndertowCamundaRestAuthenticationHandler(CamundaRestAuthenticationProvider provider, boolean corsEnabled, HttpHandler next) {
        this.provider = provider;
        this.corsEnabled = corsEnabled;
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (corsEnabled && isCorsPreflight(exchange)) {
            next.handleRequest(exchange);
            return;
        }

        var engine = ProcessEngines.getDefaultProcessEngine();
        if (engine == null) {
            exchange.setStatusCode(503);
            exchange.endExchange();
            return;
        }

        var result = provider.authenticate(exchange, engine);
        if (result == null || !result.isAuthenticated() || result.getAuthenticatedUser() == null) {
            var challenge = provider.challenge();
            if (challenge != null) {
                exchange.getResponseHeaders().put(Headers.WWW_AUTHENTICATE, challenge);
            }
            exchange.setStatusCode(401);
            exchange.endExchange();
            return;
        }

        var identityService = engine.getIdentityService();
        var userId = result.getAuthenticatedUser();
        var groups = result.getGroups() != null
            ? result.getGroups()
            : identityService.createGroupQuery().groupMember(userId).list().stream().map(Group::getId).toList();
        var tenants = result.getTenants() != null
            ? result.getTenants()
            : identityService.createTenantQuery().userMember(userId).includingGroupsOfUser(true).list().stream().map(Tenant::getId).toList();

        identityService.setAuthentication(userId, groups, tenants);
        try {
            next.handleRequest(exchange);
        } finally {
            identityService.clearAuthentication();
        }
    }

    private static boolean isCorsPreflight(HttpServerExchange exchange) {
        return Methods.OPTIONS.equals(exchange.getRequestMethod())
            && exchange.getRequestHeaders().contains(Headers.ORIGIN)
            && exchange.getRequestHeaders().contains("Access-Control-Request-Method");
    }
}
