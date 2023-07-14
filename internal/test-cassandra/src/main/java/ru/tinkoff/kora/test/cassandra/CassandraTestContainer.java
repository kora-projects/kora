package ru.tinkoff.kora.test.cassandra;

import org.junit.jupiter.api.extension.*;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public class CassandraTestContainer implements TestExecutionListener, ParameterResolver, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(CassandraTestContainer.class);
    private static volatile CassandraContainer<?> container = null;
    private static volatile CassandraParams params = null;

    public CassandraTestContainer() {
    }

    static synchronized void init() {
        if (params != null) {
            return;
        }
        var params = paramsFromEnv();
        if (params != null) {
            awaitForReady(params);
            CassandraTestContainer.params = params;
            return;
        }
        container = new CassandraContainer<>(DockerImageName.parse("cassandra").withTag("4.0.0"))
            .withExposedPorts(7000, 9042)
            .waitingFor(new HostPortWaitStrategy().withStartupTimeout(Duration.ofMinutes(10)));
        container.start();
        CassandraTestContainer.params = new CassandraParams(
            container.getHost(), container.getMappedPort(9042), "datacenter1", null, container.getUsername(), container.getPassword()
        );
    }

    public static CassandraParams getParams() {
        init();
        return Objects.requireNonNull(params);
    }

    private static void awaitForReady(CassandraParams params) {
        var start = System.currentTimeMillis();
        Exception ex = null;
        while (System.currentTimeMillis() - start <= 120000) {
            try (var c = params.getSession()) {
                return;
            } catch (Exception e) {
                ex = e;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    throw e;
                }
            }
        }
        throw new RuntimeException("Session await failed: waited " + (System.currentTimeMillis() - start) + "ms", ex);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (container != null) {
            container.stop();
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        var params = context.getStore(NAMESPACE).get(context.getRequiredTestMethod(), CassandraParams.class);
        if (params != null) {
            getParams().execute("DROP KEYSPACE IF EXISTS " + params.keyspace());
            context.getStore(NAMESPACE).remove(context.getRequiredTestMethod());
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getDeclaringExecutable() instanceof Method && parameterContext.getParameter().getType().equals(CassandraParams.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return extensionContext.getStore(NAMESPACE).getOrComputeIfAbsent(extensionContext.getRequiredTestMethod(), p -> {
            try (var connection = getParams().getSession()) {
                var dbName = "TESTDB_" + UUID.randomUUID().toString().replace('-', '_');
                connection.execute(connection.prepare("DROP KEYSPACE IF EXISTS " + dbName).bind().setTimeout(Duration.ofMinutes(3)));
                connection.execute(connection.prepare("CREATE KEYSPACE " + dbName + " WITH REPLICATION = { 'class' : 'org.apache.cassandra.locator.SimpleStrategy', 'replication_factor': '1' } AND DURABLE_WRITES = true").bind().setTimeout(Duration.ofMinutes(3)));
                return getParams().witKeyspace(dbName);
            }
        }, CassandraParams.class);
    }

    @Nullable
    private static CassandraParams paramsFromEnv() {
        var testCassandraHost = System.getenv("TEST_CASSANDRA_HOST");
        if (testCassandraHost == null) return null;
        var testCassandraPort = System.getenv("TEST_CASSANDRA_PORT");
        if (testCassandraPort == null) return null;
        var testCassandraDc = System.getenv("TEST_CASSANDRA_DC");
        if (testCassandraDc == null) return null;
        var testCassandraKeyspace = System.getenv("TEST_CASSANDRA_KEYSPACE");
        var testCassandraUsername = System.getenv("TEST_CASSANDRA_USERNAME");
        var testCassandraPassword = System.getenv("TEST_CASSANDRA_PASSWORD");
        return new CassandraParams(testCassandraHost, Integer.parseInt(testCassandraPort), testCassandraDc, testCassandraKeyspace, testCassandraUsername, testCassandraPassword);
    }
}
