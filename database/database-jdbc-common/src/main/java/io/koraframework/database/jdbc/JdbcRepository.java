package io.koraframework.database.jdbc;


import io.koraframework.database.common.annotation.Repository;

/**
 * <b>Русский</b>: Интерфейс для наследования который указывает что наследник является именно реализация JDBC репозитория.
 * <hr>
 * <b>English</b>: An interface for inheritance that specifies that the inheritor is specifically an implementation of the JDBC repository.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends JdbcRepository {
 *
 * }
 * }
 * </pre>
 *
 * @see Repository
 */
public interface JdbcRepository {

    /**
     * <b>Русский</b>: Возвращает JDBC исполнитель, который используется репозиторием для запросов и транзакций.
     * <hr>
     * <b>English</b>: Returns the JDBC executor used by the repository for queries and transactions.
     *
     * @return JDBC исполнитель / JDBC executor
     */
    JdbcExecutor executor();
}
