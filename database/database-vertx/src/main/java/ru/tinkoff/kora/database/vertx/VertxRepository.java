package ru.tinkoff.kora.database.vertx;

import ru.tinkoff.kora.database.common.annotation.Repository;

/**
 * <b>Русский</b>: Интерфейс для наследования который указывает что наследник является именно реализацией Vertx репозитория.
 * <hr>
 * <b>English</b>: An interface for inheritance that specifies that the inheritor is exactly the Vertx implementation of the repository.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends VertxRepository {
 *
 * }
 * }
 * </pre>
 *
 * @see Repository
 */
public interface VertxRepository {
    VertxConnectionFactory getVertxConnectionFactory();
}
