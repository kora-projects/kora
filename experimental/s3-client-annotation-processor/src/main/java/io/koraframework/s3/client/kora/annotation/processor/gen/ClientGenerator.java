package io.koraframework.s3.client.kora.annotation.processor.gen;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.NameUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.s3.client.kora.annotation.processor.S3ClassNames;
import io.koraframework.s3.client.kora.annotation.processor.S3ClientAnnotationProcessor;
import io.koraframework.s3.client.kora.annotation.processor.S3ClientUtils;

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
        var b = CommonUtils.extendsKeepAop(s3client, NameUtils.generatedType(s3client, "S3ClientImpl"))
            .addAnnotation(AnnotationUtils.generated(S3ClientAnnotationProcessor.class))
            .addField(S3ClassNames.CLIENT, "client", Modifier.PRIVATE, Modifier.FINAL)
            .addField(configType, "config", Modifier.PRIVATE, Modifier.FINAL);
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(String.class, "configPath")
            .addParameter(S3ClassNames.CLIENT_FACTORY, "clientFactory")
            .addParameter(configType, "clientConfig")
            .addStatement("this.client = clientFactory.create(configPath, $T.class, clientConfig)", s3client)
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
            throw new ProcessingErrorException(missingOperationError(method), method);
        }
        if (operations.size() > 1) {
            throw new ProcessingErrorException(multipleOperationsError(method), method);
        }
        var operation = operations.getFirst();
        return switch (operation.getAnnotationType().asElement().getSimpleName().toString()) {
            case "Get" -> generateGet(method, operation);
            case "List" -> generateList(method, operation);
            case "Head" -> generateHead(method, operation);
            case "Put" -> generatePut(method, operation);
            case "Delete" -> generateDelete(method, operation);
            default -> throw new IllegalStateException(unsupportedOperationInternalError(method, operation));
        };
    }

    private static MethodSpec generateDelete(ExecutableElement method, AnnotationMirror operation) {
        var b = CommonUtils.overridingKeepAop(method);
        generateCreds(method, b);
        generateBucket(method, b);
        b.addStatement("var _key = $L", generateKey(method, operation));
        var args = method.getParameters().stream().filter(p -> TypeName.get(p.asType()).equals(S3ClassNames.DELETE_OBJECT_ARGS)).findFirst().orElse(null);
        if (args == null) {
            b.addStatement("var _args = ($T) null", S3ClassNames.DELETE_OBJECT_ARGS);
        } else {
            b.addStatement("var _args = $N", args.getSimpleName());
        }
        b.addStatement("this.client.deleteObject(_creds, _bucket, _key, _args)");
        if (!TypeName.get(method.getReturnType()).equals(TypeName.VOID)) {
            throw new ProcessingErrorException(unexpectedReturnTypeError(method, "@S3.Delete", "void", TypeName.get(method.getReturnType())), method);
        }
        return b.build();
    }

    private static MethodSpec generatePut(ExecutableElement method, AnnotationMirror operation) {
        var b = CommonUtils.overridingKeepAop(method);
        generateCreds(method, b);
        generateBucket(method, b);
        b.addStatement("var _key = $L", generateKey(method, operation));
        var args = method.getParameters().stream().filter(p -> TypeName.get(p.asType()).equals(S3ClassNames.PUT_OBJECT_ARGS)).findFirst().orElse(null);
        if (args == null) {
            b.addStatement("var _args = ($T) null", S3ClassNames.PUT_OBJECT_ARGS);
        } else {
            b.addStatement("var _args = $N", args.getSimpleName());
        }
        var contents = method.getParameters().stream().filter(p -> S3ClassNames.BODY_TYPES.contains(TypeName.get(p.asType()))).toList();
        if (contents.isEmpty()) {
            throw new ProcessingErrorException("""
                S3 PUT operation '%s' has no upload body parameter.

                Fix: add exactly one body parameter with one of the supported types: %s.
                Example: String put(@S3.Bucket String bucket, String key, byte[] body)
                """.formatted(methodName(method), supportedBodyTypes()).trim(), method);
        }
        if (contents.size() != 1) {
            throw new ProcessingErrorException("""
                S3 PUT operation '%s' has %d upload body parameters, but only one body parameter is supported.

                Fix: keep exactly one body parameter with one of the supported types: %s.
                Example: String put(@S3.Bucket String bucket, String key, byte[] body)
                """.formatted(methodName(method), contents.size(), supportedBodyTypes()).trim(), method);
        }
        var returnType = TypeName.get(method.getReturnType());
        final boolean hasReturn;
        if (returnType.equals(ClassName.get(String.class))) {
            hasReturn = true;
        } else if (returnType.equals(TypeName.VOID)) {
            hasReturn = false;
        } else {
            throw new ProcessingErrorException(unexpectedReturnTypeError(method, "@S3.Put", "String or void", returnType), method);
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
            throw new ProcessingErrorException("""
                S3 PUT operation '%s' has unsupported body type '%s'.

                Fix: use one supported body type: %s.
                Example: String put(@S3.Bucket String bucket, String key, byte[] body)
                """.formatted(methodName(method), contentType, supportedBodyTypes()).trim(), method);
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
        b.addStatement("var _key = $L", generateKey(method, operation));
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
            throw new ProcessingErrorException(unexpectedReturnTypeError(method, "@S3.Head", "HeadObjectResult or @Nullable HeadObjectResult", returnType), method);
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
            b.addStatement("_args.prefix = $L", generateKey(method, operation));
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
        throw new ProcessingErrorException(
            unexpectedReturnTypeError(
                method,
                "@S3.List",
                "ListBucketResult, List<ListBucketResult.ListBucketItem>, List<String>, Iterator<ListBucketResult.ListBucketItem>, or Iterator<String>",
                returnType
            ),
            method
        );
    }

    private static MethodSpec generateGet(ExecutableElement method, AnnotationMirror operation) {
        var b = CommonUtils.overridingKeepAop(method);
        generateCreds(method, b);
        generateBucket(method, b);
        b.addStatement("var _key = $L", generateKey(method, operation));
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
            throw new ProcessingErrorException(unexpectedReturnTypeError(method, "@S3.Get", "GetObjectResult, @Nullable GetObjectResult, byte[], or @Nullable byte[]", returnType), method);
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
                throw new IllegalStateException(bucketIndexInternalError(method));
            }
            b.addStatement("var _bucket = this.bucketsConfig.bucket_$L", index);
        } else if (bucketOnClass != null) {
            var index = S3ClientUtils.parseConfigBuckets((TypeElement) method.getEnclosingElement())
                .indexOf(AnnotationUtils.<String>parseAnnotationValueWithoutDefault(bucketOnClass, "value"));
            if (index < 0) {
                throw new IllegalStateException(bucketIndexInternalError(method));
            }
            b.addStatement("var _bucket = this.bucketsConfig.bucket_$L", index);
        } else {
            throw new ProcessingErrorException("""
                S3 operation '%s' has no bucket source.

                Fix: provide the bucket in one of these ways:
                1. Add a runtime bucket parameter: GetObjectResult get(@S3.Bucket String bucket, String key)
                2. Configure it on the method: @S3.Bucket("s3.clients.default.bucket")
                3. Configure it on the @S3.Client interface: @S3.Bucket("s3.clients.default.bucket")
                """.formatted(methodName(method)).trim(), method);
        }
    }

    private static CodeBlock generateKey(ExecutableElement method, AnnotationMirror annotation) {
        var keyMapping = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(annotation, "value");
        var parameters = method.getParameters()
            .stream()
            .filter(p -> {
                var parameterTypeName = TypeName.get(p.asType());
                return !AnnotationUtils.isAnnotationPresent(p, S3ClassNames.Annotation.BUCKET)
                    && !S3ClassNames.S3_CREDENTIALS.equals(parameterTypeName)
                    && !S3ClassNames.ARGS.contains(parameterTypeName)
                    && !S3ClassNames.BODY_TYPES.contains(parameterTypeName)
                    ;
            })
            .toList();
        if (keyMapping != null && !keyMapping.isBlank()) {
            var key = parseKey(method, parameters, keyMapping);
            if (key.params().isEmpty() && !parameters.isEmpty()) {
                throw new ProcessingErrorException("""
                    S3 operation '%s' has key template '%s', but the template does not use any key parameters.

                    Fix: either reference method parameters in the template, or remove unused key parameters from the method.
                    Example: @S3.Get("users/{userId}/files/{fileId}")
                    """.formatted(methodName(method), keyMapping).trim(), method);
            }
            return key.code;
        }
        if (parameters.size() > 1) {
            throw new ProcessingErrorException("""
                S3 operation '%s' has %d key parameters, but no key template.

                Fix: add a template that names every key part, or leave exactly one key parameter.
                Example: @S3.Get("users/{userId}/files/{fileId}")
                """.formatted(methodName(method), parameters.size()).trim(), method);
        }
        if (parameters.isEmpty()) {
            throw new ProcessingErrorException("""
                S3 operation '%s' has no object key.

                Fix: add a key parameter, or specify a constant key in the operation annotation.
                Example: GetObjectResult get(@S3.Bucket String bucket, String key)
                Example: @S3.Get("constant-key")
                """.formatted(methodName(method)).trim(), method);
        }

        var firstParameter = parameters.get(0);
        if (CommonUtils.isCollection(firstParameter.asType())) {
            throw new ProcessingErrorException("""
                S3 operation '%s' expects one object key, but parameter '%s' is a collection.

                Fix: pass a single key value, or change the method to an operation that is intended to process multiple keys.
                Example: GetObjectResult get(@S3.Bucket String bucket, String key)
                """.formatted(methodName(method), firstParameter.getSimpleName()).trim(), method);
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
            if (indexEnd == -1) {
                throw new ProcessingErrorException("""
                    S3 operation '%s' has malformed key template '%s': missing closing '}'.

                    Fix: close every template parameter.
                    Example: @S3.Get("users/{userId}/files/{fileId}")
                    """.formatted(methodName(method), keyTemplate).trim(), method);
            }

            var paramName = keyTemplate.substring(indexStart + 1, indexEnd);
            var parameter = parameters.stream()
                .filter(p -> p.getSimpleName().contentEquals(paramName))
                .findFirst()
                .orElseThrow(() -> new ProcessingErrorException("""
                    S3 operation '%s' references key template parameter '{%s}', but the method has no matching key parameter.

                    Fix: rename the template parameter or add a method parameter named '%s'.
                    Example: @S3.Get("users/{%s}") GetObjectResult get(@S3.Bucket String bucket, String %s)
                    """.formatted(methodName(method), paramName, paramName, paramName, paramName).trim(), method));

            if (CommonUtils.isCollection(parameter.asType()) || CommonUtils.isMap(parameter.asType())) {
                throw new ProcessingErrorException("""
                    S3 operation '%s' uses '{%s}' in the key template, but parameter '%s' is a collection or map.

                    Fix: template parameters must be scalar values. Convert the collection to a single string before calling the S3 client method, or pass a scalar parameter.
                    Example: @S3.Get("users/{%s}") GetObjectResult get(@S3.Bucket String bucket, String %s)
                    """.formatted(methodName(method), paramName, paramName, paramName, paramName).trim(), method);
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

    private static String missingOperationError(ExecutableElement method) {
        return """
            S3 client method '%s' is not mapped to an S3 operation.

            Fix: annotate the method with exactly one S3 operation annotation: %s.
            Example: @S3.Get GetObjectResult get(@S3.Bucket String bucket, String key)
            """.formatted(methodName(method), supportedOperationAnnotations()).trim();
    }

    private static String multipleOperationsError(ExecutableElement method) {
        return """
            S3 client method '%s' has more than one S3 operation annotation.

            Fix: keep exactly one operation annotation: %s.
            Example: use @S3.Get or @S3.Put, not both on the same method.
            """.formatted(methodName(method), supportedOperationAnnotations()).trim();
    }

    private static String unexpectedReturnTypeError(ExecutableElement method, String operation, String expected, TypeName actual) {
        return """
            S3 operation '%s' on method '%s' has unsupported return type '%s'.

            Expected: %s.
            Fix: change the method return type to one of the supported types for %s.
            """.formatted(operation, methodName(method), actual, expected, operation).trim();
    }

    private static String unsupportedOperationInternalError(ExecutableElement method, AnnotationMirror operation) {
        return """
            Kora internal error: unsupported S3 operation annotation '%s' on method '%s'.

            This annotation passed the initial S3 operation filter but is not handled by the S3 client generator.
            """.formatted(operation.getAnnotationType(), methodName(method)).trim();
    }

    private static String bucketIndexInternalError(ExecutableElement method) {
        return """
            Kora internal error: S3 bucket config path for method '%s' was found on the declaration, but was not registered in the generated buckets config.

            This should not happen for a valid S3 client declaration. Please report this with the S3 client interface source.
            """.formatted(methodName(method)).trim();
    }

    private static String methodName(ExecutableElement method) {
        return method.getEnclosingElement() + "." + method.getSimpleName();
    }

    private static String supportedOperationAnnotations() {
        return String.join(", ", S3ClassNames.Annotation.OPERATIONS.stream()
            .map(cn -> "@S3." + cn.simpleName())
            .sorted()
            .toList());
    }

    private static String supportedBodyTypes() {
        return "%s, byte[], ByteBuffer, or InputStream".formatted(S3ClassNames.CONTENT_WRITER);
    }
}
