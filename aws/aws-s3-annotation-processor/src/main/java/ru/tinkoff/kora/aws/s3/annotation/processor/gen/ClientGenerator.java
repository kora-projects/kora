package ru.tinkoff.kora.aws.s3.annotation.processor.gen;

import com.palantir.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.aws.s3.annotation.processor.S3ClassNames;
import ru.tinkoff.kora.aws.s3.annotation.processor.S3ClientAnnotationProcessor;
import ru.tinkoff.kora.aws.s3.annotation.processor.S3ClientUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;

public class ClientGenerator {
    public static TypeSpec generate(ProcessingEnvironment processingEnv, TypeElement s3client) {
        var packageName = processingEnv.getElementUtils().getPackageOf(s3client).getQualifiedName().toString();
        var bucketsType = ClassName.get(packageName, NameUtils.generatedType(s3client, "BucketsConfig"));
        var bucketsPath = S3ClientUtils.parseConfigBuckets(s3client);
        var credsRequired = s3client.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .map(ExecutableElement.class::cast)
            .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
            .filter(e -> !e.getModifiers().contains(Modifier.DEFAULT))
            .anyMatch(e -> S3ClientUtils.credentialsParameter(e) == null);
        var configType = credsRequired
            ? S3ClassNames.CONFIG_WITH_CREDS
            : S3ClassNames.CONFIG;
        var b = CommonUtils.extendsKeepAop(s3client, NameUtils.generatedType(s3client, "ClientImpl"))
            .addAnnotation(AnnotationUtils.generated(S3ClientAnnotationProcessor.class))
            .addField(S3ClassNames.CLIENT, "client", Modifier.PRIVATE, Modifier.FINAL)
            .addField(configType, "config", Modifier.PRIVATE, Modifier.FINAL);
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(S3ClassNames.CLIENT_FACTORY, "clientFactory")
            .addParameter(configType, "clientConfig")
            .addStatement("this.client = clientFactory.create(clientConfig)")
            .addStatement("this.config = clientConfig");
        if (!bucketsPath.isEmpty()) {
            constructor.addParameter(bucketsType, "bucketsConfig");
            constructor.addStatement("this.bucketsConfig = bucketsConfig");
            b.addField(bucketsType, "bucketsConfig", Modifier.PRIVATE, Modifier.FINAL);
        }
        b.addMethod(constructor.build());
        for (var element : s3client.getEnclosedElements()) {
            if (element.getKind() != ElementKind.METHOD) {
                continue;
            }
            if (element.getModifiers().contains(Modifier.STATIC) || element.getModifiers().contains(Modifier.DEFAULT)) {
                continue;
            }
            var method = (ExecutableElement) element;
            b.addMethod(generateMethod(method));
        }
        return b.build();
    }

    private static MethodSpec generateMethod(ExecutableElement method) {
        var operations = S3ClassNames.Annotation.OPERATIONS.stream()
            .map(cn -> AnnotationUtils.findAnnotation(method, cn))
            .filter(Objects::nonNull)
            .toList();
        if (operations.isEmpty()) {
            throw new ProcessingErrorException("No S3 operation annotation found", method);
        }
        if (operations.size() > 1) {
            throw new ProcessingErrorException("More than one S3 operation annotation found", method);
        }
        var operation = operations.getFirst();
        return switch (operation.getAnnotationType().asElement().getSimpleName().toString()) {
            case "Get" -> generateGet(method, operation);
            case "List" -> generateList(method, operation);
            case "Head" -> generateHead(method, operation);
            case "Put" -> generatePut(method, operation);
            case "Delete" -> generateDelete(method, operation);
            default -> throw new IllegalStateException("Unexpected value: " + operation.getAnnotationType().asElement().getSimpleName().toString());
        };
    }

    private static MethodSpec generateDelete(ExecutableElement method, AnnotationMirror operation) {
        var b = CommonUtils.overridingKeepAop(method);
        generateCreds(method, b);
        generateBucket(method, b);
        b.addStatement("var _key = $L", generateKey(method, operation, true));
        var args = method.getParameters().stream().filter(p -> TypeName.get(p.asType()).equals(S3ClassNames.DELETE_OBJECT_ARGS)).findFirst().orElse(null);
        if (args == null) {
            b.addStatement("var _args = ($T) null", S3ClassNames.DELETE_OBJECT_ARGS);
        } else {
            b.addStatement("var _args = $N", args.getSimpleName());
        }
        b.addStatement("this.client.deleteObject(_creds, _bucket, _key, _args)");
        if (!TypeName.get(method.getReturnType()).equals(TypeName.VOID)) {
            throw new ProcessingErrorException("Unexpected return type %s".formatted(method.getReturnType()), method);
        }
        return b.build();
    }

