package ru.tinkoff.kora.s3.client.annotation;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@ApiStatus.Experimental
@Target({})
@Retention(RetentionPolicy.CLASS)
public @interface S3 {

    /**
     * Annotation for S3 Client interface contract
     */

    @ApiStatus.Experimental
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.CLASS)
    @interface Client {
        Class<?>[] clientFactoryTag() default {};
    }

    @ApiStatus.Experimental
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.CLASS)
    @interface Bucket {

        /**
         * @return path for bucket name in configuration
         * <pre>{@code
         *     @S3.Client
         *     @Bucket("myClient.defaultBucket")
         *     interface SomeClient {
         *         @S3.Get
         *         byte[] get1(String key);
         *     }
         * }</pre>
         * <pre>{@code
         *     @S3.Client
         *     interface SomeClient {
         *         @S3.Get
         *         byte[] get1(@S3.Bucket String bucket, String key);
         *     }
         * }</pre>
         * <pre>{@code
         *     @S3.Client
         *     interface SomeClient {
         *         @S3.Get
         *         @S3.Bucket("myClient.get1Bucket)
         *         byte[] get1(String key);
         *     }
         * }</pre>
         */
        String value() default "";
    }


    @ApiStatus.Experimental
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Get {

        /**
         * @return Specifies key template or key constant:
         * <pre>{@code
         *     @S3.Client
         *     @Bucket("bucket")
         *     interface SomeClient {
         *
         *         @S3.Get("prefix-{key}")
         *         S3Object get1(String key);
         *
         *         @S3.Get("const-key")
         *         S3ObjectMeta get2();

         *         @S3.Get("const-key")
         *         byte[] get3();
         *     }
         * }</pre>
         */
        String value() default "";
    }

    @ApiStatus.Experimental
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface List {

        /**
         * @return Specifies key template or key constant:
         * <pre>{@code
         *     @S3.Client
         *     @Bucket("bucket")
         *     interface SomeClient {
         *
         *         @S3.List("pre-{prefix}")
         *         List<S3ObjectMeta> list1(String prefix);
         *
         *         @S3.List("const-key")
         *         Iterator<S3ObjectMeta> list2();
         *     }
         * }</pre>
         */
        String value() default "";

        @ApiStatus.Experimental
        @Target({ElementType.PARAMETER, ElementType.METHOD})
        @Retention(RetentionPolicy.CLASS)
        @interface Delimiter {
            /**
             * @return Specifies delimiter for list operation:
             * <pre>{@code
             *     @S3.Client
             *     @Bucket("bucket")
             *     interface SomeClient {
             *
             *         @S3.List("pre-{prefix}")
             *         List<S3ObjectMeta> list1(String prefix, @S3.List.Delimiter String delimiter);
             *
             *         @S3.List("const-key")
             *         @S3.List.Delimiter("/")
             *         Iterator<S3ObjectMeta> list2();
             *     }
             * }</pre>
             */
            String value() default "";
        }

        @ApiStatus.Experimental
        @Target({ElementType.PARAMETER, ElementType.METHOD})
        @Retention(RetentionPolicy.CLASS)
        @interface Limit {
            int value() default 1000;
        }
    }

    @ApiStatus.Experimental
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Put {

        /**
         * @return Specifies key template or key constant:
         * <pre>{@code
         *     @S3.Client
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

    @ApiStatus.Experimental
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Delete {

        /**
         * @return Specifies key template or key constant:
         * <pre>{@code
         *     @S3.Client
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
