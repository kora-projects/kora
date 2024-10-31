package ru.tinkoff.kora.camunda.rest.undertow;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.SameThreadExecutor;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.core.Application;
import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.IoUtils;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestTelemetry;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestTelemetry.CamundaRestTelemetryContext;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestTracer;
import ru.tinkoff.kora.camunda.rest.undertow.UndertowPathMatcher.HttpMethodPath;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.cookie.Cookie;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.undertow.UndertowHttpHeaders;
import ru.tinkoff.kora.http.server.undertow.request.UndertowPublicApiRequest;
import ru.tinkoff.kora.openapi.management.OpenApiHttpServerHandler;
import ru.tinkoff.kora.openapi.management.RapidocHttpServerHandler;
import ru.tinkoff.kora.openapi.management.SwaggerUIHttpServerHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Internal
final class UndertowCamundaRestHttpHandler implements Lifecycle, Wrapped<HttpHandler> {

    private static final Logger logger = LoggerFactory.getLogger(UndertowCamundaRestHttpHandler.class);

    private final Application application;
    private final CamundaRestConfig camundaRestConfig;
    private final CamundaRestTelemetry telemetry;
    @Nullable
    private final CamundaRestTracer tracer;

    private volatile DeploymentManager deploymentManager;
    private volatile HttpHandler realhttpHandler;

    UndertowCamundaRestHttpHandler(List<Application> applications,
                                   CamundaRestConfig camundaRestConfig,
                                   CamundaRestTelemetry telemetry,
                                   @Nullable CamundaRestTracer tracer) {
        this.telemetry = telemetry;
        this.tracer = tracer;
        Set<Class<?>> classes = new HashSet<>();
        Map<String, Object> props = new HashMap<>();
        for (Application app : applications) {
            classes.addAll(app.getClasses());
            props.putAll(app.getProperties());
        }

        this.application = new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                return classes;
            }