    private static MethodSpec generatePut(ExecutableElement method, AnnotationMirror operation) {
        var b = CommonUtils.overridingKeepAop(method);
        generateCreds(method, b);
        generateBucket(method, b);
        b.addStatement("var _key = $L", generateKey(method, operation, true));
        var args = method.getParameters().stream().filter(p -> TypeName.get(p.asType()).equals(S3ClassNames.PUT_OBJECT_ARGS)).findFirst().orElse(null);
        if (args == null) {
            b.addStatement("var _args = ($T) null", S3ClassNames.PUT_OBJECT_ARGS);
        } else {
            b.addStatement("var _args = $N", args.getSimpleName());
        }
        var contents = method.getParameters().stream().filter(p -> S3ClassNames.BODY_TYPES.contains(TypeName.get(p.asType()))).toList();
        if (contents.isEmpty()) {
            throw new ProcessingErrorException("Unexpected empty content for PUT operation", method);
        }
        if (contents.size() != 1) {
            throw new ProcessingErrorException("More than one PUT operation annotation found", method);
        }
        var returnType = TypeName.get(method.getReturnType());
        final boolean hasReturn;
        if (returnType.equals(ClassName.get(String.class))) {
            hasReturn = true;
        } else if (returnType.equals(TypeName.VOID)) {
            hasReturn = false;
        } else {
            throw new ProcessingErrorException("Unexpected return type %s".formatted(returnType), method);
        }
        var content = contents.getFirst();
        var contentType = TypeName.get(content.asType());
        if (contentType.equals(S3ClassNames.CONTENT_WRITER)) {
            if (hasReturn) {
                b.addCode("return ");
            }
            b.addStatement("this.client.putObject(_creds, _bucket, _key, _args, $N)", content.getSimpleName());
            return b.build();
        }
        if (contentType.equals(ArrayTypeName.of(TypeName.BYTE))) {
            if (hasReturn) {
                b.addCode("return ");
            }
            b.addStatement("this.client.putObject(_creds, _bucket, _key, _args, $N, 0, $N.length)", content.getSimpleName(), content.getSimpleName());
            return b.build();
        }
        if (contentType.equals(ClassName.get(ByteBuffer.class))) {
            b.addStatement("var _len = $N.remaining()", content.getSimpleName());
            b.addStatement("final byte[] _buf;");
            b.addStatement("final int _off;");
            b.beginControlFlow("if ($N.hasArray())", content.getSimpleName())
                .addStatement("_buf = $N.array()", content.getSimpleName())
                .addStatement("_off = $N.arrayOffset()", content.getSimpleName())
                .nextControlFlow("else")
                .addStatement("_buf = new byte[_len]")
                .addStatement("$N.get(_buf)", content.getSimpleName())
                .addStatement("_off = 0")
                .endControlFlow();

            if (hasReturn) {
                b.addCode("return ");
            }
            b.addStatement("this.client.putObject(_creds, _bucket, _key, _args, _buf, _off, _len)");
            return b.build();
        }
        if (!contentType.equals(ClassName.get(InputStream.class))) {
            throw new ProcessingErrorException("Unexpected content type %s".formatted(contentType), method);
        }
        b.addStatement("var _buf = new byte[(int) this.config.upload().partSize().toBytes()]");
        b.beginControlFlow("try ($N)", content.getSimpleName());
        b.addStatement("var _read = $N.readNBytes(_buf, 0, _buf.length)", content.getSimpleName());
        b.addStatement("var _parts = new $T<$T>()", ArrayList.class, S3ClassNames.UPLOADED_PART);
        b.addStatement("final String _uploadId");
        b.beginControlFlow("if (_read == _buf.length)");
        // full part
        b.addStatement("var _createMultipartUploadArgs = $T.from(_args)", S3ClassNames.CREATE_MULTIPART_UPLOAD_ARGS);
        b.addStatement("_uploadId = this.client.createMultipartUpload(_creds, _bucket, _key, _createMultipartUploadArgs)");
        b.addStatement("var _part = this.client.uploadPart(_creds, _bucket, _key, _uploadId, 1, _buf, 0, _read)");
        b.addStatement("_parts.add(_part)");
        b.nextControlFlow("else");
        // last part or only part
        b.addCode("// end of stream reached on first part, just upload it\n");
        if (hasReturn) {
            b.addCode("return ");
        }
        b.addStatement("this.client.putObject(_creds, _bucket, _key, _args, _buf, 0, _read)");
        b.endControlFlow();

        b.beginControlFlow("for (var _partNumber = 2; ; _partNumber++)");
        b.addStatement("_read = $N.readNBytes(_buf, 0, _buf.length)", content.getSimpleName());
        b.beginControlFlow("if (_read > 0)");
        b.addStatement("var _part = this.client.uploadPart(_creds, _bucket, _key, _uploadId, _partNumber, _buf, 0, _read)");
        b.addStatement("_parts.add(_part)");
        b.endControlFlow();

        b.beginControlFlow("if (_read < _buf.length)");
        // final part reached
        b.addStatement("var _completeMultipartUploadArgs = $T.from(_args)", S3ClassNames.COMPLETE_MULTIPART_UPLOAD_ARGS);
        if (hasReturn) {
            b.addCode("return ");
        }
        b.addStatement("this.client.completeMultipartUpload(_creds, _bucket, _key, _uploadId, _parts, _completeMultipartUploadArgs)");


        b.endControlFlow();

        b.endControlFlow();

        b.nextControlFlow("catch ($T _e)", IOException.class)
            .addStatement("throw new $T(_e)", S3ClassNames.UNKNOWN_EXCEPTION)
            .endControlFlow();

        return b.build();
    }

