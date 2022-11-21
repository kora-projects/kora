package ru.tinkoff.kora.database.vertx.pool.factory;

import io.netty.channel.EventLoopGroup;
import io.vertx.sqlclient.SqlConnection;
import reactor.core.publisher.Mono;

public interface VertxSqlConnectionFactory {
    Mono<SqlConnection> connect(EventLoopGroup eventLoopGroup);
}
