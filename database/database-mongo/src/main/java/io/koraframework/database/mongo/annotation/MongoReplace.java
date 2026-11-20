package io.koraframework.database.mongo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что метод репозитория целиком заменяет документ сущностью из параметра.
 * <hr>
 * <b>English</b>: The annotation indicates that the repository method replaces a whole document with the entity from a parameter.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @MongoReplace(filter = "{\"_id\": :id}")
 * UpdateCount replace(ObjectId id, User user);
 * }
 * </pre>
 *
 * @see MongoCollection
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface MongoReplace {

    /**
     * @return <b>Русский</b>: Фильтр отбора заменяемого документа в виде BSON документа.
     * <hr>
     * <b>English</b>: Filter selecting the document to replace, as a BSON document.
     */
    String filter();

    /**
     * @return <b>Русский</b>: Создаёт документ, если ни один не подошёл под фильтр.
     * <hr>
     * <b>English</b>: Creates a document when none matched the filter.
     */
    boolean upsert() default false;

    /**
     * @return <b>Русский</b>: Имя коллекции, пустая строка означает вывод из {@link MongoCollection}.
     * <hr>
     * <b>English</b>: Collection name, an empty string means it is taken from {@link MongoCollection}.
     */
    String collection() default "";
}
