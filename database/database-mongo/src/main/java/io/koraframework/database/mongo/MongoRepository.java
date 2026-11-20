package io.koraframework.database.mongo;

import io.koraframework.database.common.annotation.Repository;

/**
 * <b>Русский</b>: Интерфейс для наследования который указывает что наследник является именно реализацией MongoDB репозитория.
 * <hr>
 * <b>English</b>: An interface for inheritance that specifies that the inheritor is specifically an implementation of the MongoDB repository.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends MongoRepository {
 *
 * }
 * }
 * </pre>
 *
 * @see Repository
 */
public interface MongoRepository {

    MongoExecutor executor();
}
