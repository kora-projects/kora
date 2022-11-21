package ru.tinkoff.kora.database.vertx.pool;

import io.vertx.sqlclient.SqlConnection;

public final class SqlConnectionHolder {
    final SqlConnection con;
    private volatile long lastSuccessfulOperation = System.currentTimeMillis();

    public SqlConnectionHolder(SqlConnection con) {
        this.con = con;
    }

    public void updateLastSuccessfulOperation() {
        this.lastSuccessfulOperation = System.currentTimeMillis();
    }

    public long getLastSuccessfulOperation() {
        return this.lastSuccessfulOperation;
    }

}
