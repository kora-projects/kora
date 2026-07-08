package io.koraframework.database.cassandra.impl;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import io.koraframework.database.cassandra.CassandraQueryOptions;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record CassandraQueryOptionsImpl(
    @Nullable ConsistencyLevel consistencyLevel,
    @Nullable ConsistencyLevel serialConsistencyLevel,
    @Nullable Integer pageSize,
    @Nullable Duration timeout,
    @Nullable Boolean idempotent,
    @Nullable Boolean tracing
) implements CassandraQueryOptions {}
