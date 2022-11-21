package ru.tinkoff.kora.database.vertx.pool.factory;

import io.netty.channel.EventLoopGroup;
import io.vertx.pgclient.PgConnection;
import io.vertx.sqlclient.SqlConnection;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.database.vertx.VertxDatabaseConfig;
import ru.tinkoff.kora.vertx.common.VertxUtil;

import java.util.concurrent.atomic.AtomicBoolean;

public class SingleHostVertxSqlConnectionFactory implements VertxSqlConnectionFactory {
    private final VertxDatabaseConfig config;
    private final String host;
    private final int port;

    public SingleHostVertxSqlConnectionFactory(VertxDatabaseConfig config) {
        this.config = config;
        var parts = this.config.host().split(":");
        this.host = parts[0];
        this.port = parts.length > 1
            ? Integer.parseInt(parts[1])
            : 5432;
    }

    @Override
    public Mono<SqlConnection> connect(EventLoopGroup eventLoopGroup) {
        return Mono.create(sink -> {
            var cancelled = new AtomicBoolean(false);
            sink.onCancel(() -> cancelled.set(true));
            var vertx = VertxUtil.customEventLoopVertx(eventLoopGroup);
            PgConnection.connect(vertx, config.toPgConnectOptions(this.host, this.port), event -> {
                if (cancelled.get()) {
                    if (event.succeeded()) {
                        event.result().close();
                    }
                } else {
                    if (event.succeeded()) {
                        sink.success(event.result());
                    } else {
                        sink.error(event.cause());
                    }
                }
            });
        });
    }
}
