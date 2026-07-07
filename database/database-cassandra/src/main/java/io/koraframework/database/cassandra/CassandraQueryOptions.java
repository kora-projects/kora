package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record CassandraQueryOptions(
    @Nullable ConsistencyLevel consistencyLevel,
    @Nullable ConsistencyLevel serialConsistencyLevel,
    @Nullable Integer pageSize,
    @Nullable Duration timeout,
    @Nullable Boolean idempotent,
    @Nullable Boolean tracing
) {
    public static final CassandraQueryOptions DEFAULT = new CassandraQueryOptions(null, null, null, null, null, null);
}
