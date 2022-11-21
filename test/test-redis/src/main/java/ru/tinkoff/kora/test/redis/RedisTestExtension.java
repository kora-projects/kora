package ru.tinkoff.kora.test.redis;

import io.lettuce.core.api.sync.RedisServerCommands;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.lang.reflect.Method;
import java.time.Duration;

public class RedisTestExtension implements TestExecutionListener, ParameterResolver, AfterEachCallback {

    private static final Logger logger = LoggerFactory.getLogger(RedisTestExtension.class);
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(RedisContainer.class);

    private static volatile RedisParams envParams = null;
    private static volatile RedisContainer container = null;
    private static volatile RedisParams containerParams = null;

    private static synchronized void init() {
        if (envParams != null) {
            return;
        }

        if (containerParams != null) {
            return;
        }

        envParams = paramsFromEnv();
        if (containerParams != null) {
            logger.info("[REDIS] Waiting for Redis CI readiness...");
            awaitForReady(envParams);
            logger.info("[REDIS] Redis CI ready at {}", envParams);
            return;
        }

        logger.info("[REDIS] Starting Redis TestContainer...");
        container = new RedisContainer();
        container.start();
        containerParams = new RedisParams(container.getHost(), container.getPort());
        logger.info("[REDIS] Redis TestContainer ready at {}", containerParams);
    }

    private static synchronized void cleanup() {
        if (container != null) {
            logger.info("[REDIS] Stopping Redis TestContainer...");
            containerParams = null;
            container.stop();
            container = null;
            logger.info("[REDIS] Redis TestContainer stopped");
        }
    }

    private static RedisParams paramsFromEnv() {
        var host = System.getenv("TEST_REDIS_HOST");
        if (host == null)
            return null;

        var port = System.getenv("TEST_REDIS_PORT");
        if (port == null)
            return null;

        return new RedisParams(host, Integer.parseInt(port));
    }

    private RedisParams getParams() {
        init();
        return (envParams == null)
            ? containerParams
            : envParams;
    }

    private static void awaitForReady(RedisParams params) {
        Awaitility.await("REDIS")
            .pollDelay(Duration.ofSeconds(1))
            .pollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(60))
            .untilAsserted(() -> params.execute(RedisServerCommands::time));

        logger.info("[REDIS] Ready for execution...");
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getDeclaringExecutable() instanceof Method && parameterContext.getParameter().getType().equals(RedisParams.class);
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        init();
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        cleanup();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        var params = context.getStore(NAMESPACE).get(context.getRequiredTestMethod(), RedisParams.class);
        if (params != null) {
            context.getStore(NAMESPACE).remove(context.getRequiredTestMethod());
        }
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return extensionContext.getStore(NAMESPACE).getOrComputeIfAbsent(extensionContext.getRequiredTestMethod(), p -> getParams(), RedisParams.class);
    }
}
