package ru.tinkoff.kora.s3.client.annotation.processor;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import java.io.InputStream;
import java.util.Set;

public class S3ClassNames {

    public static class Annotation {
        public static final ClassName CLIENT = ClassName.get("ru.tinkoff.kora.s3.client.annotation", "S3", "Client");
        public static final ClassName BUCKET = ClassName.get("ru.tinkoff.kora.s3.client.annotation", "S3", "Bucket");
        public static final ClassName GET = ClassName.get("ru.tinkoff.kora.s3.client.annotation", "S3", "Get");
        public static final ClassName LIST = ClassName.get("ru.tinkoff.kora.s3.client.annotation", "S3", "List");
        public static final ClassName LIST_LIMIT = ClassName.get("ru.tinkoff.kora.s3.client.annotation", "S3", "List", "Limit");
        public static final ClassName LIST_DELIMITER = ClassName.get("ru.tinkoff.kora.s3.client.annotation", "S3", "List", "Delimiter");
        public static final ClassName PUT = ClassName.get("ru.tinkoff.kora.s3.client.annotation", "S3", "Put");
        public static final ClassName DELETE = ClassName.get("ru.tinkoff.kora.s3.client.annotation", "S3", "Delete");

        public static final Set<ClassName> OPERATIONS = Set.of(GET, LIST, PUT, DELETE);
    }


    public static final ClassName CLIENT = ClassName.get("ru.tinkoff.kora.s3.client", "S3Client");
    public static final ClassName CLIENT_FACTORY = ClassName.get("ru.tinkoff.kora.s3.client", "S3ClientFactory");
    public static final ClassName S3_BODY = ClassName.get("ru.tinkoff.kora.s3.client.model", "S3Body");
    public static final ClassName S3_OBJECT = ClassName.get("ru.tinkoff.kora.s3.client.model", "S3Object");
    public static final ClassName S3_OBJECT_META = ClassName.get("ru.tinkoff.kora.s3.client.model", "S3ObjectMeta");
    public static final ClassName S3_OBJECT_UPLOAD_RESULT = ClassName.get("ru.tinkoff.kora.s3.client.model", "S3ObjectUploadResult");
    public static final Set<TypeName> BODY_TYPES = Set.of(S3_BODY, ArrayTypeName.of(TypeName.BYTE), ClassName.get(InputStream.class));

    public static final ClassName RANGE_DATA = ClassName.get("ru.tinkoff.kora.s3.client", "S3Client", "RangeData");
    public static final ClassName RANGE_DATA_RANGE = ClassName.get("ru.tinkoff.kora.s3.client", "S3Client", "RangeData", "Range");
    public static final ClassName RANGE_DATA_START_FROM = ClassName.get("ru.tinkoff.kora.s3.client", "S3Client", "RangeData", "StartFrom");
    public static final ClassName RANGE_DATA_LAST_N = ClassName.get("ru.tinkoff.kora.s3.client", "S3Client", "RangeData", "LastN");
    public static final Set<TypeName> RANGE_CLASSES = Set.of(RANGE_DATA, RANGE_DATA_RANGE, RANGE_DATA_START_FROM, RANGE_DATA_LAST_N);
}
