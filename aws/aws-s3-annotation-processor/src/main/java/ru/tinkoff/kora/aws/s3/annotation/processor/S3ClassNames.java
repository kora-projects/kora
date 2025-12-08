package ru.tinkoff.kora.aws.s3.annotation.processor;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Set;

public class S3ClassNames {
    public static class Annotation {
        public static final ClassName CLIENT = ClassName.get("ru.tinkoff.kora.aws.s3.annotation", "S3", "Client");
        public static final ClassName BUCKET = ClassName.get("ru.tinkoff.kora.aws.s3.annotation", "S3", "Bucket");
        public static final ClassName GET = ClassName.get("ru.tinkoff.kora.aws.s3.annotation", "S3", "Get");
        public static final ClassName LIST = ClassName.get("ru.tinkoff.kora.aws.s3.annotation", "S3", "List");
        public static final ClassName PUT = ClassName.get("ru.tinkoff.kora.aws.s3.annotation", "S3", "Put");
        public static final ClassName DELETE = ClassName.get("ru.tinkoff.kora.aws.s3.annotation", "S3", "Delete");
        public static final ClassName HEAD = ClassName.get("ru.tinkoff.kora.aws.s3.annotation", "S3", "Head");

        public static final Set<ClassName> OPERATIONS = Set.of(GET, LIST, PUT, DELETE, HEAD);
    }


    public static final ClassName CLIENT = ClassName.get("ru.tinkoff.kora.aws.s3", "S3Client");
    public static final ClassName AWS_CREDENTIALS = ClassName.get("ru.tinkoff.kora.aws.s3", "AwsCredentials");
    public static final ClassName CLIENT_FACTORY = ClassName.get("ru.tinkoff.kora.aws.s3", "S3ClientFactory");
    public static final ClassName CONFIG = ClassName.get("ru.tinkoff.kora.aws.s3", "S3ClientConfig");
    public static final ClassName CONFIG_WITH_CREDS = ClassName.get("ru.tinkoff.kora.aws.s3", "S3ClientConfigWithCredentials");

    public static final ClassName CONTENT_WRITER = CLIENT.nestedClass("ContentWriter");

    public static final ClassName PUT_OBJECT_ARGS = ClassName.get("ru.tinkoff.kora.aws.s3.model.request", "PutObjectArgs");
    public static final ClassName GET_OBJECT_ARGS = ClassName.get("ru.tinkoff.kora.aws.s3.model.request", "GetObjectArgs");
    public static final ClassName DELETE_OBJECT_ARGS = ClassName.get("ru.tinkoff.kora.aws.s3.model.request", "DeleteObjectArgs");
    public static final ClassName HEAD_OBJECT_ARGS = ClassName.get("ru.tinkoff.kora.aws.s3.model.request", "HeadObjectArgs");
    public static final ClassName LIST_OBJECTS_ARGS = ClassName.get("ru.tinkoff.kora.aws.s3.model.request", "ListObjectsArgs");
    public static final ClassName CREATE_MULTIPART_UPLOAD_ARGS = ClassName.get("ru.tinkoff.kora.aws.s3.model.request", "CreateMultipartUploadArgs");
    public static final ClassName COMPLETE_MULTIPART_UPLOAD_ARGS = ClassName.get("ru.tinkoff.kora.aws.s3.model.request", "CompleteMultipartUploadArgs");
    public static final Set<TypeName> ARGS = Set.of(PUT_OBJECT_ARGS, GET_OBJECT_ARGS, DELETE_OBJECT_ARGS, HEAD_OBJECT_ARGS, LIST_OBJECTS_ARGS);

    public static final ClassName GET_OBJECT_RESULT = ClassName.get("ru.tinkoff.kora.aws.s3.model.response", "GetObjectResult");
    public static final ClassName HEAD_OBJECT_RESULT = ClassName.get("ru.tinkoff.kora.aws.s3.model.response", "HeadObjectResult");
    public static final ClassName LIST_BUCKET_RESULT = ClassName.get("ru.tinkoff.kora.aws.s3.model.response", "ListBucketResult");
    public static final ClassName UPLOADED_PART = ClassName.get("ru.tinkoff.kora.aws.s3.model.response", "UploadedPart");
    public static final ClassName LIST_BUCKET_RESULT_ITEM = LIST_BUCKET_RESULT.nestedClass("ListBucketItem");

    public static final ClassName UNKNOWN_EXCEPTION = ClassName.get("ru.tinkoff.kora.aws.s3.exception", "S3ClientUnknownException");

    public static final Set<TypeName> BODY_TYPES = Set.of(
        ArrayTypeName.of(TypeName.BYTE),
        ClassName.get(ByteBuffer.class),
        ClassName.get(InputStream.class),
        CONTENT_WRITER
    );

}
