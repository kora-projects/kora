package io.koraframework.database.cassandra;

import io.koraframework.database.common.annotation.Repository;

/**
 * <b>Русский</b>: Интерфейс для наследования который указывает что наследник является именно реализация Cassandra репозитория.
 * <hr>
 * <b>English</b>: An interface for inheritance that specifies that the inheritor is specifically an implementation of the Cassandra repository.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends CassandraRepository {
 *
 * }
 * }
 * </pre>
 *
 * @see Repository
 */
public interface CassandraRepository {
    CassandraConnectionFactory getCassandraConnectionFactory();
}