    private static MethodSpec generateHead(ExecutableElement method, AnnotationMirror operation) {
        var b = CommonUtils.overridingKeepAop(method);
        generateCreds(method, b);
        generateBucket(method, b);
        b.addStatement("var _key = $L", generateKey(method, operation, true));
        var args = method.getParameters().stream().filter(p -> TypeName.get(p.asType()).equals(S3ClassNames.HEAD_OBJECT_ARGS)).findFirst().orElse(null);
        if (args == null) {
            b.addStatement("var _args = ($T) null", S3ClassNames.HEAD_OBJECT_ARGS);
        } else {
            b.addStatement("var _args = $N", args.getSimpleName());
        }
        var isRequired = !CommonUtils.isNullable(method);

        b.addStatement("var _rs = this.client.headObject(_creds, _bucket, _key, _args, $L)", isRequired);
        if (!isRequired) {
            b.addStatement("if (_rs == null) return null");
        }
        var returnType = TypeName.get(method.getReturnType());
        if (returnType.equals(S3ClassNames.HEAD_OBJECT_RESULT)) {
            b.addStatement("return _rs");
        } else {
            throw new ProcessingErrorException("Unexpected return type %s".formatted(method.getReturnType()), method);
        }

        return b.build();
    }

    private static MethodSpec generateList(ExecutableElement method, AnnotationMirror operation) {
        var b = CommonUtils.overridingKeepAop(method);
        generateCreds(method, b);
        generateBucket(method, b);
        var args = method.getParameters().stream().filter(p -> TypeName.get(p.asType()).equals(S3ClassNames.LIST_OBJECTS_ARGS)).findFirst().orElse(null);
        if (args == null) {
            b.addStatement("var _args = new $T()", S3ClassNames.LIST_OBJECTS_ARGS);
            b.addStatement("_args.prefix = $L", generateKey(method, operation, true));
        } else {
            b.addStatement("var _args = $N", args.getSimpleName());
        }
        var returnType = TypeName.get(method.getReturnType());
        if (returnType.equals(S3ClassNames.LIST_BUCKET_RESULT)) {
            b.addStatement("return this.client.listObjectsV2(_creds, _bucket, _args)");
            return b.build();
        }

        if (returnType.equals(ParameterizedTypeName.get(ClassName.get(List.class), S3ClassNames.LIST_BUCKET_RESULT_ITEM))) {
            b.addStatement("return this.client.listObjectsV2(_creds, _bucket, _args).items()");
            return b.build();
        }
        if (returnType.equals(ParameterizedTypeName.get(List.class, String.class))) {
            b.addStatement("var _items =  this.client.listObjectsV2(_creds, _bucket, _args).items()");
            b.addStatement("var _result = new $T<String>(_items.size())", ArrayList.class);
            b.beginControlFlow("for (var _item : _items)")
                .addStatement("_result.add(_item.key())")
                .endControlFlow();
            b.addStatement("return _result");
            return b.build();
        }
        if (returnType.equals(ParameterizedTypeName.get(ClassName.get(Iterator.class), S3ClassNames.LIST_BUCKET_RESULT_ITEM))) {
            b.addStatement("return this.client.listObjectsV2Iterator(_creds, _bucket, _args)");
            return b.build();
        }
        if (returnType.equals(ParameterizedTypeName.get(Iterator.class, String.class))) {
            b.addStatement("var _iterator =  this.client.listObjectsV2Iterator(_creds, _bucket, _args)");
            b.beginControlFlow("return new $T<>()", ClassName.get(Iterator.class));
            b.addCode("""
                @Override public boolean hasNext() { return _iterator.hasNext(); }
                @Override public String next() { return _iterator.next().key(); }
                """);
            b.endControlFlow("");
            return b.build();
        }


        throw new ProcessingErrorException("Unexpected return type: " + returnType, method);
    }

