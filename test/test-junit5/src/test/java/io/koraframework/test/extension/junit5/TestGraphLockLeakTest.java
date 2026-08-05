package io.koraframework.test.extension.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

class TestGraphLockLeakTest {

    @KoraAppTest(TestApplication.class)
    private static final class AnnotationHolder {}

    private static Semaphore lock() throws Exception {
        var field = TestGraph.class.getDeclaredField("LOCK");
        field.setAccessible(true);
        return (Semaphore) field.get(null);
    }

    private static TestGraph failingGraph(Map<String, String> systemProperties) {
        var config = new KoraJUnit5Extension.TestClassMetadata.Config() {

            @Override
            public Map<String, String> systemProperties() {
                return systemProperties;
            }

            @Override
            public void setup(ApplicationGraphDraw graphDraw) throws IOException {
                throw new IOException("graph setup failed");
            }

            @Override
            public void cleanup() {}
        };

        var classMetadata = new KoraJUnit5Extension.TestClassMetadata(
                TestGraphLockLeakTest.class, List.of(), null, List.of(),
                AnnotationHolder.class.getAnnotation(KoraAppTest.class),
                TestInstance.Lifecycle.PER_METHOD,
                KoraJUnit5Extension.InitializeOrigin.CONSTRUCTOR,
                config, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

        var methodMetadata = new KoraJUnit5Extension.TestMethodMetadata(
                classMetadata, "test", Set.of(), Set.of());

        return new TestGraph(new ApplicationGraphDraw(TestApplication.class), methodMetadata, List.of(), List.of());
    }

    /**
     * A leaked permit does not fail the next initialization, it blocks it forever, so the attempt runs
     * on its own thread and the test asserts that the thread finished at all instead of hanging the build.
     */
    private static void initializeExpectingFailure(Map<String, String> systemProperties) throws Exception {
        var outcome = new AtomicReference<Throwable>();
        var attempt = new Thread(() -> {
            try {
                failingGraph(systemProperties).initialize();
            } catch (Throwable e) {
                outcome.set(e);
            }
        });
        attempt.setDaemon(true);
        attempt.start();
        attempt.join(30_000);

        assertThat(attempt.isAlive())
                .describedAs("initialization is still waiting for a permit leaked by an earlier one")
                .isFalse();
        assertThat(outcome.get()).isInstanceOf(ExtensionConfigurationException.class);
    }

    private static void assertPermitsSurviveFailedInitialization(Map<String, String> systemProperties) throws Exception {
        var permits = lock().availablePermits();
        try {
            initializeExpectingFailure(systemProperties);

            assertThat(lock().availablePermits()).isEqualTo(permits);
        } finally {
            // a leaked permit blocks every later @KoraAppTest in this JVM, including the other case below
            var leaked = permits - lock().availablePermits();
            if (leaked > 0) {
                lock().release(leaked);
            }
        }
    }

    @Test
    void permitsAreReleasedWhenInitializationFailsWithoutSystemProperties() throws Exception {
        assertPermitsSurviveFailedInitialization(Map.of());
    }

    @Test
    void permitsAreReleasedWhenInitializationFailsWithSystemProperties() throws Exception {
        assertPermitsSurviveFailedInitialization(Map.of("some.property", "value"));
    }
}
