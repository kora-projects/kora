package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import io.koraframework.database.common.QueryContext;
import io.koraframework.database.common.telemetry.DatabaseObservation;
import io.koraframework.database.common.telemetry.DatabaseTelemetry;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CassandraExecutorTest {

    private final DatabaseTelemetry telemetry = Mockito.mock(DatabaseTelemetry.class);
    private final DatabaseObservation observation = Mockito.mock(DatabaseObservation.class);
    private final CqlSession session = Mockito.mock(CqlSession.class);
    private final IllegalStateException error = new IllegalStateException("unconfigured table test");
    private final CassandraExecutor executor = new CassandraExecutor() {
        @Override
        public CqlSession currentSession() {
            return session;
        }

        @Override
        public DatabaseTelemetry telemetry() {
            return telemetry;
        }
    };

    CassandraExecutorTest() {
        when(telemetry.observe(any())).thenReturn(observation);
        when(observation.span()).thenReturn(Span.getInvalid());
        when(session.prepare(anyString())).thenThrow(error);
    }

    @Test
    void testQueryContextPrepareErrorIsObserved() {
        var queryContext = new QueryContext("SELECT * FROM test", "SELECT * FROM test");

        assertThatThrownBy(() -> executor.query(queryContext, (Function<PreparedStatement, Object>) ps -> ps)).isSameAs(error);
        verify(observation).observeError(error);
        verify(observation).end();
    }

    @Test
    void testCassandraQueryPrepareErrorIsObserved() {
        var query = CassandraQuery.named().cql("SELECT * FROM test WHERE id = :id").bind("id", 1).build();

        assertThatThrownBy(() -> executor.query(query, (Function<BoundStatement, Object>) bs -> bs)).isSameAs(error);
        verify(observation).observeError(error);
        verify(observation).end();
    }
}