    private static MethodSpec generateGet(ExecutableElement method, AnnotationMirror operation) {
        var b = CommonUtils.overridingKeepAop(method);
        generateCreds(method, b);
        generateBucket(method, b);
        b.addStatement("var _key = $L", generateKey(method, operation, true));
        var args = method.getParameters().stream().filter(p -> TypeName.get(p.asType()).equals(S3ClassNames.GET_OBJECT_ARGS)).findFirst();
        if (args.isEmpty()) {
            b.addStatement("var _args = ($T) null", S3ClassNames.GET_OBJECT_ARGS);
        } else {
            b.addStatement("var _args = $N", args.get().getSimpleName());
        }
        var isRequired = !CommonUtils.isNullable(method);

        b.addStatement("var _rs = this.client.getObject(_creds, _bucket, _key, _args, $L)", isRequired);
        if (!isRequired) {
            b.addStatement("if (_rs == null) return null");
        }
        var returnType = TypeName.get(method.getReturnType());
        if (returnType.equals(S3ClassNames.GET_OBJECT_RESULT)) {
            b.addStatement("return _rs");
        } else if (returnType.equals(ArrayTypeName.of(TypeName.BYTE))) {
            b.beginControlFlow("try (_rs; var _body = _rs.body(); var _is = _body.asInputStream())")
                .addStatement("return _is.readAllBytes()")
                .nextControlFlow("catch ($T _e)", IOException.class)
                .addStatement("throw new $T(_e)", S3ClassNames.UNKNOWN_EXCEPTION)
                .endControlFlow();
        } else {
            throw new ProcessingErrorException("Unexpected return type %s".formatted(method.getReturnType()), method);
        }

        return b.build();
    }

    private static void generateCreds(ExecutableElement method, MethodSpec.Builder b) {
        var credentials = S3ClientUtils.credentialsParameter(method);
        if (credentials != null) {
            b.addStatement("var _creds = $N", credentials.getSimpleName());
        } else {
            b.addStatement("var _creds = this.config.credentials()");
        }
    }

    private static void generateBucket(ExecutableElement method, MethodSpec.Builder b) {
        var bucketParam = S3ClientUtils.bucketParameter(method);
        var bucketOnMethod = AnnotationUtils.findAnnotation(method, S3ClassNames.Annotation.BUCKET);
        var bucketOnClass = AnnotationUtils.findAnnotation(method.getEnclosingElement(), S3ClassNames.Annotation.BUCKET);
        if (bucketParam != null) {
            b.addStatement("var _bucket = $N", bucketParam.getSimpleName());
        } else if (bucketOnMethod != null) {
            var index = S3ClientUtils.parseConfigBuckets((TypeElement) method.getEnclosingElement())
                .indexOf(AnnotationUtils.<String>parseAnnotationValueWithoutDefault(bucketOnMethod, "value"));
            if (index < 0) {
                throw new IllegalStateException();
            }
            b.addStatement("var _bucket = this.bucketsConfig.bucket_$L", index);
        } else if (bucketOnClass != null) {
            var index = S3ClientUtils.parseConfigBuckets((TypeElement) method.getEnclosingElement())
                .indexOf(AnnotationUtils.<String>parseAnnotationValueWithoutDefault(bucketOnClass, "value"));
            if (index < 0) {
                throw new IllegalStateException();
            }
            b.addStatement("var _bucket = this.bucketsConfig.bucket_$L", index);
        }
    }

