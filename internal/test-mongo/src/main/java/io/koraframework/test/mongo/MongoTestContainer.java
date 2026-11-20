package io.koraframework.test.mongo;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

/**
 * Starts a single-node replica set, which is what MongoDB requires for transactions.
 */
public class MongoTestContainer implements TestExecutionListener, ParameterResolver, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(MongoTestContainer.class);

    private static volatile MongoDBContainer container = null;
    private static volatile MongoParams params = null;

    public MongoTestContainer() {
    }

    static synchronized void init() {
        if (params != null) {
            return;
        }
        var fromEnv = paramsFromEnv();
        if (fromEnv != null) {
            MongoTestContainer.params = fromEnv;
            return;
        }
        container = new MongoDBContainer(DockerImageName.parse("mongo:8.0")).withReplicaSet();
        container.start();
        MongoTestContainer.params = new MongoParams(container.getConnectionString(), "test");
    }

    public static MongoParams getParams() {
        init();
        return Objects.requireNonNull(params);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (container != null) {
            container.stop();
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        var testParams = context.getStore(NAMESPACE).get(context.getRequiredTestMethod(), MongoParams.class);
        if (testParams != null) {
            try (var client = testParams.client()) {
                client.getDatabase(testParams.database()).drop();
            }
            context.getStore(NAMESPACE).remove(context.getRequiredTestMethod());
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getDeclaringExecutable() instanceof Method
            && parameterContext.getParameter().getType().equals(MongoParams.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return extensionContext.getStore(NAMESPACE).getOrComputeIfAbsent(
            extensionContext.getRequiredTestMethod(),
            method -> getParams().withDatabase("testdb_" + UUID.randomUUID().toString().replace('-', '_')),
            MongoParams.class);
    }

    @Nullable
    private static MongoParams paramsFromEnv() {
        var connectionString = System.getenv("TEST_MONGO_URI");
        if (connectionString == null) {
            return null;
        }
        var database = System.getenv("TEST_MONGO_DATABASE");
        return new MongoParams(connectionString, database == null ? "test" : database);
    }
}
