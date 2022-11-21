package ru.tinkoff.kora.database.jdbi;

import org.jdbi.v3.core.Jdbi;

import javax.sql.DataSource;

public interface JdbiModule {
    default Jdbi jdbiDataBase(DataSource datasource) {
        return Jdbi.create(datasource);
    }
}
