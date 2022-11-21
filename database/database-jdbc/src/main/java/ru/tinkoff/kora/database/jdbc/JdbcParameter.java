package ru.tinkoff.kora.database.jdbc;

import java.sql.PreparedStatement;

// index is zero-based
public record JdbcParameter<T>(PreparedStatement stmt, int sqlIndex, T value) {}
