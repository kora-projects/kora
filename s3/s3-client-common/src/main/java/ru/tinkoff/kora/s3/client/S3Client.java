package ru.tinkoff.kora.s3.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface S3Client<K, V> {

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Put { }



    @Put
    void put();

}
