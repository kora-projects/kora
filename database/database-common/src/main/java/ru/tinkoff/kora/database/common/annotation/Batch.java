package ru.tinkoff.kora.database.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает, что входной параметр запроса должен быть выполнен как пакетный запрос
 * <hr>
 * <b>English</b>: Annotation indicates that input query parameter is intended to be executed as Batch request
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Repository
 * public interface MyRepository extends JdbcRepository {
 *
 *     @Query("INSERT INTO users(fullname) VALUES (:names)")
 *     void addUsers(@Batch List<String> names);
 * }
 * }
 * </pre>
 *
 * @see Query
 * @see Repository
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Batch {

}
