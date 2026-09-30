package io.koraframework.camunda.rest.undertow;

import io.koraframework.camunda.rest.BasicCamundaRestAuthenticationProvider;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.Tenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UndertowCamundaRestAuthenticationHandlerTests {

    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicBoolean nextCalled = new AtomicBoolean();

    private ProcessEngine engine;
    private IdentityService identityService;
    private Undertow undertow;

    @BeforeEach
    void setUp() {
        identityService = mock(IdentityService.class, RETURNS_DEEP_STUBS);
        engine = mock(ProcessEngine.class);
        when(engine.getName()).thenReturn(ProcessEngines.NAME_DEFAULT);
        when(engine.getIdentityService()).thenReturn(identityService);
        ProcessEngines.init();
        ProcessEngines.registerProcessEngine(engine);
    }

    @AfterEach
    void tearDown() {
        if (undertow != null) {
            undertow.stop();
        }
        ProcessEngines.unregister(engine);
    }

    @Test
    void missingCredentialsRejectedWithChallenge() throws Exception {
        var port = start(false);

        var response = client.send(request(port).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue("Basic realm=\"camunda\"");
        assertThat(nextCalled).isFalse();
    }

    @Test
    void invalidCredentialsRejected() throws Exception {
        when(identityService.checkPassword("demo", "wrong")).thenReturn(false);
        var port = start(false);

        var response = client.send(request(port).header("Authorization", basic("demo", "wrong")).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(nextCalled).isFalse();
    }

    @Test
    void validCredentialsSetEngineAuthentication() throws Exception {
        var group = mock(Group.class);
        when(group.getId()).thenReturn("camunda-admin");
        var tenant = mock(Tenant.class);
        when(tenant.getId()).thenReturn("tenant");
        when(identityService.checkPassword("demo", "demo")).thenReturn(true);
        when(identityService.createGroupQuery().groupMember("demo").list()).thenReturn(List.of(group));
        when(identityService.createTenantQuery().userMember("demo").includingGroupsOfUser(true).list()).thenReturn(List.of(tenant));
        var port = start(false);

        var response = client.send(request(port).header("Authorization", basic("demo", "demo")).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(nextCalled).isTrue();
        var inOrder = inOrder(identityService);
        inOrder.verify(identityService).setAuthentication("demo", List.of("camunda-admin"), List.of("tenant"));
        inOrder.verify(identityService).clearAuthentication();
    }

    @Test
    void corsPreflightSkipsAuthenticationWhenCorsEnabled() throws Exception {
        var port = start(true);

        var response = client.send(request(port)
            .header("Origin", "http://example.com")
            .header("Access-Control-Request-Method", "GET")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(nextCalled).isTrue();
    }

    private int start(boolean corsEnabled) {
        HttpHandler next = exchange -> {
            nextCalled.set(true);
            exchange.setStatusCode(200);
            exchange.endExchange();
        };
        var handler = new UndertowCamundaRestAuthenticationHandler(new BasicCamundaRestAuthenticationProvider("camunda"), corsEnabled, next);
        undertow = Undertow.builder()
            .addHttpListener(0, "localhost", new BlockingHandler(handler))
            .build();
        undertow.start();
        return ((InetSocketAddress) undertow.getListenerInfo().getFirst().getAddress()).getPort();
    }

    private static HttpRequest.Builder request(int port) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/engine-rest/engine"));
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
