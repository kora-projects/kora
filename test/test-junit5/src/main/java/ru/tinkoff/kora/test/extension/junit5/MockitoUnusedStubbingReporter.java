package ru.tinkoff.kora.test.extension.junit5;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.internal.junit.TestFinishedEvent;
import org.mockito.internal.junit.UniversalTestListener;
import org.mockito.mock.MockCreationSettings;
import org.mockito.plugins.MockitoLogger;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import static org.mockito.Mockito.withSettings;

public class MockitoUnusedStubbingReporter {
    private final Collection<?> unused;
    private final Strictness strictness;

    public MockitoUnusedStubbingReporter(Collection<?> unused, Strictness strictness) {
        this.unused = unused;
        this.strictness = strictness;
    }

    @SuppressWarnings("rawtypes")
    public void reportUnused(ExtensionContext context) {
        if (unused.isEmpty()) {
            return;
        }

        var listener = new UniversalTestListener(strictness, new ReporterLogger());

        for (Object mock : unused) {
            listener.onMockCreated(mock, (MockCreationSettings)withSettings());
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

    static class ReporterLogger implements MockitoLogger {
        private static final Logger logger = LoggerFactory.getLogger(ReporterLogger.class);

        @Override
        public void log(Object o) {
            logger.info(String.valueOf(o));
        }
    }
}
