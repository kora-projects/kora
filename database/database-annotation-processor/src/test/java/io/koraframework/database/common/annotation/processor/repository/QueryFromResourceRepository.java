package io.koraframework.database.common.annotation.processor.repository;

import io.koraframework.database.common.annotation.Query;
import io.koraframework.database.common.annotation.Repository;
import io.koraframework.database.jdbc.JdbcRepository;

@Repository
public interface QueryFromResourceRepository extends JdbcRepository {
    @Query("classpath:/sql/test-query.sql")
    void test();
}
