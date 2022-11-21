package ru.tinkoff.kora.database.common.annotation.processor.repository;

import ru.tinkoff.kora.database.common.annotation.Query;
import ru.tinkoff.kora.database.common.annotation.Repository;
import ru.tinkoff.kora.database.jdbc.JdbcRepository;

import javax.annotation.Nullable;

@Repository
public abstract class AbstractClassRepository implements JdbcRepository {
    private final String field;

    public AbstractClassRepository(@Nullable String field) {
        this.field = field;
    }

    @Query("INSERT INTO table(value) VALUES (:value)")
    public abstract void abstractMethod(String value);

    public void nonAbstractMethod() {

    }
}
