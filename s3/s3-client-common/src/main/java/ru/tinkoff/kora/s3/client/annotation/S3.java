package ru.tinkoff.kora.s3.client.annotation;

import org.jetbrains.annotations.Range;
import ru.tinkoff.kora.s3.client.S3ClientConfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.CLASS)
public @interface S3 {

    /**
     * Annotation for S3 Client interface contract
     */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.CLASS)
    @interface Client {

        /**
         * @return path for {@link S3ClientConfig} configuration
         * <pre>{@code
         *     @S3.Client("my-client")
         *     interface SomeClient {
         *
         *     }
         * }</pre>
         */
        String value() default "";
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Get {

        /**
         * @return Specifies key template or key constant:
         * <pre>{@code
         *     @S3.Client("my-client")
         *     interface SomeClient {
         *
         *         @S3.Get("prefix-{key}")
         *         S3Object get1(String key);
         *
         *         @S3.Get("const-key")
         *         S3Object get2();
         *     }
         * }</pre>
         */
        String value() default "";
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface List {

        /**
         * @return Specifies key template or key constant:
         * <pre>{@code
         *     @S3.Client("my-client")
         *     interface SomeClient {
         *
         *         @S3.List("pre-{prefix}")
         *         S3ObjectList list1(String prefix);
         *
         *         @S3.List("const-key")
         *         S3ObjectList list2();
         *     }
         * }</pre>
         */
        String value() default "";

        @Range(from = 1, to = 1000)
        int limit() default 1000;
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Put {

        /**
         * @return Specifies key template or key constant:
         * <pre>{@code
         *     @S3.Client("my-client")
         *     interface SomeClient {
         *
         *         @S3.Put("prefix-{key}")
         *         void put1(String key, S3Body body);
         *
         *         @S3.Put("const-key")
         *         void put2(S3Body body);
         *     }
         * }</pre>
         */
        String value() default "";

        /**
         * @return Content-Type value like: image/jpeg
         */
        String type() default "";

        /**
         * @return Content-Encoding value like: gzip
         */
        String encoding() default "";
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Delete {

        /**
         * @return Specifies key template or key constant:
         * <pre>{@code
         *     @S3.Client("my-client")
         *     interface SomeClient {
         *
         *         @S3.Put("prefix-{key}")
         *         void delete1(String key);
         *
         *         @S3.Put("const-key")
         *         void delete2();
         *     }
         * }</pre>
         */
        String value() default "";
    }
}