            @Override
            public Map<String, Object> getProperties() {
                return props;
            }
        };

        this.camundaRestConfig = camundaRestConfig;
    }

    @Override
    public HttpHandler value() {
        return realhttpHandler;
    }

    @Override
    public void init() throws Exception {
        logger.debug("Camunda Rest Handler (Undertow) configuring...");
        final long started = TimeUtils.started();

        final ResteasyDeployment deployment = new ResteasyDeploymentImpl();
        deployment.setApplication(application);
        deployment.start();

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        var server = new UndertowJaxrsServer();
        final DeploymentInfo di = server.undertowDeployment(deployment);
        final ClassLoader classLoader = UndertowCamundaRestHttpHandler.class.getClassLoader();
        di.setClassLoader(classLoader);
        di.setContextPath(camundaRestConfig.path());
        di.setDeploymentName("ResteasyCamundaKora");
        deploymentManager = container.addDeployment(di);
        deploymentManager.deploy();

        var restPaths = getRestPaths(camundaRestConfig);
        var restMatcher = new UndertowPathMatcher(restPaths);

        var restHandler = deploymentManager.start();
        root.addPrefixPath(camundaRestConfig.path(), exchange -> {
            var match = restMatcher.getMatch(exchange.getRequestMethod().toString(), exchange.getRequestPath());
            final CamundaRestTelemetryContext telemetryContext;
            var context = Context.clear();
            var req = new UndertowPublicApiRequest(exchange, context);
            if (match.isPresent()) {
                telemetryContext = telemetry.get(req.scheme(), req.hostName(), req.method(), req.path(), match.get().pathTemplate(), req.headers(), req.queryParams(), req.body());
            } else {
                telemetryContext = telemetry.get(req.scheme(), req.hostName(), req.method(), req.path(), null, req.headers(), req.queryParams(), req.body());
            }

            restHandler.handleRequest(exchange.addExchangeCompleteListener((ex, nextListener) -> {
                var httpResultCode = HttpResultCode.fromStatusCode(ex.getStatusCode());
                telemetryContext.close(ex.getStatusCode(), httpResultCode, new UndertowHttpHeaders(ex.getResponseHeaders()), null);
                nextListener.proceed();
            }));
        });

        OpenApiHttpHandler openApiHttpHandler = new OpenApiHttpHandler(camundaRestConfig, telemetry, tracer);
        root.addPrefixPath("/", openApiHttpHandler);
        this.realhttpHandler = root;

        logger.info("Camunda Rest Handler (Undertow) configured in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() throws Exception {
        logger.debug("Camunda Rest Handler (Undertow) stopping...");
        final long started = TimeUtils.started();

        deploymentManager.stop();

        logger.info("Camunda Rest Handler (Undertow) stopped in {}", TimeUtils.tookForLogging(started));
    }

    private static List<HttpMethodPath> getRestPaths(CamundaRestConfig restConfig) {
        List<HttpMethodPath> restPaths = new ArrayList<>(400);
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/authorization"));
        restPaths.add(new HttpMethodPath("options", restConfig.path() + "/authorization"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/authorization/check"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/authorization/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/authorization/create"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/authorization/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/authorization/{id}"));
        restPaths.add(new HttpMethodPath("options", restConfig.path() + "/authorization/{id}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/authorization/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/batch"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/batch/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/batch/statistics"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/batch/statistics/count"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/batch/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/batch/{id}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/batch/{id}/suspended"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/condition"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-definition"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-definition/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-definition/key/{key}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-definition/key/{key}/diagram"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/decision-definition/key/{key}/evaluate"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/decision-definition/key/{key}/history-time-to-live"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-definition/key/{key}/tenant-id/{tenant-id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-definition/key/{key}/tenant-id/{tenant-id}/diagram"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/decision-definition/key/{key}/tenant-id/{tenant-id}/evaluate"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/decision-definition/key/{key}/tenant-id/{tenant-id}/history-time-to-live"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-definition/key/{key}/tenant-id/{tenant-id}/xml"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-definition/key/{key}/xml"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-definition/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-definition/{id}/diagram"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/decision-definition/{id}/evaluate"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/decision-definition/{id}/history-time-to-live"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-definition/{id}/xml"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-requirements-definition"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-requirements-definition/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-requirements-definition/key/{key}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-requirements-definition/key/{key}/diagram"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-requirements-definition/key/{key}/tenant-id/{tenant-id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-requirements-definition/key/{key}/tenant-id/{tenant-id}/diagram"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-requirements-definition/key/{key}/tenant-id/{tenant-id}/xml"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-requirements-definition/key/{key}/xml"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-requirements-definition/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-requirements-definition/{id}/diagram"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/decision-requirements-definition/{id}/xml"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/deployment"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/deployment/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/deployment/create"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/deployment/registered"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/deployment/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/deployment/{id}"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/deployment/{id}/redeploy"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/deployment/{id}/resources"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/deployment/{id}/resources/{resourceId}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/deployment/{id}/resources/{resourceId}/data"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/engine"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/event-subscription"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/event-subscription/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/execution"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/execution"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/execution/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/execution/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/execution/{id}"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/execution/{id}/create-incident"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/execution/{id}/localVariables"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/execution/{id}/localVariables"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/execution/{id}/localVariables/{varName}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/execution/{id}/localVariables/{varName}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/execution/{id}/localVariables/{varName}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/execution/{id}/localVariables/{varName}/data"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/execution/{id}/localVariables/{varName}/data"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/execution/{id}/messageSubscriptions/{messageName}"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/execution/{id}/messageSubscriptions/{messageName}/trigger"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/execution/{id}/signal"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/external-task"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/external-task"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/external-task/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/external-task/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/external-task/fetchAndLock"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/external-task/retries"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/external-task/retries-async"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/external-task/topic-names"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/external-task/{id}"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/external-task/{id}/bpmnError"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/external-task/{id}/complete"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/external-task/{id}/errorDetails"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/external-task/{id}/extendLock"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/external-task/{id}/failure"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/external-task/{id}/lock"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/external-task/{id}/priority"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/external-task/{id}/retries"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/external-task/{id}/unlock"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/filter"));
        restPaths.add(new HttpMethodPath("options", restConfig.path() + "/filter"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/filter/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/filter/create"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/filter/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/filter/{id}"));
        restPaths.add(new HttpMethodPath("options", restConfig.path() + "/filter/{id}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/filter/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/filter/{id}/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/filter/{id}/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/filter/{id}/list"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/filter/{id}/list"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/filter/{id}/singleResult"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/filter/{id}/singleResult"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/group"));
        restPaths.add(new HttpMethodPath("options", restConfig.path() + "/group"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/group"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/group/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/group/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/group/create"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/group/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/group/{id}"));
        restPaths.add(new HttpMethodPath("options", restConfig.path() + "/group/{id}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/group/{id}"));
        restPaths.add(new HttpMethodPath("options", restConfig.path() + "/group/{id}/members"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/group/{id}/members/{userId}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/group/{id}/members/{userId}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/activity-instance"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/activity-instance"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/activity-instance/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/activity-instance/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/activity-instance/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/batch"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/batch/cleanable-batch-report"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/batch/cleanable-batch-report/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/batch/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/batch/set-removal-time"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/history/batch/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/batch/{id}"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/cleanup"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/cleanup/configuration"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/cleanup/job"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/cleanup/jobs"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/decision-definition/cleanable-decision-instance-report"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/decision-definition/cleanable-decision-instance-report/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/decision-instance"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/decision-instance/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/decision-instance/delete"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/decision-instance/set-removal-time"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/decision-instance/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/decision-requirements-definition/{id}/statistics"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/detail"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/detail"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/detail/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/detail/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/detail/{id}/data"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/external-task-log"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/external-task-log"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/external-task-log/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/external-task-log/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/external-task-log/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/external-task-log/{id}/error-details"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/identity-link-log"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/identity-link-log/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/incident"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/incident/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/job-log"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/job-log"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/job-log/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/job-log/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/job-log/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/job-log/{id}/stacktrace"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/process-definition/cleanable-process-instance-report"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/process-definition/cleanable-process-instance-report/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/process-definition/{id}/statistics"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/process-instance"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/process-instance"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/process-instance/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/process-instance/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/process-instance/delete"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/process-instance/report"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/process-instance/set-removal-time"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/history/process-instance/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/process-instance/{id}"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/history/process-instance/{id}/variable-instances"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/task"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/task"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/task/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/task/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/task/report"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/user-operation"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/user-operation/count"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/history/user-operation/{operationId}/clear-annotation"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/history/user-operation/{operationId}/set-annotation"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/variable-instance"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/variable-instance"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/variable-instance/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/history/variable-instance/count"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/history/variable-instance/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/variable-instance/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/history/variable-instance/{id}/data"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/identity/groups"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/identity/password-policy"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/identity/password-policy"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/identity/verify"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/incident"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/incident/count"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/incident/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/incident/{id}"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/incident/{id}/annotation"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/incident/{id}/annotation"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/job"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/job"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/job-definition"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/job-definition"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/job-definition/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/job-definition/count"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/job-definition/suspended"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/job-definition/{id}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/job-definition/{id}/jobPriority"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/job-definition/{id}/retries"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/job-definition/{id}/suspended"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/job/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/job/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/job/retries"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/job/suspended"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/job/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/job/{id}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/job/{id}/duedate"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/job/{id}/duedate/recalculate"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/job/{id}/execute"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/job/{id}/priority"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/job/{id}/retries"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/job/{id}/stacktrace"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/job/{id}/suspended"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/message"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/metrics"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/metrics/task-worker"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/metrics/{metrics-name}/sum"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/migration/execute"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/migration/executeAsync"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/migration/generate"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/migration/validate"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/modification/execute"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/modification/executeAsync"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/count"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/process-definition/key/{key}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}/deployed-start-form"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}/diagram"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}/form-variables"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/process-definition/key/{key}/history-time-to-live"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}/rendered-form"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-definition/key/{key}/start"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}/startForm"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}/statistics"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-definition/key/{key}/submit-form"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/process-definition/key/{key}/suspended"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/process-definition/key/{key}/tenant-id/{tenant-id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}/tenant-id/{tenant-id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}/tenant-id/{tenant-id}/deployed-start-form"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}/tenant-id/{tenant-id}/diagram"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}/tenant-id/{tenant-id}/form-variables"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/process-definition/key/{key}/tenant-id/{tenant-id}/history-time-to-live"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}/tenant-id/{tenant-id}/rendered-form"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-definition/key/{key}/tenant-id/{tenant-id}/start"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}/tenant-id/{tenant-id}/startForm"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}/tenant-id/{tenant-id}/statistics"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-definition/key/{key}/tenant-id/{tenant-id}/submit-form"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/process-definition/key/{key}/tenant-id/{tenant-id}/suspended"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}/tenant-id/{tenant-id}/xml"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/key/{key}/xml"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/statistics"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/process-definition/suspended"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/process-definition/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/{id}/deployed-start-form"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/{id}/diagram"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/{id}/form-variables"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/process-definition/{id}/history-time-to-live"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/{id}/rendered-form"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-definition/{id}/restart"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-definition/{id}/restart-async"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-definition/{id}/start"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/{id}/startForm"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/{id}/static-called-process-definitions"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/{id}/statistics"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-definition/{id}/submit-form"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/process-definition/{id}/suspended"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-definition/{id}/xml"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-instance"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-instance"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-instance/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-instance/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-instance/delete"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-instance/delete-historic-query-based"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-instance/job-retries"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-instance/job-retries-historic-query-based"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-instance/message-async"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/process-instance/suspended"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-instance/suspended-async"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-instance/variables-async"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/process-instance/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-instance/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-instance/{id}/activity-instances"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-instance/{id}/comment"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-instance/{id}/modification"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-instance/{id}/modification-async"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/process-instance/{id}/suspended"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-instance/{id}/variables"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-instance/{id}/variables"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/process-instance/{id}/variables/{varName}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-instance/{id}/variables/{varName}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/process-instance/{id}/variables/{varName}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/process-instance/{id}/variables/{varName}/data"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/process-instance/{id}/variables/{varName}/data"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/schema/log"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/schema/log"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/signal"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/create"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/report/candidate-group-count"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/task/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/task/{id}"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/assignee"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/attachment"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/attachment/create"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/task/{id}/attachment/{attachmentId}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/attachment/{attachmentId}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/attachment/{attachmentId}/data"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/bpmnError"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/bpmnEscalation"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/claim"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/comment"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/comment/create"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/comment/{commentId}"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/complete"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/delegate"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/deployed-form"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/form"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/form-variables"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/identity-links"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/identity-links"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/identity-links/delete"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/localVariables"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/localVariables"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/task/{id}/localVariables/{varName}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/localVariables/{varName}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/task/{id}/localVariables/{varName}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/localVariables/{varName}/data"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/localVariables/{varName}/data"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/rendered-form"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/resolve"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/submit-form"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/unclaim"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/variables"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/variables"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/task/{id}/variables/{varName}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/variables/{varName}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/task/{id}/variables/{varName}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/task/{id}/variables/{varName}/data"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/task/{id}/variables/{varName}/data"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/telemetry/configuration"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/telemetry/configuration"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/telemetry/data"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/tenant"));
        restPaths.add(new HttpMethodPath("options", restConfig.path() + "/tenant"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/tenant/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/tenant/create"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/tenant/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/tenant/{id}"));
        restPaths.add(new HttpMethodPath("options", restConfig.path() + "/tenant/{id}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/tenant/{id}"));
        restPaths.add(new HttpMethodPath("options", restConfig.path() + "/tenant/{id}/group-members"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/tenant/{id}/group-members/{groupId}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/tenant/{id}/group-members/{groupId}"));
        restPaths.add(new HttpMethodPath("options", restConfig.path() + "/tenant/{id}/user-members"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/tenant/{id}/user-members/{userId}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/tenant/{id}/user-members/{userId}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/user"));
        restPaths.add(new HttpMethodPath("options", restConfig.path() + "/user"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/user/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/user/create"));
        restPaths.add(new HttpMethodPath("delete", restConfig.path() + "/user/{id}"));
        restPaths.add(new HttpMethodPath("options", restConfig.path() + "/user/{id}"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/user/{id}/credentials"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/user/{id}/profile"));
        restPaths.add(new HttpMethodPath("put", restConfig.path() + "/user/{id}/profile"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/user/{id}/unlock"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/variable-instance"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/variable-instance"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/variable-instance/count"));
        restPaths.add(new HttpMethodPath("post", restConfig.path() + "/variable-instance/count"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/variable-instance/{id}"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/variable-instance/{id}/data"));
        restPaths.add(new HttpMethodPath("get", restConfig.path() + "/version"));
        return restPaths;
    }

    private static final class OpenApiHttpHandler implements HttpHandler {

        private final UndertowPathMatcher pathMatcher;
        private final CamundaRestConfig restConfig;
        private final CamundaRestTelemetry telemetry;
        @Nullable
        private final CamundaRestTracer tracer;

        private final OpenApiHttpServerHandler openApiHandler;
        private final SwaggerUIHttpServerHandler swaggerUIHandler;
        private final RapidocHttpServerHandler rapidocHandler;

        private OpenApiHttpHandler(CamundaRestConfig restConfig,
                                   CamundaRestTelemetry telemetry,
                                   @Nullable CamundaRestTracer tracer) {
            this.restConfig = restConfig;
            this.telemetry = telemetry;
            this.tracer = tracer;

            final List<HttpMethodPath> openapiMethods = new ArrayList<>();
            var openapi = restConfig.openapi();
            if (openapi.file().size() == 1) {
                openapiMethods.add(new HttpMethodPath(HttpMethod.GET, openapi.endpoint()));
            } else {
                openapiMethods.add(new HttpMethodPath(HttpMethod.GET, openapi.endpoint() + "/{file}"));
            }
            openapiMethods.add(new HttpMethodPath(HttpMethod.GET, openapi.rapidoc().endpoint()));
            openapiMethods.add(new HttpMethodPath(HttpMethod.GET, openapi.swaggerui().endpoint()));
            this.pathMatcher = new UndertowPathMatcher(openapiMethods);

            this.openApiHandler = new OpenApiHttpServerHandler(openapi.file(), f -> {
                if ("/engine-rest".equals(restConfig.path())) {
                    String fileAsStr = new String(f, StandardCharsets.UTF_8);
                    return fileAsStr
                        .replace("8080", String.valueOf(restConfig.port()))
                        .getBytes(StandardCharsets.UTF_8);
                } else {
                    String fileAsStr = new String(f, StandardCharsets.UTF_8);
                    String newEnginePath = restConfig.path().startsWith("/")
                        ? restConfig.path().substring(1)
                        : restConfig.path();

                    return fileAsStr
                        .replace("engine-rest", newEnginePath)
                        .replace("8080", String.valueOf(restConfig.port()))
                        .getBytes(StandardCharsets.UTF_8);
                }
            });
            this.swaggerUIHandler = new SwaggerUIHttpServerHandler(openapi.endpoint(), openapi.swaggerui().endpoint(), openapi.file());
            this.rapidocHandler = new RapidocHttpServerHandler(openapi.endpoint(), openapi.rapidoc().endpoint(), openapi.file());
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) {
            exchange.dispatch(SameThreadExecutor.INSTANCE, () -> {
                final String requestPath = exchange.getRequestPath();
                var match = pathMatcher.getMatch(exchange.getRequestMethod().toString(), requestPath);
                if (match.isPresent()) {
                    Context context = Context.clear();

                    var req = new UndertowPublicApiRequest(exchange, context);
                    var telemetryContext = telemetry.get(req.scheme(), req.hostName(), req.method(), req.path(), match.get().pathTemplate(), req.headers(), req.queryParams(), req.body());

                    var fakeRequest = getFakeRequest(match.get());
                    var openapi = restConfig.openapi();
                    if (openapi.enabled() && requestPath.startsWith(openapi.endpoint())) {
                        var response = openApiHandler.apply(context, fakeRequest).toCompletableFuture();
                        sendResponse(exchange, response, context, telemetryContext);
                    } else if (openapi.swaggerui().enabled() && requestPath.startsWith(openapi.swaggerui().endpoint())) {
                        var response = swaggerUIHandler.apply(context, fakeRequest).toCompletableFuture();
                        sendResponse(exchange, response, context, telemetryContext);
                    } else if (openapi.rapidoc().enabled() && requestPath.startsWith(openapi.rapidoc().endpoint())) {
                        var response = rapidocHandler.apply(context, fakeRequest).toCompletableFuture();
                        sendResponse(exchange, response, context, telemetryContext);
                    } else {
                        telemetryContext.close(404, HttpResultCode.CLIENT_ERROR, HttpHeaders.empty(), null);
                        exchange.setStatusCode(404);
                        exchange.endExchange();
                    }
                } else {
                    exchange.setStatusCode(404);
                    exchange.endExchange();
                }
            });
        }

        private HttpServerRequest getFakeRequest(UndertowPathMatcher.Match match) {
            return new HttpServerRequest() {
                @Override
                public String method() {
                    return "";
                }

                @Override
                public String path() {
                    return "";
                }

                @Override
                public String route() {
                    return "";
                }

                @Override
                public HttpHeaders headers() {
                    return null;
                }

                @Override
                public List<Cookie> cookies() {
                    return List.of();
                }

                @Override
                public Map<String, ? extends Collection<String>> queryParams() {
                    return Map.of();
                }

                @Override
                public Map<String, String> pathParams() {
                    return restConfig.openapi().file().size() == 1
                        ? Map.of()
                        : Map.of("file", match.pathParameters().get("file"));
                }

                @Override
                public HttpBodyInput body() {
                    return null;
                }
            };
        }

        private void sendResponse(HttpServerExchange exchange,
                                  CompletableFuture<HttpServerResponse> responseFuture,
                                  Context context,
                                  CamundaRestTelemetryContext telemetryContext) {
            try {
                if (responseFuture.isDone()) {
                    sendResponse(exchange, responseFuture.join(), context, telemetryContext);
                } else {
                    responseFuture.whenComplete((httpServerResponse, throwable) -> {
                        if (httpServerResponse != null) {
                            sendResponse(exchange, httpServerResponse, context, telemetryContext);
                        } else if (throwable != null) {
                            var cause = (throwable instanceof CompletionException)
                                ? throwable.getCause()
                                : throwable;

                            sendException(exchange, context, telemetryContext, cause);
                        } else {
                            sendException(exchange, context, telemetryContext, new IllegalStateException("Illegal state: response future is empty"));
                        }
                    });
                }
            } catch (Throwable e) {
                var cause = (e instanceof CompletionException)
                    ? e.getCause()
                    : e;

                sendException(exchange, context, telemetryContext, cause);
            }
        }

        private void sendResponse(HttpServerExchange exchange,
                                  HttpServerResponse httpResponse,
                                  Context context,
                                  CamundaRestTelemetryContext telemetryContext) {
            var headers = httpResponse.headers();
            exchange.setStatusCode(httpResponse.code());
            if (tracer != null) {
                tracer.inject(context, exchange.getResponseHeaders(),
                    (carrier, key, value) -> carrier.add(HttpString.tryFromString(key), value)
                );
            }

            setHeaders(exchange.getResponseHeaders(), headers, null);
            var body = httpResponse.body();
            if (body == null) {
                exchange.addExchangeCompleteListener((e, nextListener) -> {
                    var httpResultCode = HttpResultCode.fromStatusCode(e.getStatusCode());
                    telemetryContext.close(e.getStatusCode(), httpResultCode, httpResponse.headers(), null);
                    nextListener.proceed();
                });
                exchange.endExchange();
                return;
            }

            var contentType = body.contentType();
            if (contentType != null) {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
            }

            var full = body.getFullContentIfAvailable();
            if (full != null) {
                this.sendBody(exchange, httpResponse, telemetryContext, full);
            } else {
                throw new IllegalStateException("Shouldn't happen");
            }
        }

        private void setHeaders(HeaderMap responseHeaders, HttpHeaders headers, @Nullable String contentType) {
            for (var header : headers) {
                var key = header.getKey();
                if (key.equals("server")) {
                    continue;
                }
                if (key.equals("content-type") && contentType != null) {
                    continue;
                }
                if (key.equals("content-length")) {
                    continue;
                }
                if (key.equals("transfer-encoding")) {
                    continue;
                }
                responseHeaders.addAll(HttpString.tryFromString(key), header.getValue());
            }
        }

        private void sendBody(HttpServerExchange exchange,
                              HttpServerResponse httpResponse,
                              CamundaRestTelemetryContext telemetryContext,
                              @Nullable ByteBuffer body) {
            var headers = httpResponse.headers();
            if (body == null || body.remaining() == 0) {
                exchange.setResponseContentLength(0);
                exchange.addExchangeCompleteListener((e, nextListener) -> {
                    var resultCode = HttpResultCode.fromStatusCode(exchange.getStatusCode());
                    telemetryContext.close(exchange.getStatusCode(), resultCode, headers, null);
                    nextListener.proceed();
                });
                exchange.endExchange();
            } else {
                exchange.setResponseContentLength(body.remaining());
                // io.undertow.io.DefaultIoCallback
                exchange.getResponseSender().send(body, new IoCallback() {
                    @Override
                    public void onComplete(HttpServerExchange exchange, Sender sender) {
                        sender.close(new IoCallback() {
                            @Override
                            public void onComplete(HttpServerExchange exchange, Sender sender) {
                                if (exchange.isComplete()) {
                                    var resultCode = HttpResultCode.fromStatusCode(exchange.getStatusCode());
                                    telemetryContext.close(exchange.getStatusCode(), resultCode, headers, null);
                                } else {
                                    exchange.addExchangeCompleteListener((e, nextListener) -> {
                                        var resultCode = HttpResultCode.fromStatusCode(e.getStatusCode());
                                        telemetryContext.close(e.getStatusCode(), resultCode, headers, null);
                                        nextListener.proceed();
                                    });
                                    exchange.endExchange();
                                }
                            }

                            @Override
                            public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                                try {
                                    exchange.endExchange();
                                } finally {
                                    var resultCode = HttpResultCode.fromStatusCode(exchange.getStatusCode());
                                    telemetryContext.close(exchange.getStatusCode(), resultCode, HttpHeaders.empty(), exception);
                                }
                            }
                        });
                    }

                    @Override
                    public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                        try {
                            exchange.endExchange();
                        } finally {
                            IoUtils.safeClose(exchange.getConnection());
                            var resultCode = HttpResultCode.fromStatusCode(exchange.getStatusCode());
                            telemetryContext.close(exchange.getStatusCode(), resultCode, HttpHeaders.empty(), exception);
                        }
                    }
                });
            }
        }

        private void sendException(HttpServerExchange exchange,
                                   Context context,
                                   CamundaRestTelemetryContext telemetryContext,
                                   Throwable error) {
            if (!(error instanceof HttpServerResponse rs)) {
                exchange.setStatusCode(500);
                exchange.getResponseSender().send(Objects.requireNonNullElse(error.getMessage(), "Unknown error"), StandardCharsets.UTF_8, new IoCallback() {
                    @Override
                    public void onComplete(HttpServerExchange exchange, Sender sender) {
                        telemetryContext.close(500, HttpResultCode.SERVER_ERROR, HttpHeaders.empty(), error);
                        IoCallback.END_EXCHANGE.onComplete(exchange, sender);
                    }

                    @Override
                    public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                        error.addSuppressed(exception);
                        telemetryContext.close(500, HttpResultCode.CONNECTION_ERROR, HttpHeaders.empty(), error);
                        IoCallback.END_EXCHANGE.onException(exchange, sender, exception);
                    }
                });
                exchange.endExchange();
            } else {
                sendResponse(exchange, rs, context, telemetryContext);
            }
        }
    }
}
