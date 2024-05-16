package ru.tinkoff.kora.s3.client.annotation;

import org.jetbrains.annotations.Range;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.CLASS)
public @interface S3 {

    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.CLASS)
    @interface Client {

        String value() default "";
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Get {

        String value() default "";
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface List {

        String value() default "";

        @Range(from = 1, to = 1000)
        int limit() default 1000;
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Put {

        String value() default "";

        String type() default "";

        String encoding() default "";
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Delete {

        String value() default "";
    }
}
