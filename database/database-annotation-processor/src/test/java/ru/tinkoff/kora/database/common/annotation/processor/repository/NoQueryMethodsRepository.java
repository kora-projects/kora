package ru.tinkoff.kora.database.common.annotation.processor.repository;

import ru.tinkoff.kora.database.common.annotation.Repository;
import ru.tinkoff.kora.database.jdbc.JdbcRepository;

@Repository
public interface NoQueryMethodsRepository extends JdbcRepository {
}
