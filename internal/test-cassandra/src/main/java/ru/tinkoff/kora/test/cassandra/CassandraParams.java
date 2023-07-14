package ru.tinkoff.kora.test.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultProgrammaticDriverConfigLoaderBuilder;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.function.Function;

import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.*;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.CONNECTION_WARN_INIT_ERROR;

public record CassandraParams(String host, int port, String dc, String keyspace, String username, String password) {
    public CqlSession getSession() {
        var configLoader = new DefaultProgrammaticDriverConfigLoaderBuilder();
         configLoader.withDuration(CONNECTION_CONNECT_TIMEOUT, Duration.ofMinutes(1));
         configLoader.withDuration(CONNECTION_INIT_QUERY_TIMEOUT, Duration.ofMinutes(1));
         configLoader.withDuration(CONNECTION_SET_KEYSPACE_TIMEOUT, Duration.ofMinutes(1));

        var b = new CqlSessionBuilder()
            .withConfigLoader(configLoader.build())
            .withLocalDatacenter(dc)
            .addContactPoint(new InetSocketAddress(host, port))
            .withKeyspace(keyspace);
        if (username != null && password != null) {
            b.withAuthCredentials(username, password);
        }
        return b.build();
    }

    public void execute(String sql) {
        try (var connection = getSession()) {
            connection.execute(connection.prepare(sql).bind().setTimeout(Duration.ofMinutes(3)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T query(String sql, Function<ResultSet, T> extractor) {
        try (var connection = getSession()) {
            var rs = connection.execute(connection.prepare(sql).bind().setTimeout(Duration.ofMinutes(3)));
            return extractor.apply(rs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public CassandraParams witKeyspace(String keyspace) {
        return new CassandraParams(
            host, port, dc, keyspace, username, password
        );
    }
}
