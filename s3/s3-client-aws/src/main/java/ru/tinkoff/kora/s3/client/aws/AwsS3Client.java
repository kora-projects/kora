package ru.tinkoff.kora.s3.client.aws;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface AwsS3Client {

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Put {}



    @Put
    void put();
}
