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
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.bpmn.camunda7.rest.CamundaRestConfig;

final class CamundaRestUndertowHttpHandler implements HttpHandler, Lifecycle, Wrapped<HttpHandler> {

    private final Application application;
    private final CamundaRestConfig camundaRestConfig;

    private volatile DeploymentManager deploymentManager;
    private volatile HttpHandler realhttpHandler;

    CamundaRestUndertowHttpHandler(Application application, CamundaRestConfig camundaRestConfig) {
        this.application = application;
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
        final ResteasyDeployment deployment = new ResteasyDeploymentImpl();
        deployment.setApplication(application);
        deployment.start();

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        var server = new UndertowJaxrsServer();
        final DeploymentInfo di = server.undertowDeployment(deployment);
        final ClassLoader classLoader = application.getClass().getClassLoader();
        di.setClassLoader(classLoader);
        di.setContextPath(camundaRestConfig.path());
        di.setDeploymentName("ResteasyCamundaKora");

        deploymentManager = container.addDeployment(di);
        deploymentManager.deploy();

        root.addPrefixPath(camundaRestConfig.path(), deploymentManager.start());
        this.realhttpHandler = root;
    }

    @Override
    public void release() throws Exception {
        deploymentManager.stop();
    }
}
