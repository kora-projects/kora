package io.koraframework.database.mongo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что метод репозитория вставляет документы. Метод принимает ровно один параметр —
 * сущность или коллекцию сущностей.
 * <hr>
 * <b>English</b>: The annotation indicates that the repository method inserts documents. The method takes exactly one parameter —
 * an entity or a collection of entities.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @MongoInsert
 * void insert(User user);
 * }
 * </pre>
 *
 * @see MongoCollection
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface MongoInsert {

    /**
     * @return <b>Русский</b>: Имя коллекции, пустая строка означает вывод из {@link MongoCollection}.
     * <hr>
     * <b>English</b>: Collection name, an empty string means it is taken from {@link MongoCollection}.
     */
    String collection() default "";
}
