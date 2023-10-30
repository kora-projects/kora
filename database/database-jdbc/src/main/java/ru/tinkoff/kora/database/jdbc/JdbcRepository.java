package ru.tinkoff.kora.database.jdbc;


import ru.tinkoff.kora.database.common.annotation.Repository;

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
    JdbcConnectionFactory getJdbcConnectionFactory();
}
