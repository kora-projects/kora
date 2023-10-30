package ru.tinkoff.kora.database.r2dbc;


import ru.tinkoff.kora.database.common.annotation.Repository;

/**
 * <b>Русский</b>: Интерфейс для наследования который указывает что наследник является именно реализацией R2dbc репозитория.
 * <hr>
 * <b>English</b>: An interface for inheritance that specifies that the inheritor is exactly the R2dbc implementation of the repository.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends R2dbcRepository {
 *
 * }
 * }
 * </pre>
 *
 * @see Repository
 */
public interface R2dbcRepository {
    R2dbcConnectionFactory getR2dbcConnectionFactory();
}
