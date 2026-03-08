package io.koraframework.database.common.annotation.processor.repository;

import io.koraframework.database.common.annotation.Repository;
import io.koraframework.database.jdbc.JdbcRepository;

@Repository
public interface NoQueryMethodsRepository extends JdbcRepository {
}
