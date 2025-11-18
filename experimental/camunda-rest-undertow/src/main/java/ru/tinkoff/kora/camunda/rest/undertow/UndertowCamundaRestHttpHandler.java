package ru.tinkoff.kora.camunda.rest.undertow;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.util.AttachmentKey;
import jakarta.ws.rs.core.Application;
import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestTelemetry;
import ru.tinkoff.kora.camunda.rest.undertow.UndertowPathMatcher.HttpMethodPath;
import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.common.telemetry.OpentelemetryContext;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.http.server.undertow.UndertowContext;
import ru.tinkoff.kora.http.server.undertow.UndertowExchangeProcessor;
import ru.tinkoff.kora.http.server.undertow.UndertowHttpServer;

import java.util.*;
import java.util.concurrent.ExecutorService;

final class UndertowCamundaRestHttpHandler implements Lifecycle, Wrapped<HttpHandler> {

    private static final Logger logger = LoggerFactory.getLogger(UndertowCamundaRestHttpHandler.class);
    private final AttachmentKey<ExecutorService> executorServiceAttachmentKey = AttachmentKey.create(ExecutorService.class);

    private final Application application;
    private final CamundaRestConfig camundaRestConfig;
    private final CamundaRestTelemetry telemetry;

    private volatile DeploymentManager deploymentManager;
    private volatile HttpHandler realhttpHandler;

    UndertowCamundaRestHttpHandler(List<Application> applications,
                                   CamundaRestConfig camundaRestConfig,
                                   CamundaRestTelemetry telemetry) {
        this.telemetry = telemetry;
        var classes = new HashSet<Class<?>>();
        var props = new HashMap<String, Object>();
        for (var app : applications) {
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
        var started = TimeUtils.started();

        var deployment = new ResteasyDeploymentImpl();
        deployment.setApplication(application);
        deployment.start();

        var root = new PathHandler();
        var container = ServletContainer.Factory.newInstance();

        var server = new UndertowJaxrsServer();
        var di = server.undertowDeployment(deployment);

        var classLoader = UndertowCamundaRestHttpHandler.class.getClassLoader();
        di.setClassLoader(classLoader);
        di.setContextPath(camundaRestConfig.path());
        di.setDeploymentName("ResteasyCamundaKora");
        deploymentManager = container.addDeployment(di);
        deploymentManager.deploy();

        var restPaths = getRestPaths(camundaRestConfig);
        var restMatcher = new UndertowPathMatcher(restPaths);

        var restHandler = deploymentManager.start();
        root.addPrefixPath(camundaRestConfig.path(), exchange -> {
            var rootCtx = W3CTraceContextPropagator.getInstance().extract(io.opentelemetry.context.Context.root(), exchange.getRequestHeaders(), UndertowExchangeProcessor.HttpServerExchangeMapGetter.INSTANCE);
            ScopedValue
                .where(UndertowContext.VALUE, new UndertowContext(exchange))
                .where(ru.tinkoff.kora.logging.common.MDC.VALUE, new ru.tinkoff.kora.logging.common.MDC())
                .where(OpentelemetryContext.VALUE, rootCtx)
                .call(() -> {
                    MDC.clear();
                    var match = restMatcher.getMatch(exchange.getRequestMethod().toString(), exchange.getRequestPath());
                    var pathTemplate = match == null ? null : match.pathTemplate();
                    var observation = this.telemetry.observe(exchange, pathTemplate);
                    var ctx = rootCtx.with(observation.span());
                    W3CTraceContextPropagator.getInstance().inject(
                        ctx,
                        exchange.getResponseHeaders(),
                        UndertowExchangeProcessor.HttpServerExchangeMapGetter.INSTANCE
                    );
                    exchange.addExchangeCompleteListener((e, nextListener) -> {
                        observation.observeResponseCode(e.getStatusCode());
                        observation.end();
                        nextListener.proceed();
                    });

                    ScopedValue
                        .where(OpentelemetryContext.VALUE, ctx)
                        .where(Observation.VALUE, observation)
                        .call(() -> {
                            try {
                                restHandler.handleRequest(exchange);
                                return null;
                            } catch (Throwable t) {
                                observation.observeError(t);
                                throw t;
                            }
                        });
                    return null;
                });
        });

        root.addPrefixPath("/", new OpenApiHttpHandler(camundaRestConfig));
        this.realhttpHandler = exchange -> {
            var executor = UndertowHttpServer.getOrCreateExecutor(exchange, executorServiceAttachmentKey, "camunda-rest");
            exchange.dispatch(executor, root);
        };

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

}