    private static CodeBlock generateKey(ExecutableElement method, AnnotationMirror annotation, boolean required) {
        var keyMapping = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(annotation, "value");
        var parameters = method.getParameters()
            .stream()
            .filter(p -> {
                var parameterTypeName = TypeName.get(p.asType());
                return !AnnotationUtils.isAnnotationPresent(p, S3ClassNames.Annotation.BUCKET)
                    && !S3ClassNames.AWS_CREDENTIALS.equals(parameterTypeName)
                    && !S3ClassNames.ARGS.contains(parameterTypeName)
                    && !S3ClassNames.BODY_TYPES.contains(parameterTypeName)
                    ;
            })
            .toList();
        if (keyMapping != null && !keyMapping.isBlank()) {
            var key = parseKey(method, parameters, keyMapping);
            if (key.params().isEmpty() && !parameters.isEmpty()) {
                throw new ProcessingErrorException("@S3 operation prefix template must use method arguments or they should be removed", method);
            }
            return key.code;
        }
        if (parameters.size() > 1) {
            throw new ProcessingErrorException("@S3 operation can't have multiple method parameters for keys without key template", method);
        }
        if (parameters.isEmpty()) {
            if (required) {
                throw new ProcessingErrorException("@S3 operation must have at least one method parameter for keys", method);
            } else {
                return CodeBlock.of("(String) null");
            }
        }

        var firstParameter = parameters.get(0);
        if (CommonUtils.isCollection(firstParameter.asType())) {
            throw new ProcessingErrorException("@%s operation expected single result, but parameter is collection of keys".formatted(annotation), method);
        } else {
            return CodeBlock.of("String.valueOf($N)", firstParameter.toString());
        }
    }

    private static Key parseKey(ExecutableElement method, List<? extends VariableElement> parameters, String keyTemplate) {
        int indexStart = keyTemplate.indexOf("{");
        if (indexStart == -1) {
            return new Key(CodeBlock.of("$S", keyTemplate), Collections.emptyList());
        }

        var params = new ArrayList<VariableElement>();
        var builder = CodeBlock.builder();
        int indexEnd = 0;
        while (indexStart != -1) {
            if (indexStart != 0) {
                if (indexEnd == 0) {
                    builder.add("$S + ", keyTemplate.substring(0, indexStart));
                } else if (indexStart != (indexEnd + 1)) {
                    builder.add("$S + ", keyTemplate.substring(indexEnd + 1, indexStart));
                }
            }
            indexEnd = keyTemplate.indexOf("}", indexStart);

            var paramName = keyTemplate.substring(indexStart + 1, indexEnd);
            var parameter = parameters.stream()
                .filter(p -> p.getSimpleName().contentEquals(paramName))
                .findFirst()
                .orElseThrow(() -> new ProcessingErrorException("@S3 operation key part named '%s' expected, but wasn't found".formatted(paramName), method));

            if (CommonUtils.isCollection(parameter.asType()) || CommonUtils.isMap(parameter.asType())) {
                throw new ProcessingErrorException("@S3 operation key part '%s' can't be Collection or Map".formatted(paramName), method);
            }

            params.add(parameter);
            builder.add("$L", paramName);
            indexStart = keyTemplate.indexOf("{", indexEnd);
            if (indexStart != -1) {
                builder.add(" + ");
            }
        }

        if (indexEnd + 1 != keyTemplate.length()) {
            builder.add(" + $S", keyTemplate.substring(indexEnd + 1));
        }

        return new Key(builder.build(), params);
    }

    record Key(CodeBlock code, List<VariableElement> params) {}
}
