package ru.tinkoff.kora.bpmn.camunda7.rest;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ServletContainer;
import org.camunda.bpm.engine.rest.impl.CamundaRestResources;
import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.jboss.resteasy.spi.ResteasyDeployment;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.GraphInterceptor;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

public interface CamundaRestModule {

    @Tag(PublicApiHandler.class)
    default GraphInterceptor<HttpHandler> camundaRestHttpHandler(@Tag(CamundaRestModule.class) HttpHandler camundaHttpHandler,
                                                                 All<HttpServerRequestHandler> handlers,
                                                                 HttpServerConfig config) {
        return new GraphInterceptor<>() {

            @Override
            public HttpHandler init(HttpHandler value) {
                return new CamundaRestHttpHandler(camundaHttpHandler, value, handlers, config);
            }

            @Override
            public HttpHandler release(HttpHandler value) {
                return value;
            }
        };
    }

    private static Application camundaRestApplication() {
        return new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                var set = new HashSet<Class<?>>();
                set.addAll(CamundaRestResources.getResourceClasses());
                set.addAll(CamundaRestResources.getConfigurationClasses());
                set.add(AllowAllCorsFilter.class);
                return set;
            }
        };
    }

    @Tag(CamundaRestModule.class)
    default HttpHandler camundaRestHttpHandler() {
        try {
            Application application = camundaRestApplication();
            ResteasyDeployment resteasyDeployment = new ResteasyDeploymentImpl();
            resteasyDeployment.setApplication(application);
            resteasyDeployment.start();

            ClassLoader classLoader = application.getClass().getClassLoader();

            String path = "/engine-resty";

            final PathHandler root = new PathHandler();
            final ServletContainer container = ServletContainer.Factory.newInstance();

            var server = new UndertowJaxrsServer();
//            server.setRootResourcePath(path);
            DeploymentInfo di = server.undertowDeployment(resteasyDeployment);
            populateDeploymentInfo(di, classLoader, path);

            var deployment = new ResteasyDeploymentImpl();
            deployment.setApplication(application);
            deployment.setInjectorFactoryClass("org.jboss.resteasy.cdi.CdiInjectorFactory");
            var manager = container.addDeployment(configureDefaults(di, deployment));
            manager.deploy();

            root.addPrefixPath(path, manager.start());

            return root;
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
    }

    @Root
    default Lifecycle camundaRestServer() {
        return new Lifecycle() {
            private volatile UndertowJaxrsServer server;

            @Override
            public void init() {
                server = new UndertowJaxrsServer();
                Application application = camundaRestApplication();
                server.setHostname("0.0.0.0");
                server.setPort(8090);
                server.deploy(application);
                server.start();
            }

            @Override
            public void release() {
                server.stop();
            }
        };
    }

    private static DeploymentInfo configureDefaults(final DeploymentInfo deploymentInfo, final ResteasyDeployment deployment) {
        // Check for a default multipart config. If not found and the application class is set, check there.
        if (deploymentInfo.getDefaultMultipartConfig() == null) {
            final Application application = deployment.getApplication();
            if (application != null) {
                final MultipartConfig multipartConfig = application.getClass().getAnnotation(MultipartConfig.class);
                if (multipartConfig != null) {
                    deploymentInfo.setDefaultMultipartConfig(new MultipartConfigElement(multipartConfig));
                }
            }
        }
        return deploymentInfo;
    }

    private static void populateDeploymentInfo(DeploymentInfo di, ClassLoader clazzLoader, String contextPath) {
        di.setClassLoader(clazzLoader);
        di.setContextPath(contextPath);
        di.setDeploymentName("ResteasyCamundaKora");
    }
}
