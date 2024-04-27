package ru.tinkoff.kora.bpmn.camunda7.rest.undertow;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import jakarta.ws.rs.core.Application;
import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.bpmn.camunda7.rest.Camunda7RestConfig;

import java.time.Duration;
import java.util.*;

final class UndertowCamundaRestHttpHandler implements HttpHandler, Lifecycle, Wrapped<HttpHandler> {

    private static final Logger logger = LoggerFactory.getLogger(UndertowCamundaRestHttpHandler.class);

    private final Application application;
    private final Camunda7RestConfig camundaRestConfig;

    private volatile DeploymentManager deploymentManager;
    private volatile HttpHandler realhttpHandler;

    UndertowCamundaRestHttpHandler(List<Application> applications, Camunda7RestConfig camundaRestConfig) {
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
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        this.realhttpHandler.handleRequest(exchange);
    }

    @Override
    public HttpHandler value() {
        return realhttpHandler;
    }

    @Override
    public void init() throws Exception {
        logger.debug("Camunda7 Rest Handler (Undertow) configuring...");
        final long started = System.nanoTime();

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

        root.addPrefixPath(camundaRestConfig.path(), deploymentManager.start());
        this.realhttpHandler = root;

        logger.info("Camunda7 Rest Handler (Undertow) configured in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
    }

    @Override
    public void release() throws Exception {
        deploymentManager.stop();
    }
}
