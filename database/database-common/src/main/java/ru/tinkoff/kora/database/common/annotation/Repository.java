package ru.tinkoff.kora.database.common.annotation;

import ru.tinkoff.kora.common.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что интерфейс является репозиторием и отвечает за взаимодействие с базой данных.
 * <hr>
 * <b>English</b>: The annotation indicates that the interface is a repository and is responsible for interacting with the database.
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
 * @see Query
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Repository {

    /**
     * @return <b>Русский</b>: Указывает тег для набора соединений на котором будут запускаться запросы в базу данных.
     * <hr>
     * <b>English</b>: The annotation indicates that the interface is a repository and is responsible for interacting with the database.
     */
    Tag executorTag() default @Tag({});
}
