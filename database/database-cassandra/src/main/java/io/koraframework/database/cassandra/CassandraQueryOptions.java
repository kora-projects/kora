package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import io.koraframework.database.cassandra.impl.CassandraQueryOptionsImpl;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public interface CassandraQueryOptions {

    CassandraQueryOptions DEFAULT = new CassandraQueryOptionsImpl(null, null, null, null, null, null);

    @Nullable
    ConsistencyLevel consistencyLevel();

    @Nullable
    ConsistencyLevel serialConsistencyLevel();

    @Nullable
    Integer pageSize();

    @Nullable
    Duration timeout();

    @Nullable
    Boolean idempotent();

    @Nullable
    Boolean tracing();
}
