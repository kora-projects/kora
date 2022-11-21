package ru.tinkoff.kora.database.common.annotation.processor.repository;

import ru.tinkoff.kora.database.common.annotation.Query;
import ru.tinkoff.kora.database.common.annotation.Repository;
import ru.tinkoff.kora.database.jdbc.JdbcRepository;

@Repository
public interface QueryFromResourceRepository extends JdbcRepository {
    @Query("classpath:/sql/test-query.sql")
    void test();
}
