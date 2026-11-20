package io.koraframework.database.mongo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что метод репозитория изменяет документы операторами обновления.
 * <hr>
 * <b>English</b>: The annotation indicates that the repository method modifies documents with update operators.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @MongoUpdate(filter = "{\"_id\": :id}", update = "{\"$set\": {\"login\": :login}}")
 * UpdateCount rename(ObjectId id, String login);
 * }
 * </pre>
 *
 * @see MongoCollection
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface MongoUpdate {

    /**
     * @return <b>Русский</b>: Фильтр отбора изменяемых документов в виде BSON документа.
     * <hr>
     * <b>English</b>: Filter selecting the documents to modify, as a BSON document.
     */
    String filter();

    /**
     * @return <b>Русский</b>: Документ обновления с операторами вида {@code $set} или {@code $inc}.
     * <hr>
     * <b>English</b>: Update document with operators such as {@code $set} or {@code $inc}.
     */
    String update();

    /**
     * @return <b>Русский</b>: Создаёт документ, если ни один не подошёл под фильтр.
     * <hr>
     * <b>English</b>: Creates a document when none matched the filter.
     */
    boolean upsert() default false;

    /**
     * @return <b>Русский</b>: Изменяет все подходящие документы вместо первого найденного.
     * <hr>
     * <b>English</b>: Modifies every matching document instead of the first one.
     */
    boolean many() default false;

    /**
     * @return <b>Русский</b>: Имя коллекции, пустая строка означает вывод из {@link MongoCollection}.
     * <hr>
     * <b>English</b>: Collection name, an empty string means it is taken from {@link MongoCollection}.
     */
    String collection() default "";
}
