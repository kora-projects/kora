package io.koraframework.database.mongo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает имя коллекции MongoDB. Может быть указана как на сущности, так и на репозитории.
 * <hr>
 * <b>English</b>: The annotation specifies the MongoDB collection name. It can be placed on an entity as well as on a repository.
 * <br>
 * <br>
 * <b>Русский</b>: Коллекция для метода ищется в порядке: атрибут {@code collection} аннотации операции, затем эта аннотация на репозитории,
 * затем эта аннотация на сущности.
 * <hr>
 * <b>English</b>: The collection for a method is resolved in this order: the {@code collection} attribute of the operation annotation,
 * then this annotation on the repository, then this annotation on the entity.
 *
 * @see EntityMongo
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface MongoCollection {

    /**
     * @return <b>Русский</b>: Имя коллекции в MongoDB.
     * <hr>
     * <b>English</b>: Collection name in MongoDB.
     */
    String value();
}
