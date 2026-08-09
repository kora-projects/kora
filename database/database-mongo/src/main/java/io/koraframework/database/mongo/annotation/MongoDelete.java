package io.koraframework.database.mongo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что метод репозитория удаляет документы.
 * <hr>
 * <b>English</b>: The annotation indicates that the repository method deletes documents.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @MongoDelete(filter = "{\"_id\": :id}")
 * UpdateCount deleteById(ObjectId id);
 * }
 * </pre>
 *
 * @see MongoCollection
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface MongoDelete {

    /**
     * @return <b>Русский</b>: Фильтр отбора удаляемых документов в виде BSON документа.
     * <hr>
     * <b>English</b>: Filter selecting the documents to delete, as a BSON document.
     */
    String filter();

    /**
     * @return <b>Русский</b>: Удаляет все подходящие документы вместо первого найденного.
     * <hr>
     * <b>English</b>: Deletes every matching document instead of the first one.
     */
    boolean many() default false;

    /**
     * @return <b>Русский</b>: Имя коллекции, пустая строка означает вывод из {@link MongoCollection}.
     * <hr>
     * <b>English</b>: Collection name, an empty string means it is taken from {@link MongoCollection}.
     */
    String collection() default "";
}
