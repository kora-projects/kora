package ru.tinkoff.kora.test.postgres;

import org.junit.jupiter.api.extension.*;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public final class PostgresTestContainer implements TestExecutionListener, ParameterResolver, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(PostgresTestContainer.class);
    private static volatile PostgreSQLContainer<?> container = null;
    private static volatile PostgresParams params = null;

    public static PostgresParams getParams() {
        init();
        return Objects.requireNonNull(params);
    }

    public PostgresTestContainer() {
    }

    private static synchronized void init() {
        if (params != null) {
            return;
        }
        params = paramsFromEnv();
        if (params != null) {
            awaitForReady(params);
            return;
        }
        container = new PostgreSQLContainer<>("postgres:14");
        container.start();
        try (var c = container.createConnection("?")) {
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        params = new PostgresParams(container.getHost(), container.getMappedPort(5432), container.getDatabaseName(), container.getUsername(), container.getPassword());
    }

    private static void awaitForReady(PostgresParams params) {
        var start = System.currentTimeMillis();
        var lastError = (Exception) null;
        while (System.currentTimeMillis() - start < 60000) {
            try (var c = params.createConnection()) {
                c.isValid(1000);
                return;
            } catch (SQLException e) {
                lastError = e;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (lastError == null) {
            lastError = new TimeoutException("Timeout on waiting for db");
        }
        throw new RuntimeException(lastError);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getDeclaringExecutable() instanceof Method && parameterContext.getParameter().getType().equals(PostgresParams.class);
    }

    @Nullable
    private static PostgresParams paramsFromEnv() {
        var testPostgresHost = System.getenv("TEST_POSTGRESQL_HOST");
        if (testPostgresHost == null) return null;
        var testPostgresPort = System.getenv("TEST_POSTGRESQL_PORT");
        if (testPostgresPort == null) return null;
        var testPostgresDb = System.getenv("TEST_POSTGRESQL_DB");
        if (testPostgresDb == null) return null;
        var testPostgresUsername = System.getenv("TEST_POSTGRESQL_USERNAME");
        if (testPostgresUsername == null) return null;
        var testPostgresPassword = System.getenv("TEST_POSTGRESQL_PASSWORD");
        if (testPostgresPassword == null) return null;
        return new PostgresParams(testPostgresHost, Integer.parseInt(testPostgresPort), testPostgresDb, testPostgresUsername, testPostgresPassword);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (container != null) {
            container.stop();
        }
    }

    public static String randomName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace('-', '_').toLowerCase();
    }


    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return extensionContext.getStore(NAMESPACE).getOrComputeIfAbsent(extensionContext.getRequiredTestMethod(), p -> {
            var params = getParams();
            var dbName = randomName("db");

            params.execute("CREATE DATABASE " + dbName + " ALLOW_CONNECTIONS true");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return params.withDb(dbName.toLowerCase());
        }, PostgresParams.class);
    }


    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        var params = context.getStore(NAMESPACE).get(context.getRequiredTestMethod(), PostgresParams.class);
        if (params != null) {
            getParams().execute("DROP DATABASE IF EXISTS " + params.db() + " WITH (FORCE);");
            context.getStore(NAMESPACE).remove(context.getRequiredTestMethod());
        }
    }
}
