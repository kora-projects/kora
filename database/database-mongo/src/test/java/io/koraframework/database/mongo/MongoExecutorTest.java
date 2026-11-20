package io.koraframework.database.mongo;

import com.mongodb.TransactionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.koraframework.database.common.QueryContext;
import io.koraframework.database.common.telemetry.DatabaseObservation;
import io.koraframework.database.common.telemetry.DatabaseTelemetry;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MongoExecutorTest {

    @Test
    public void testQueryRecordsErrorInObservation() {
        var observation = new RecordingObservation();
        var executor = new StubExecutor(observation);
        var error = new AssertionError("boom");

        assertThatThrownBy(() -> executor.query(new QueryContext("q", "q", "find"), db -> {
            throw error;
        })).isSameAs(error);

        assertThat(observation.error).isSameAs(error);
        assertThat(observation.ended).isTrue();
    }

    private static final class RecordingObservation implements DatabaseObservation {
        private @Nullable Throwable error;
        private boolean ended;

        @Override
        public Span span() {
            return Span.getInvalid();
        }

        @Override
        public void end() {
            this.ended = true;
        }

        @Override
        public void observeError(Throwable e) {
            this.error = e;
        }

        @Override
        public void observeConnection() {
        }

        @Override
        public void observeStatement() {
        }
    }

    private record StubExecutor(DatabaseObservation observation) implements MongoExecutor {

        @Override
        public MongoClient client() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable MongoDatabase database() {
            return null;
        }

        @Override
        public MongoDatabase database(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DatabaseTelemetry telemetry() {
            return new DatabaseTelemetry() {
                @Override
                public MeterRegistry meterRegistry() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public DatabaseObservation observe(QueryContext query) {
                    return observation;
                }
            };
        }

        @Override
        public @Nullable ClientSession currentSession() {
            return null;
        }

        @Override
        public <T> T inTx(TransactionOptions options, Supplier<T> callback) {
            return callback.get();
        }
    }
}
