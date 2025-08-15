package ru.tinkoff.kora.test.extension.junit5;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mockito;
import org.mockito.internal.junit.TestFinishedEvent;
import org.mockito.internal.junit.UniversalTestListener;
import org.mockito.mock.MockCreationSettings;
import org.mockito.plugins.MockitoLogger;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MockitoUnusedStubbingReporter {

    private final GraphMockitoContext mockitoContext;
    private final Strictness strictness;

    public MockitoUnusedStubbingReporter(GraphMockitoContext mockitoContext, Strictness strictness) {
        this.mockitoContext = mockitoContext;
        this.strictness = strictness;
    }

    public void reportUnused(ExtensionContext context) {
        if (mockitoContext.isEmpty()) {
            return;
        }

        var listener = new UniversalTestListener(strictness, new Slf4jReporterLogger());
        for (var mockEntry : mockitoContext.mocksMap().entrySet()) {
            listener.onMockCreated(mockEntry.getKey(), mockEntry.getValue());
        }
        for (var spy : mockitoContext.spySet()) {
            listener.onMockCreated(spy, (MockCreationSettings<?>) Mockito.withSettings());
        }

        listener.testFinished(
            new TestFinishedEvent() {
                @Override
                public Throwable getFailure() {
                    return context.getExecutionException().orElse(null);
                }

                @Override
                public String getTestName() {
                    return context.getRequiredTestInstance().getClass().getCanonicalName();
                }
            });
    }

    private static class Slf4jReporterLogger implements MockitoLogger {

        private static final Logger logger = LoggerFactory.getLogger(MockitoUnusedStubbingReporter.class);

        // warn also here cause MockitoLogger#warn is never used in Mockito actually...
        @Override
        public void log(Object o) {
            logger.warn(String.valueOf(o));
        }

        @Override
        public void warn(Object what) {
            logger.warn(String.valueOf(what));
        }
    }
}
