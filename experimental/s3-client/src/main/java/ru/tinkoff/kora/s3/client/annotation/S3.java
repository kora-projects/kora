package ru.tinkoff.kora.s3.client.annotation;

import ru.tinkoff.kora.s3.client.AwsCredentials;
import ru.tinkoff.kora.s3.client.S3ClientConfig;
import ru.tinkoff.kora.s3.client.exception.S3ClientNoSuchKeyException;
import ru.tinkoff.kora.s3.client.model.request.DeleteObjectArgs;
import ru.tinkoff.kora.s3.client.model.request.GetObjectArgs;
import ru.tinkoff.kora.s3.client.model.request.HeadObjectArgs;
import ru.tinkoff.kora.s3.client.model.request.PutObjectArgs;
import ru.tinkoff.kora.s3.client.model.response.GetObjectResult;
import ru.tinkoff.kora.s3.client.model.response.HeadObjectResult;

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
         *     @S3.Client("myClient")
         *     interface SomeClient {
         *         @S3.Get
         *         @S3.Bucket(".get1Bucket)
         *         byte[] get1(String key);
         *     }
         * }</pre>
         */
        String value() default "";
    }


    /**
     * Marks get operation in a client interface.<br />
     * Possible return values are {@link GetObjectResult} and byte[].<br />
     * Possible arguments are path template parameters, {@link AwsCredentials}, {@link S3.Bucket} annotated string and {@link GetObjectArgs}<br />
     * If no object found method will return null if marked with @Nullable and throw {@link S3ClientNoSuchKeyException} otherwize.
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Get {

        /**
         * @return Specifies key template or key constant:
         * <pre>{@code
         *     @S3.Client("my-client")
         *     @Bucket("bucket")
         *     interface SomeClient {
         *         @S3.Get("prefix-{key}")
         *         @Nullable
         *         byte[] getObjectByKeyTemplate(String key);
         *
         *         @S3.Get("constant-key")
         *         byte[] getObjectByKeyConstant();
         *     }
         * }</pre>
         */
        String value() default "";
    }

    /**
     * Marks head operation in a client interface.<br />
     * Possible return type is {@link HeadObjectResult}.<br />
     * Possible arguments are path template parameters, {@link AwsCredentials}, {@link S3.Bucket} annotated string and {@link HeadObjectArgs}<br />
     * If no object found method will return null if marked with @Nullable and throw {@link S3ClientNoSuchKeyException} otherwise.
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Head {

        /**
         * @return Specifies key template or key constant:
         * <pre>{@code
         *     @S3.Client("my-client")
         *     @Bucket("bucket")
         *     interface SomeClient {
         *
         *         @S3.Head
         *         @Nullable
         *         HeadObjectResult get1(String key, HeadObjectArgs args);
         *
         *         @S3.Head
         *         HeadObjectResult get2(String key);
         *     }
         * }</pre>
         */
        String value() default "";
    }

    /**
     * Marks list operation in a client interface.
     * This operation enumerates objects in a bucket using an optional prefix or a fully specified set of list arguments.
     * Possible return types include a paged result model ListBucketResult, List&lt;String>, Iterator&lt;String>,
     * List&lt;ListBucketResult.ListBucketItem>, Iterator&lt;ListBucketResult.ListBucketItem>.
     * Possible method arguments are path template parameters, {@link AwsCredentials}, {@link S3.Bucket} annotated string
     * and a ListObjectsArgs argument that encapsulates list options (prefix, delimiter, maxKeys, continuationToken, etc.).
     * When returning an iterator the implementation will lazily fetch subsequent pages.
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface List {

        /**
         * @return Specifies key prefix template or constant prefix to filter listed objects. If empty, no prefix filter is applied.
         * <pre>{@code
         *     @S3.Client
         *     @Bucket("bucket")
         *     interface SomeClient {
         *
         *         @S3.List("pre-{prefix}")
         *         ListBucketResult list(String prefix);
         *
         *         @S3.List("prefix")
         *         ListBucketResult list(ListObjectsArgs args);
         *
         *         @S3.List
         *         List<String> listStrings(String prefix);
         *
         *         @S3.List
         *         Iterator<String> iteratorString(ListObjectsArgs args);
         *
         *     }
         * }</pre>
         */
        String value() default "";
    }

    /**
     * Marks put (upload) operation in a client interface.<br />
     * Marked method should use void or string as return type, string represents returned ETag.<br />
     * Possible arguments are path template parameters, {@link AwsCredentials}, {@link S3.Bucket} annotated string, {@link PutObjectArgs}
     * and a request body. Supported body types include byte[], java.nio.ByteBuffer, java.io.InputStream and a ContentWriter provided by the client.<br />
     * InputStream body will lead to multipart upload with buffer size specified in {@link S3ClientConfig.UploadConfig#partSize()}.
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Put {

        /**
         * @return Specifies key template or key constant:
         * <pre>{@code
         *     @S3.Client("my-client")
         *     @Bucket(".bucket")
         *     interface SomeClient {"}],
         *
         *         @S3.Put("prefix-{key}")
         *         void putByteArray(String key, byte[] body);
         *
         *         @S3.Put("constant-key")
         *         void putWriter(PutObjectArgs args, S3Client.ContentWriter body);
         *     }
         * }</pre>
         */
        String value() default "";
    }

    /**
     * Marks delete operation in a client interface.<br />
     * Marked method should use void as return type.<br />
     * Possible arguments are path template parameters, {@link AwsCredentials}, {@link S3.Bucket} annotated string and {@link DeleteObjectArgs}<br />
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Delete {

        /**
         * @return Specifies key template or key constant:
         * <pre>{@code
         *     @S3.Client("my-client")
         *     @Bucket(".bucket")
         *     interface SomeClient {
         *
         *         @S3.Delete("prefix-{key}")
         *         void delete1(String key);
         *
         *         @S3.Delete("constant-key")
         *         void delete2(@Bucket String bucket);
         *     }
         * }</pre>
         */
        String value() default "";
    }
}
