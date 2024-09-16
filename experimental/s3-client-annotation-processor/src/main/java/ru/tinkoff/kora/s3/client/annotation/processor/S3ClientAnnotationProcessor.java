package ru.tinkoff.kora.s3.client.annotation.processor;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.Module;
import ru.tinkoff.kora.s3.client.annotation.processor.S3Operation.ImplType;
import ru.tinkoff.kora.s3.client.annotation.processor.S3Operation.OperationType;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

public class S3ClientAnnotationProcessor extends AbstractKoraProcessor {

    private static final ClassName ANNOTATION_CLIENT = ClassName.get("ru.tinkoff.kora.s3.client.annotation", "S3", "Client");

    private static final ClassName ANNOTATION_OP_GET = ClassName.get("ru.tinkoff.kora.s3.client.annotation", "S3", "Get");
    private static final ClassName ANNOTATION_OP_LIST = ClassName.get("ru.tinkoff.kora.s3.client.annotation", "S3", "List");
    private static final ClassName ANNOTATION_OP_PUT = ClassName.get("ru.tinkoff.kora.s3.client.annotation", "S3", "Put");
    private static final ClassName ANNOTATION_OP_DELETE = ClassName.get("ru.tinkoff.kora.s3.client.annotation", "S3", "Delete");

    private static final ClassName CLASS_CONFIG = ClassName.get("ru.tinkoff.kora.s3.client", "S3Config");
    private static final ClassName CLASS_AWS_CONFIG = ClassName.get("ru.tinkoff.kora.s3.client.aws", "AwsS3ClientConfig");
    private static final ClassName CLASS_CLIENT_CONFIG = ClassName.get("ru.tinkoff.kora.s3.client", "S3ClientConfig");
    private static final ClassName CLASS_CLIENT_SIMPLE_SYNC = ClassName.get("ru.tinkoff.kora.s3.client", "S3KoraClient");
    private static final ClassName CLASS_CLIENT_SIMPLE_ASYNC = ClassName.get("ru.tinkoff.kora.s3.client", "S3KoraAsyncClient");
    private static final ClassName CLASS_CLIENT_AWS_SYNC = ClassName.get("software.amazon.awssdk.services.s3", "S3Client");
    private static final ClassName CLASS_CLIENT_AWS_ASYNC = ClassName.get("software.amazon.awssdk.services.s3", "S3AsyncClient");
    private static final ClassName CLASS_CLIENT_AWS_ASYNC_MULTIPART = ClassName.get("software.amazon.awssdk.services.s3.internal.multipart", "MultipartS3AsyncClient");
    private static final ClassName CLASS_INTERCEPTOR_AWS_CONTEXT_KEY = ClassName.get("ru.tinkoff.kora.s3.client.aws", "AwsS3ClientTelemetryInterceptor");
    private static final ClassName CLASS_INTERCEPTOR_AWS_OPERATION = ClassName.get("ru.tinkoff.kora.s3.client.aws", "AwsS3ClientTelemetryInterceptor", "Operation");
    private static final ClassName CLASS_CLIENT_AWS_TAG = ClassName.get("software.amazon.awssdk.awscore", "AwsClient");
    private static final ClassName CLASS_CLIENT_AWS_MULTIPART_TAG = ClassName.get("software.amazon.awssdk.services.s3.model", "MultipartUpload");

    private static final ClassName CLASS_S3_TELEMETRY = ClassName.get("ru.tinkoff.kora.s3.client.telemetry", "S3ClientTelemetry");
    private static final ClassName CLASS_S3_TELEMETRY_FACTORY = ClassName.get("ru.tinkoff.kora.s3.client.telemetry", "S3ClientTelemetryFactory");
    private static final ClassName CLASS_S3_EXCEPTION = ClassName.get("ru.tinkoff.kora.s3.client", "S3Exception");
    private static final ClassName CLASS_S3_EXCEPTION_NOT_FOUND = ClassName.get("ru.tinkoff.kora.s3.client", "S3NotFoundException");
    private static final ClassName CLASS_S3_BODY = ClassName.get("ru.tinkoff.kora.s3.client.model", "S3Body");
    private static final ClassName CLASS_S3_UPLOAD = ClassName.get("ru.tinkoff.kora.s3.client.model", "S3ObjectUpload");
    private static final ClassName CLASS_S3_BODY_BYTES = ClassName.get("ru.tinkoff.kora.s3.client.model", "ByteS3Body");
    private static final ClassName CLASS_S3_BODY_PUBLISHER = ClassName.get("ru.tinkoff.kora.s3.client.model", "PublisherS3Body");
    private static final ClassName CLASS_S3_OBJECT = ClassName.get("ru.tinkoff.kora.s3.client.model", "S3Object");
    private static final ClassName CLASS_S3_OBJECT_META = ClassName.get("ru.tinkoff.kora.s3.client.model", "S3ObjectMeta");
    private static final TypeName CLASS_S3_OBJECT_MANY = ParameterizedTypeName.get(ClassName.get(List.class), CLASS_S3_OBJECT);
    private static final TypeName CLASS_S3_OBJECT_META_MANY = ParameterizedTypeName.get(ClassName.get(List.class), CLASS_S3_OBJECT_META);
    private static final ClassName CLASS_S3_OBJECT_LIST = ClassName.get("ru.tinkoff.kora.s3.client.model", "S3ObjectList");
    private static final ClassName CLASS_S3_OBJECT_META_LIST = ClassName.get("ru.tinkoff.kora.s3.client.model", "S3ObjectMetaList");

    private static final ClassName CLASS_JDK_FLOW_ADAPTER = ClassName.get("reactor.adapter", "JdkFlowAdapter");

    private static final ClassName CLASS_AWS_EXCEPTION_NO_KEY = ClassName.get("software.amazon.awssdk.services.s3.model", "NoSuchKeyException");
    private static final ClassName CLASS_AWS_EXCEPTION_NO_BUCKET = ClassName.get("software.amazon.awssdk.services.s3.model", "NoSuchBucketException");
    private static final ClassName CLASS_AWS_EXCEPTION = ClassName.get("software.amazon.awssdk.awscore.exception", "AwsServiceException");
    private static final ClassName CLASS_AWS_IS_SYNC_BODY = ClassName.get("software.amazon.awssdk.core.sync", "RequestBody");
    private static final ClassName CLASS_AWS_IS_ASYNC_BODY = ClassName.get("software.amazon.awssdk.core.async", "AsyncRequestBody");
    private static final ClassName CLASS_AWS_IS_ASYNC_TRANSFORMER = ClassName.get("software.amazon.awssdk.core.async", "AsyncResponseTransformer");
    private static final ClassName CLASS_AWS_GET_REQUEST = ClassName.get("software.amazon.awssdk.services.s3.model", "GetObjectRequest");
    private static final ClassName CLASS_AWS_GET_RESPONSE = ClassName.get("software.amazon.awssdk.services.s3.model", "GetObjectResponse");
    private static final TypeName CLASS_AWS_GET_IS_RESPONSE = ParameterizedTypeName.get(ClassName.get("software.amazon.awssdk.core", "ResponseInputStream"), CLASS_AWS_GET_RESPONSE);
    private static final ClassName CLASS_AWS_DELETE_REQUEST = ClassName.get("software.amazon.awssdk.services.s3.model", "DeleteObjectRequest");
    private static final ClassName CLASS_AWS_DELETE_RESPONSE = ClassName.get("software.amazon.awssdk.services.s3.model", "DeleteObjectResponse");
    private static final ClassName CLASS_AWS_DELETES_REQUEST = ClassName.get("software.amazon.awssdk.services.s3.model", "DeleteObjectsRequest");
    private static final ClassName CLASS_AWS_DELETES_RESPONSE = ClassName.get("software.amazon.awssdk.services.s3.model", "DeleteObjectsResponse");
    private static final ClassName CLASS_AWS_LIST_REQUEST = ClassName.get("software.amazon.awssdk.services.s3.model", "ListObjectsV2Request");
    private static final ClassName CLASS_AWS_LIST_RESPONSE = ClassName.get("software.amazon.awssdk.services.s3.model", "ListObjectsV2Response");
    private static final ClassName CLASS_AWS_PUT_REQUEST = ClassName.get("software.amazon.awssdk.services.s3.model", "PutObjectRequest");
    private static final ClassName CLASS_AWS_PUT_RESPONSE = ClassName.get("software.amazon.awssdk.services.s3.model", "PutObjectResponse");

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ANNOTATION_CLIENT.canonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var annotation = processingEnv.getElementUtils().getTypeElement(ANNOTATION_CLIENT.canonicalName());
        if (annotation == null) {
            return false;
        }

        for (var element : roundEnv.getElementsAnnotatedWith(annotation)) {
            if (!element.getKind().isInterface()) {
                throw new ProcessingErrorException("@S3.Client annotation is intended to be used on interfaces, but was: " + element.getKind().name(), element);
            }

            var s3client = (TypeElement) element;
            var packageName = getPackage(s3client);

            try {
                TypeSpec spec = generateClient(s3client);
                var implFile = JavaFile.builder(packageName, spec).build();
                implFile.writeTo(processingEnv.getFiler());

                TypeSpec configSpec = generateClientConfig(s3client);
                var configFile = JavaFile.builder(packageName, configSpec).build();
                configFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        return false;
    }

    private TypeSpec generateClient(TypeElement s3client) {
        var implSpecBuilder = TypeSpec.classBuilder(NameUtils.generatedType(s3client, "Impl"))
            .addModifiers(Modifier.FINAL, Modifier.PUBLIC)
            .addAnnotation(AnnotationUtils.generated(S3ClientAnnotationProcessor.class))
            .addAnnotation(Component.class)
            .addSuperinterface(s3client.asType());

        final Set<Signature> constructed = new HashSet<>();
        var constructorBuilder = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        var constructorCode = CodeBlock.builder();
        implSpecBuilder.addField(CLASS_CLIENT_CONFIG, "_clientConfig", Modifier.PRIVATE, Modifier.FINAL);
        constructorCode.addStatement("this._clientConfig = clientConfig");
        constructorBuilder.addParameter(ParameterSpec.builder(CLASS_CLIENT_CONFIG, "clientConfig")
            .addAnnotation(TagUtils.makeAnnotationSpecForTypes(TypeName.get(s3client.asType())))
            .build());

        for (Element enclosedElement : s3client.getEnclosedElements()) {
            if (enclosedElement instanceof ExecutableElement method) {
                var operationType = getOperationType(method);
                if (operationType.isEmpty() && !method.getModifiers().contains(Modifier.DEFAULT)) {
                    throw new ProcessingErrorException("@S3.Client method without operation annotation can't be non default", method);
                } else if (operationType.isPresent()) {
                    S3Operation operation = getOperation(method, operationType.get());
                    MethodSpec methodSpec = MethodSpec.overriding(method)
                        .addCode(operation.code())
                        .build();

                    implSpecBuilder.addMethod(methodSpec);

                    final List<Signature> signatures = new ArrayList<>();
                    if (operation.impl() == ImplType.SIMPLE) {
                        if (operation.mode() == S3Operation.Mode.SYNC) {
                            signatures.add(new Signature(CLASS_CLIENT_SIMPLE_SYNC, "simpleSyncClient"));
                        } else {
                            signatures.add(new Signature(CLASS_CLIENT_SIMPLE_ASYNC, "simpleAsyncClient"));
                        }
                    } else if (operation.impl() == ImplType.AWS) {
                        if (operation.mode() == S3Operation.Mode.SYNC) {
                            signatures.add(new Signature(CLASS_CLIENT_AWS_SYNC, "awsSyncClient"));
                        } else {
                            signatures.add(new Signature(CLASS_CLIENT_AWS_ASYNC, "awsAsyncClient"));
                        }
                        if (operation.type() == OperationType.PUT) {
                            signatures.add(new Signature(CLASS_CLIENT_AWS_ASYNC_MULTIPART, "awsAsyncMultipartClient", List.of(CLASS_CLIENT_AWS_MULTIPART_TAG)));
                            signatures.add(new Signature(CLASS_CLIENT_AWS_ASYNC, "awsAsyncClient"));
                            signatures.add(new Signature(CLASS_AWS_CONFIG, "awsClientConfig"));
                            signatures.add(new Signature(ClassName.get(ExecutorService.class), "awsAsyncExecutor", List.of(CLASS_CLIENT_AWS_TAG)));
                        }
                    }

                    for (Signature signature : signatures) {
                        if (!constructed.contains(signature)) {
                            if (signature.tags().isEmpty()) {
                                constructorBuilder.addParameter(signature.type(), signature.name());
                            } else {
                                constructorBuilder.addParameter(ParameterSpec.builder(signature.type(), signature.name())
                                    .addAnnotation(TagUtils.makeAnnotationSpecForTypes(signature.tags()))
                                    .build());
                            }
                            implSpecBuilder.addField(signature.type(), "_" + signature.name, Modifier.PRIVATE, Modifier.FINAL);
                            constructorCode.addStatement("this._" + signature.name() + " = " + signature.name());
                            constructed.add(signature);
                        }
                    }
                }
            }
        }

        constructorBuilder.addCode(constructorCode.build());
        implSpecBuilder.addMethod(constructorBuilder.build());

        return implSpecBuilder.build();
    }

    private TypeSpec generateClientConfig(TypeElement s3client) {
        var clientAnnotation = AnnotationUtils.findAnnotation(s3client, ANNOTATION_CLIENT);
        final String clientConfigPath = AnnotationUtils.parseAnnotationValueWithoutDefault(clientAnnotation, "value");

        var extractorClass = ParameterizedTypeName.get(CommonClassNames.configValueExtractor, CLASS_CLIENT_CONFIG);
        return TypeSpec.interfaceBuilder(NameUtils.generatedType(s3client, "ClientConfigModule"))
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationUtils.generated(S3ClientAnnotationProcessor.class))
            .addAnnotation(AnnotationSpec.builder(Module.class).build())
            .addOriginatingElement(s3client)
            .addMethod(MethodSpec.methodBuilder("clientConfig")
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addAnnotation(TagUtils.makeAnnotationSpecForTypes(TypeName.get(s3client.asType())))
                .addParameter(ParameterSpec.builder(CommonClassNames.config, "config").build())
                .addParameter(ParameterSpec.builder(extractorClass, "extractor").build())
                .addStatement("var value = config.get($S)", clientConfigPath)
                .addStatement("return $T.ofNullable(extractor.extract(value)).orElseThrow(() -> $T.missingValueAfterParse(value))", Optional.class, CommonClassNames.configValueExtractionException)
                .returns(CLASS_CLIENT_CONFIG)
                .build())
            .build();
    }

    record Signature(TypeName type, String name, List<TypeName> tags) {

        public Signature(TypeName type, String name) {
            this(type, name, Collections.emptyList());
        }
    }

    record OperationMeta(OperationType type, AnnotationMirror annotation) {}

    private Optional<OperationMeta> getOperationType(ExecutableElement method) {
        Optional<OperationMeta> value = Optional.empty();

        for (AnnotationMirror annotationMirror : method.getAnnotationMirrors()) {
            OperationType type = null;
            if (ClassName.get(annotationMirror.getAnnotationType()).equals(ANNOTATION_OP_GET)) {
                type = OperationType.GET;
            } else if (ClassName.get(annotationMirror.getAnnotationType()).equals(ANNOTATION_OP_LIST)) {
                type = OperationType.LIST;
            } else if (ClassName.get(annotationMirror.getAnnotationType()).equals(ANNOTATION_OP_PUT)) {
                type = OperationType.PUT;
            } else if (ClassName.get(annotationMirror.getAnnotationType()).equals(ANNOTATION_OP_DELETE)) {
                type = OperationType.DELETE;
            }

            if (value.isEmpty() && type != null) {
                value = Optional.of(new OperationMeta(type, annotationMirror));
            } else {
                throw new ProcessingErrorException("@S3.Client method must be annotated with single operation annotation", method);
            }
        }

        return value;
    }

    private S3Operation getOperation(ExecutableElement method, OperationMeta operationMeta) {
        if (MethodUtils.isPublisher(method)) {
            throw new ProcessingErrorException("@S3.%s operation method return signature can't be: Publisher".formatted(
                operationMeta.type().name()), method);
        } else if (MethodUtils.isFlux(method)) {
            throw new ProcessingErrorException("@S3.%s operation method return signature can't be: Flux".formatted(
                operationMeta.type().name()), method);
        }

        final S3Operation.Mode mode = MethodUtils.isFuture(method) || MethodUtils.isMono(method)
            ? S3Operation.Mode.ASYNC
            : S3Operation.Mode.SYNC;

        if (OperationType.GET == operationMeta.type) {
            return operationGET(method, operationMeta, mode);
        } else if (OperationType.LIST == operationMeta.type) {
            return operationLIST(method, operationMeta, mode);
        } else if (OperationType.PUT == operationMeta.type) {
            return operationPUT(method, operationMeta, mode);
        } else if (OperationType.DELETE == operationMeta.type) {
            return operationDELETE(method, operationMeta, mode);
        } else {
            throw new UnsupportedOperationException("Unsupported S3 operation type");
        }
    }

    private S3Operation operationGET(ExecutableElement method, OperationMeta operationMeta, S3Operation.Mode mode) {
        final String keyMapping = AnnotationUtils.parseAnnotationValueWithoutDefault(operationMeta.annotation, "value");
        final Key key;
        final VariableElement firstParameter = method.getParameters().stream().findFirst().orElse(null);
        if (keyMapping != null && !keyMapping.isBlank()) {
            key = parseKey(method, keyMapping);
            if (key.params().isEmpty() && !method.getParameters().isEmpty()) {
                throw new ProcessingErrorException("@S3.Get operation key template must use method arguments or they should be removed", method);
            }
        } else if (method.getParameters().size() > 1) {
            throw new ProcessingErrorException("@S3.Get operation can't have multiple method parameters for keys without key template", method);
        } else if (method.getParameters().isEmpty()) {
            throw new ProcessingErrorException("@S3.Get operation must have key parameter", method);
        } else {
            key = new Key(CodeBlock.of("var _key = String.valueOf($L)", firstParameter.toString()), List.of(firstParameter));
        }

        boolean isOptional = MethodUtils.isOptional(method);
        boolean isMono = MethodUtils.isMono(method);
        final TypeName returnType;
        if (mode == S3Operation.Mode.SYNC) {
            if (isOptional) {
                returnType = ClassName.get(MethodUtils.getGenericType(method.getReturnType()).orElseThrow());
            } else {
                returnType = ClassName.get(method.getReturnType());
            }
        } else {
            returnType = ClassName.get(MethodUtils.getGenericType(method.getReturnType()).orElseThrow());
        }

        var codeBuilder = CodeBlock.builder();
        for (VariableElement parameter : method.getParameters()) {
            if(!(method.getReturnType() instanceof PrimitiveType)) {
                codeBuilder.add("""
                    if($T.isNull($L)) {
                        throw new $T("S3.Get request key argument expected, but was null for arg: $L");
                    }
                    """, Objects.class, parameter.getSimpleName().toString(), IllegalArgumentException.class, parameter.getSimpleName().toString());
            }
        }
        codeBuilder.add("\n");

        if (CLASS_S3_OBJECT.equals(returnType) || CLASS_S3_OBJECT_META.equals(returnType)) {
            if (firstParameter != null && CommonUtils.isCollection(firstParameter.asType())) {
                throw new ProcessingErrorException("@S3.Get operation expected single result, but parameter is collection of keys", method);
            }

            var bodyBuilder = CodeBlock.builder();
            if (mode == S3Operation.Mode.SYNC) {
                bodyBuilder.add("_simpleSyncClient");
            } else {
                bodyBuilder.add("_simpleAsyncClient");
            }

            if (CLASS_S3_OBJECT.equals(returnType)) {
                bodyBuilder.add(".get(_clientConfig.bucket(), _key)");
            } else {
                bodyBuilder.add(".getMeta(_clientConfig.bucket(), _key)");
            }

            if (mode == S3Operation.Mode.ASYNC && CompletableFuture.class.getCanonicalName().equals(((DeclaredType) method.getReturnType()).asElement().toString())) {
                bodyBuilder.add(".toCompletableFuture()");
            }

            codeBuilder.addStatement(key.code());
            if (isOptional) {
                codeBuilder
                    .beginControlFlow("try")
                    .add("return $T.of(", Optional.class)
                    .add(bodyBuilder.build())
                    .add(");\n")
                    .nextControlFlow("catch($T e)", CLASS_S3_EXCEPTION_NOT_FOUND)
                    .addStatement("return $T.empty()", Optional.class)
                    .endControlFlow();
            } else if (isMono) {
                codeBuilder
                    .beginControlFlow("return $T.fromCompletionStage(() -> ", CommonClassNames.mono)
                    .add("return ").add(bodyBuilder.build()).add("\n")
                    .add("""
                            .exceptionallyCompose(e -> {
                                Throwable cause = e;
                                if(e instanceof $T) {
                                    cause = e.getCause();
                                }
                                
                                if(cause instanceof $T) {
                                    return $T.completedFuture(null);
                                } else {
                                    return $T.failedFuture(cause);
                                }
                            });
                        """, CompletionException.class, CLASS_S3_EXCEPTION_NOT_FOUND, CompletableFuture.class, CompletableFuture.class)
                    .endControlFlow(")");
            } else {
                codeBuilder.add("return ").add(bodyBuilder.build()).add(";\n");
            }

            return new S3Operation(method, operationMeta.annotation, OperationType.GET, ImplType.SIMPLE, mode, codeBuilder.build());
        } else if (CLASS_S3_OBJECT_MANY.equals(returnType) || CLASS_S3_OBJECT_META_MANY.equals(returnType)) {
            if (firstParameter != null && !CommonUtils.isCollection(firstParameter.asType())) {
                throw new ProcessingErrorException("@S3.Get operation expected many results, but parameter isn't collection of keys", method);
            } else if (keyMapping != null && !keyMapping.isBlank()) {
                throw new ProcessingErrorException("@S3.Get operation expected many results, key template can't be specified for collection of keys", method);
            } else if (isOptional) {
                throw new ProcessingErrorException("@S3.Get operation with multiple keys, can't be return type Optional<T>", method);
            }

            var bodyBuilder = CodeBlock.builder();

            String clientField = mode == S3Operation.Mode.SYNC
                ? "_simpleSyncClient"
                : "_simpleAsyncClient";

            if (isMono) {
                bodyBuilder.beginControlFlow("return $T.fromCompletionStage(() -> ", CommonClassNames.mono);
            }

            if (CLASS_S3_OBJECT_MANY.equals(returnType)) {
                bodyBuilder.add("return $L.get(_clientConfig.bucket(), $L)",
                    clientField, firstParameter.getSimpleName().toString());
            } else {
                bodyBuilder.add("return $L.getMeta(_clientConfig.bucket(), $L)",
                    clientField, firstParameter.getSimpleName().toString());
            }

            if (mode == S3Operation.Mode.ASYNC && CompletableFuture.class.getCanonicalName().equals(((DeclaredType) method.getReturnType()).asElement().toString())) {
                bodyBuilder.add(".toCompletableFuture()");
            }
            bodyBuilder.add(";\n");

            if (isMono) {
                bodyBuilder.endControlFlow(")");
            }

            return new S3Operation(method, operationMeta.annotation, OperationType.GET, ImplType.SIMPLE, mode, bodyBuilder.build());
        } else if (CLASS_AWS_GET_RESPONSE.equals(returnType) || CLASS_AWS_GET_IS_RESPONSE.equals(returnType)) {
            if (firstParameter != null && CommonUtils.isCollection(firstParameter.asType())) {
                throw new ProcessingErrorException("@S3.Get operation expected single result, but parameter is collection of keys", method);
            }

            String clientField = mode == S3Operation.Mode.SYNC
                ? "_awsSyncClient"
                : "_awsAsyncClient";

            codeBuilder
                .addStatement(key.code())
                .add("\n")
                .addStatement(CodeBlock.of("""
                    var _request = $T.builder()
                        .bucket(_clientConfig.bucket())
                        .key(_key)
                        .build()""", CLASS_AWS_GET_REQUEST))
                .add("\n");

            if (isMono) {
                codeBuilder.beginControlFlow("return $T.fromCompletionStage(() -> ", CommonClassNames.mono);
            }

            if (mode == S3Operation.Mode.SYNC) {
                if (isOptional) {
                    codeBuilder.beginControlFlow("try");
                    codeBuilder.add("return $T.of(", Optional.class);
                } else {
                    codeBuilder.add("return ");
                }

                if (CLASS_AWS_GET_RESPONSE.equals(returnType)) {
                    codeBuilder.add("$L.getObject(_request).response()", clientField);
                } else {
                    codeBuilder.add("$L.getObject(_request)", clientField);
                }

                if (isOptional) {
                    codeBuilder
                        .addStatement(")")
                        .nextControlFlow("catch($T | $T e)", CLASS_AWS_EXCEPTION_NO_KEY, CLASS_AWS_EXCEPTION_NO_BUCKET)
                        .addStatement("return $T.empty()", Optional.class)
                        .endControlFlow();
                } else {
                    codeBuilder.add(";\n");
                }
            } else {
                if (CLASS_AWS_GET_RESPONSE.equals(returnType)) {
                    codeBuilder.add("return $L.getObject(_request, $T.toBlockingInputStream()).thenApply(_r -> _r.response())",
                        clientField, CLASS_AWS_IS_ASYNC_TRANSFORMER);
                } else {
                    codeBuilder.add("return $L.getObject(_request, $T.toBlockingInputStream())",
                        clientField, CLASS_AWS_IS_ASYNC_TRANSFORMER);
                }

                if (isMono) {
                    codeBuilder
                        .add("\n")
                        .add("""
                                .exceptionallyCompose(e -> {
                                    Throwable cause = e;
                                    if(e instanceof $T) {
                                        cause = e.getCause();
                                    }
                                    
                                    if(cause instanceof $T) {
                                        return $T.completedFuture(null);
                                    } else {
                                        return $T.failedFuture(cause);
                                    }
                                });
                            """, CompletionException.class, CLASS_S3_EXCEPTION_NOT_FOUND, CompletableFuture.class, CompletableFuture.class);
                } else if (isOptional) {
                    codeBuilder.add(";\n");
                } else {
                    codeBuilder.add(";\n");
                }
            }

            if (isMono) {
                codeBuilder.endControlFlow(")");
            }

            return new S3Operation(method, operationMeta.annotation, OperationType.GET, ImplType.AWS, mode, codeBuilder.build());
        } else {
            if (firstParameter != null && CommonUtils.isCollection(firstParameter.asType())) {
                throw new ProcessingErrorException("@S3.Get operation unsupported method return signature, expected any of List<%s>/List<%s>".formatted(
                    CLASS_S3_OBJECT.simpleName(), CLASS_S3_OBJECT_META.simpleName()
                ), method);
            } else {
                throw new ProcessingErrorException("@S3.Get operation unsupported method return signature, expected any of %s/%s/%s/ResponseInputStream<%s>".formatted(
                    CLASS_S3_OBJECT.simpleName(), CLASS_S3_OBJECT_META.simpleName(), CLASS_AWS_GET_RESPONSE.simpleName(), CLASS_AWS_GET_RESPONSE.simpleName()
                ), method);
            }
        }
    }

    private S3Operation operationLIST(ExecutableElement method, OperationMeta operationMeta, S3Operation.Mode mode) {
        final String keyMapping = AnnotationUtils.parseAnnotationValueWithoutDefault(operationMeta.annotation, "value");
        final Key key;
        final VariableElement firstParameter = method.getParameters().stream().findFirst().orElse(null);
        if (keyMapping != null && !keyMapping.isBlank()) {
            key = parseKey(method, keyMapping);
            if (key.params().isEmpty() && !method.getParameters().isEmpty()) {
                throw new ProcessingErrorException("@S3.List operation prefix template must use method arguments or they should be removed", method);
            }
        } else if (method.getParameters().size() > 1) {
            throw new ProcessingErrorException("@S3.List operation can't have multiple method parameters for keys without key template", method);
        } else if (method.getParameters().isEmpty()) {
            key = null;
        } else if (CommonUtils.isCollection(firstParameter.asType())) {
            throw new ProcessingErrorException("@S3.List operation expected single result, but parameter is collection of keys", method);
        } else {
            key = new Key(CodeBlock.of("var _key = String.valueOf($L)", firstParameter.toString()), List.of(firstParameter));
        }

        if (MethodUtils.isOptional(method)) {
            throw new ProcessingErrorException("@S3.List operation, can't be return type Optional<T>", method);
        }

        boolean isMono = MethodUtils.isMono(method);
        final Integer limit = AnnotationUtils.parseAnnotationValue(elements, operationMeta.annotation(), "limit");
        final String delimiter = AnnotationUtils.parseAnnotationValueWithoutDefault(operationMeta.annotation(), "delimiter");
        final TypeName returnType = (mode == S3Operation.Mode.SYNC)
            ? ClassName.get(method.getReturnType())
            : ClassName.get(MethodUtils.getGenericType(method.getReturnType()).orElseThrow());

        var codeBuilder = CodeBlock.builder();
        for (VariableElement parameter : method.getParameters()) {
            if(!(method.getReturnType() instanceof PrimitiveType)) {
                codeBuilder.add("""
                    if($T.isNull($L)) {
                        throw new $T("S3.List request prefix argument expected, but was null for arg: $L");
                    }
                    """, Objects.class, parameter.getSimpleName().toString(), IllegalArgumentException.class, parameter.getSimpleName().toString());

            }
        }
        codeBuilder.add("\n");

        if (CLASS_S3_OBJECT_LIST.equals(returnType) || CLASS_S3_OBJECT_META_LIST.equals(returnType)) {
            if (key != null) {
                codeBuilder.addStatement(key.code());
            }

            if (isMono) {
                codeBuilder.beginControlFlow("return $T.fromCompletionStage(() -> ", CommonClassNames.mono);
            }

            var bodyBuilder = CodeBlock.builder();
            if (mode == S3Operation.Mode.SYNC) {
                bodyBuilder.add("return _simpleSyncClient");
            } else {
                bodyBuilder.add("return _simpleAsyncClient");
            }

            String keyField = (key == null) ? "(String) null" : "_key";
            if (CLASS_S3_OBJECT_LIST.equals(returnType)) {
                bodyBuilder.add(".list(_clientConfig.bucket(), $L, $S, $L)", keyField, delimiter, limit);
            } else {
                bodyBuilder.add(".listMeta(_clientConfig.bucket(), $L, $S, $L)", keyField, delimiter, limit);
            }

            if (mode == S3Operation.Mode.ASYNC && CompletableFuture.class.getCanonicalName().equals(((DeclaredType) method.getReturnType()).asElement().toString())) {
                bodyBuilder.add(".toCompletableFuture()");
            }
            bodyBuilder.add(";\n");

            codeBuilder.add(bodyBuilder.build());

            if (isMono) {
                codeBuilder.endControlFlow(")");
            }

            return new S3Operation(method, operationMeta.annotation, OperationType.LIST, ImplType.SIMPLE, mode, codeBuilder.build());
        } else if (CLASS_AWS_LIST_RESPONSE.equals(returnType)) {
            String clientField = mode == S3Operation.Mode.SYNC
                ? "_awsSyncClient"
                : "_awsAsyncClient";

            if (key != null) {
                codeBuilder.addStatement(key.code()).add("\n");
            }

            String keyField = (key == null) ? "null" : "_key";
            codeBuilder
                .addStatement(CodeBlock.of("""
                    var _request = $L.builder()
                        .bucket(_clientConfig.bucket())
                        .prefix($L)
                        .delimiter($S)
                        .maxKeys($L)
                        .build()""", CLASS_AWS_LIST_REQUEST, keyField, delimiter, limit))
                .add("\n");

            if (MethodUtils.isMono(method)) {
                codeBuilder.beginControlFlow("return $T.fromCompletionStage(() -> ", CommonClassNames.mono);
            }

            codeBuilder
                .addStatement("return $L.listObjectsV2(_request)", clientField)
                .build();

            if (MethodUtils.isMono(method)) {
                codeBuilder.endControlFlow(")");
            }

            return new S3Operation(method, operationMeta.annotation, OperationType.LIST, ImplType.AWS, mode, codeBuilder.build());
        } else {
            throw new ProcessingErrorException("@S3.List operation unsupported method return signature, expected any of %s/%s/%s".formatted(
                CLASS_S3_OBJECT.simpleName(), CLASS_S3_OBJECT_LIST.simpleName(), CLASS_AWS_LIST_RESPONSE.simpleName()
            ), method);
        }
    }

    private S3Operation operationPUT(ExecutableElement method, OperationMeta operationMeta, S3Operation.Mode mode) {
        final String keyMapping = AnnotationUtils.parseAnnotationValueWithoutDefault(operationMeta.annotation, "value");
        final Key key;

        var keyParameters = method.getParameters().stream()
            .filter(p -> {
                TypeName bodyType = ClassName.get(p.asType());
                return !CLASS_S3_BODY.equals(bodyType)
                       && !ClassName.get(ByteBuffer.class).equals(bodyType)
                       && !ArrayTypeName.get(byte[].class).equals(bodyType);
            })
            .toList();

        final VariableElement firstParameter = keyParameters.stream().findFirst().orElse(null);
        if (keyMapping != null && !keyMapping.isBlank()) {
            key = parseKey(method, keyMapping);
            if (key.params().isEmpty() && !keyParameters.isEmpty()) {
                throw new ProcessingErrorException("@S3.Put operation key template must use method arguments or they should be removed", method);
            }
        } else if (CommonUtils.isCollection(firstParameter.asType())) {
            throw new ProcessingErrorException("@S3.Put operation expected single result, but parameter is collection of keys", method);
        } else {
            key = new Key(CodeBlock.of("var _key = String.valueOf($L)", firstParameter.toString()), List.of(firstParameter));
        }

        if (MethodUtils.isOptional(method)) {
            throw new ProcessingErrorException("@S3.Put operation, can't be return type Optional<T>", method);
        }

        final TypeMirror returnTypeMirror = (mode == S3Operation.Mode.SYNC)
            ? method.getReturnType()
            : MethodUtils.getGenericType(method.getReturnType()).orElseThrow();
        final TypeName returnType = ClassName.get(returnTypeMirror);

        final VariableElement bodyParam = method.getParameters().stream()
            .filter(p -> key.params().stream().noneMatch(kp -> p == kp))
            .findFirst()
            .orElseThrow(() -> new ProcessingErrorException("@S3.Put operation body parameter not found", method));

        TypeName bodyType = ClassName.get(bodyParam.asType());

        final boolean isMono = MethodUtils.isMono(method);
        final boolean isUploadResponse = CLASS_S3_UPLOAD.equals(returnType);
        final boolean isAwsResponse = CLASS_AWS_PUT_RESPONSE.equals(returnType);

        var methodBuilder = CodeBlock.builder();
        for (VariableElement parameter : method.getParameters()) {
            if(!(method.getReturnType() instanceof PrimitiveType)) {
                methodBuilder.add("""
                    if($T.isNull($L)) {
                        throw new $T("S3.Put request argument expected, but was null for arg: $L");
                    }
                    """, Objects.class, parameter.getSimpleName().toString(), IllegalArgumentException.class, parameter.getSimpleName().toString());
            }
        }
        methodBuilder.add("\n");

        if (CommonUtils.isVoid(returnTypeMirror) || isUploadResponse) {
            CodeBlock bodyCode;
            if (CLASS_S3_BODY.equals(bodyType)) {
                bodyCode = CodeBlock.of("var _body = $L", bodyParam.getSimpleName().toString());
            } else {
                final String methodCall;
                if (ClassName.get(ByteBuffer.class).equals(bodyType)) {
                    methodCall = "ofBuffer";
                } else if (ArrayTypeName.get(byte[].class).equals(bodyType)) {
                    methodCall = "ofBytes";
                } else {
                    throw new ProcessingErrorException("@S3.Put operation body must be S3Body/bytes/ByteBuffer", method);
                }

                final String type = AnnotationUtils.parseAnnotationValueWithoutDefault(operationMeta.annotation(), "type");
                final String encoding = AnnotationUtils.parseAnnotationValueWithoutDefault(operationMeta.annotation(), "encoding");
                if (type != null && encoding != null) {
                    bodyCode = CodeBlock.of("var _body = $T.$L($L, $S, $S)",
                        CLASS_S3_BODY, methodCall, bodyParam.getSimpleName().toString(), methodCall, type, encoding);
                } else if (type != null) {
                    bodyCode = CodeBlock.of("var _body = $T.$L($L, $S)",
                        CLASS_S3_BODY, methodCall, bodyParam.getSimpleName().toString(), methodCall, type);
                } else if (encoding != null) {
                    bodyCode = CodeBlock.of("var _body = $T.$L($L, null, $S)",
                        CLASS_S3_BODY, methodCall, bodyParam.getSimpleName().toString(), methodCall, encoding);
                } else {
                    bodyCode = CodeBlock.of("var _body = $T.$L($L)",
                        CLASS_S3_BODY, methodCall, bodyParam.getSimpleName().toString());
                }
            }

            if (mode == S3Operation.Mode.SYNC) {
                if (isUploadResponse) {
                    methodBuilder.add("return _simpleSyncClient.put(_clientConfig.bucket(), _key, _body)");
                } else {
                    methodBuilder.add("_simpleSyncClient.put(_clientConfig.bucket(), _key, _body)");
                }
                methodBuilder.add(";\n");
            } else {
                methodBuilder.add("return _simpleAsyncClient.put(_clientConfig.bucket(), _key, _body)");
                if (!isUploadResponse) {
                    methodBuilder.add(".thenAccept(_v -> {})");
                }
                if (CompletableFuture.class.getCanonicalName().equals(((DeclaredType) method.getReturnType()).asElement().toString())) {
                    methodBuilder.add(".toCompletableFuture()");
                }
                methodBuilder.add(";\n");
            }

            var bodyBuilder = CodeBlock.builder()
                .addStatement(key.code())
                .add("\n");

            if (isMono) {
                bodyBuilder.beginControlFlow("return $T.fromCompletionStage(() -> ", CommonClassNames.mono);
            }

            bodyBuilder
                .addStatement(bodyCode)
                .add("\n")
                .add(methodBuilder.build());

            if (isMono) {
                bodyBuilder.endControlFlow(")");
            }

            return new S3Operation(method, operationMeta.annotation, OperationType.PUT, ImplType.SIMPLE, mode, bodyBuilder.build());
        } else if (isAwsResponse) {
            CodeBlock bodyCode;
            var requestBuilder = CodeBlock.builder();
            final String type = AnnotationUtils.parseAnnotationValueWithoutDefault(operationMeta.annotation(), "type");
            final String encoding = AnnotationUtils.parseAnnotationValueWithoutDefault(operationMeta.annotation(), "encoding");
            final String bodyParamName = bodyParam.getSimpleName().toString();

            if (CLASS_S3_BODY.equals(bodyType)) {
                if (mode == S3Operation.Mode.SYNC) {
                    bodyCode = CodeBlock.builder()
                        .add("final $T _requestBody;\n", CLASS_AWS_IS_SYNC_BODY)
                        .beginControlFlow("if ($L instanceof $T _bb)", bodyParamName, CLASS_S3_BODY_BYTES)
                        .add("_requestBody = $T.fromBytes(_bb.bytes());\n", CLASS_AWS_IS_SYNC_BODY)
                        .nextControlFlow("else")
                        .add("_requestBody = $T.fromContentProvider(() -> $L.asInputStream(), $L.size(), $L.type());\n", CLASS_AWS_IS_SYNC_BODY, bodyParamName, bodyParamName, bodyParamName)
                        .endControlFlow()
                        .build();
                } else {
                    bodyCode = CodeBlock.builder()
                        .add("final $T _requestBody;\n", CLASS_AWS_IS_ASYNC_BODY)
                        .beginControlFlow("if ($L instanceof $T _bb)", bodyParamName, CLASS_S3_BODY_BYTES)
                        .add("_requestBody = $T.fromBytes(_bb.bytes());\n", CLASS_AWS_IS_ASYNC_BODY)
                        .nextControlFlow("else if($L instanceof $T _bb)", bodyParamName, CLASS_S3_BODY_PUBLISHER)
                        .add("_requestBody = $T.fromPublisher($T.flowPublisherToFlux(_bb.asPublisher()));\n", CLASS_AWS_IS_ASYNC_BODY, CLASS_JDK_FLOW_ADAPTER)
                        .nextControlFlow("else", bodyParamName, bodyParamName)
                        .add("final Long _bodySize = $L.size() > 0 ? $L.size() : null;\n", bodyParamName, bodyParamName)
                        .add("_requestBody = $T.fromInputStream($L.asInputStream(), _bodySize, _awsAsyncExecutor);\n", CLASS_AWS_IS_ASYNC_BODY, bodyParamName)
                        .endControlFlow()
                        .build();
                }

                requestBuilder.addStatement("_requestBuilder.contentLength($L.size() > 0 ? $L.size() : null)", bodyParamName, bodyParamName);
                if (type != null) {
                    requestBuilder.addStatement("_requestBuilder.contentType($S)", type);
                } else {
                    requestBuilder.addStatement("_requestBuilder.contentType($L.type())", bodyParamName);
                }
                if (encoding != null) {
                    requestBuilder.addStatement("_requestBuilder.contentEncoding($S)", encoding);
                } else {
                    requestBuilder.addStatement("_requestBuilder.contentEncoding($L.encoding())", bodyParamName);
                }
            } else {
                var awsBodyClass = mode == S3Operation.Mode.SYNC
                    ? CLASS_AWS_IS_SYNC_BODY
                    : CLASS_AWS_IS_ASYNC_BODY;

                if (ClassName.get(ByteBuffer.class).equals(bodyType)) {
                    bodyCode = CodeBlock.of("var _requestBody = $T.fromByteBuffer($L)",
                        awsBodyClass, bodyParamName);
                    requestBuilder.addStatement("_requestBuilder.contentLength($L.remaining())", bodyParamName);
                } else if (ArrayTypeName.get(byte[].class).equals(bodyType)) {
                    bodyCode = CodeBlock.of("var _requestBody = $T.fromBytes($L)",
                        awsBodyClass, bodyParamName);
                    requestBuilder.addStatement("_requestBuilder.contentLength($L.length)", bodyParamName);
                } else {
                    throw new ProcessingErrorException("@S3.Put operation body must be S3Body/bytes/ByteBuffer", method);
                }

                if (type != null) {
                    requestBuilder.addStatement("_requestBuilder.contentType($S)", type);
                }
                if (encoding != null) {
                    requestBuilder.addStatement("_requestBuilder.contentEncoding($S)", encoding);
                }
            }

            methodBuilder
                .addStatement(key.code())
                .add("\n")
                .addStatement("""
                    var _requestBuilder = $T.builder()
                        .bucket(_clientConfig.bucket())
                        .key(_key)""", CLASS_AWS_PUT_REQUEST)
                .add(requestBuilder.build())
                .add("\n")
                .addStatement("var _request = _requestBuilder.build()")
                .add("\n");

            if (MethodUtils.isMono(method)) {
                methodBuilder.beginControlFlow("return $T.fromCompletionStage(() -> ", CommonClassNames.mono);
            }

            if (CLASS_S3_BODY.equals(bodyType) && mode == S3Operation.Mode.SYNC) {
                methodBuilder.add("""
                        if ($L instanceof $T pb) {
                            return _awsAsyncClient.putObject(_request, $T.fromPublisher($T.flowPublisherToFlux(pb.asPublisher()))).join();
                        } else if($L.size() < 0 || $L.size() > _awsClientConfig.upload().partSize().toBytes()) {
                            final Long _bodySize = $L.size() > 0 ? $L.size() : null;
                            return _awsAsyncMultipartClient.putObject(_request, $T.fromInputStream($L.asInputStream(), _bodySize, _awsAsyncExecutor)).join();
                        }
                        """,
                    bodyParamName, CLASS_S3_BODY_PUBLISHER,
                    CLASS_AWS_IS_ASYNC_BODY, CLASS_JDK_FLOW_ADAPTER,
                    bodyParamName, bodyParamName,
                    bodyParamName, bodyParamName,
                    CLASS_AWS_IS_ASYNC_BODY, bodyParamName);
            }

            methodBuilder
                .add("\n")
                .add(bodyCode)
                .add("\n");

            if (mode == S3Operation.Mode.ASYNC) {
                methodBuilder
                    .addStatement("""
                        return ($L.size() > 0 && $L.size() <= _awsClientConfig.upload().partSize().toBytes())
                            ? _awsAsyncClient.putObject(_request, _requestBody)
                            : _awsAsyncMultipartClient.putObject(_request, _requestBody)""", bodyParamName, bodyParamName);
            } else {
                methodBuilder.addStatement("return _awsSyncClient.putObject(_request, _requestBody)");
            }

            if (MethodUtils.isMono(method)) {
                methodBuilder.endControlFlow(")");
            }

            return new S3Operation(method, operationMeta.annotation, OperationType.PUT, ImplType.AWS, mode, methodBuilder.build());
        } else {
            throw new ProcessingErrorException("@S3.Put operation unsupported method return signature, expected any of Void/%s/%s".formatted(
                CLASS_S3_UPLOAD.simpleName(), CLASS_AWS_PUT_RESPONSE.simpleName()
            ), method);
        }
    }

    private S3Operation operationDELETE(ExecutableElement method, OperationMeta operationMeta, S3Operation.Mode mode) {
        final String keyMapping = AnnotationUtils.parseAnnotationValueWithoutDefault(operationMeta.annotation, "value");
        final Key key;
        final VariableElement firstParameter = method.getParameters().stream().findFirst().orElse(null);
        if (keyMapping != null && !keyMapping.isBlank()) {
            key = parseKey(method, keyMapping);
            if (key.params().isEmpty() && !method.getParameters().isEmpty()) {
                throw new ProcessingErrorException("@S3.Delete operation key template must use method arguments or they should be removed", method);
            }
        } else if (method.getParameters().size() > 1) {
            throw new ProcessingErrorException("@S3.Delete operation can't have multiple method parameters for keys without key template", method);
        } else if (method.getParameters().isEmpty()) {
            throw new ProcessingErrorException("@S3.Delete operation must have key parameter", method);
        } else {
            key = new Key(CodeBlock.of("var _key = String.valueOf($L)", firstParameter.toString()), List.of(firstParameter));
        }

        if (MethodUtils.isOptional(method)) {
            throw new ProcessingErrorException("@S3.Delete operation, can't be return type Optional<T>", method);
        }

        final boolean isMono = MethodUtils.isMono(method);
        final TypeMirror returnTypeMirror = (mode == S3Operation.Mode.SYNC)
            ? method.getReturnType()
            : MethodUtils.getGenericType(method.getReturnType()).orElseThrow();
        final TypeName returnType = ClassName.get(returnTypeMirror);

        var methodBuilder = CodeBlock.builder();
        for (VariableElement parameter : method.getParameters()) {
            if(!(method.getReturnType() instanceof PrimitiveType)) {
                methodBuilder.add("""
                    if($T.isNull($L)) {
                        throw new $T("S3.Delete request key argument expected, but was null for arg: $L");
                    }
                    """, Objects.class, parameter.getSimpleName().toString(), IllegalArgumentException.class, parameter.getSimpleName().toString());
            }
        }
        methodBuilder.add("\n");

        if (CommonUtils.isVoid(returnTypeMirror)) {
            String clientField = mode == S3Operation.Mode.SYNC
                ? "_simpleSyncClient"
                : "_simpleAsyncClient";

            final String keyArgName;
            boolean isKeyCollection = firstParameter != null && CommonUtils.isCollection(firstParameter.asType());
            if (isKeyCollection) {
                keyArgName = firstParameter.getSimpleName().toString();
            } else {
                methodBuilder.addStatement(key.code());
                keyArgName = "_key";
            }

            if (isMono) {
                methodBuilder.beginControlFlow("return $T.fromCompletionStage(() -> ", CommonClassNames.mono);
            }

            if (mode == S3Operation.Mode.ASYNC) {
                methodBuilder.add("return ");
            }
            methodBuilder.add("$L.delete(_clientConfig.bucket(), $L)", clientField, keyArgName);
            if (mode == S3Operation.Mode.ASYNC) {
                if (CompletableFuture.class.getCanonicalName().equals(((DeclaredType) method.getReturnType()).asElement().toString())) {
                    methodBuilder.add(".toCompletableFuture()");
                }
            }
            methodBuilder.add(";\n");

            if (isMono) {
                methodBuilder.endControlFlow(")");
            }

            return new S3Operation(method, operationMeta.annotation, OperationType.DELETE, ImplType.SIMPLE, mode, methodBuilder.build());
        } else if (CLASS_AWS_DELETE_RESPONSE.equals(returnType)) {
            if (firstParameter != null && CommonUtils.isCollection(firstParameter.asType())) {
                throw new ProcessingErrorException("@S3.Delete operation expected single result, but parameter is collection of keys", method);
            }

            String clientField = mode == S3Operation.Mode.SYNC
                ? "_awsSyncClient"
                : "_awsAsyncClient";

            methodBuilder
                .addStatement(key.code())
                .add("\n")
                .addStatement(CodeBlock.of("""
                    var _request = $T.builder()
                        .bucket(_clientConfig.bucket())
                        .key(_key)
                        .build()""", CLASS_AWS_DELETE_REQUEST))
                .add("\n");

            if (MethodUtils.isMono(method)) {
                methodBuilder.beginControlFlow("return $T.fromCompletionStage(() -> ", CommonClassNames.mono);
            }

            methodBuilder
                .addStatement("return $L.deleteObject(_request)", clientField)
                .build();

            if (MethodUtils.isMono(method)) {
                methodBuilder.endControlFlow(")");
            }

            return new S3Operation(method, operationMeta.annotation, OperationType.DELETE, ImplType.AWS, mode, methodBuilder.build());
        } else if (CLASS_AWS_DELETES_RESPONSE.equals(returnType)) {
            if (firstParameter == null || !CommonUtils.isCollection(firstParameter.asType())) {
                throw new ProcessingErrorException("@S3.Delete operation multiple keys, but parameter is not collection of keys", method);
            }

            String clientField = mode == S3Operation.Mode.SYNC
                ? "_awsSyncClient"
                : "_awsAsyncClient";

            methodBuilder.addStatement(CodeBlock.of("""
                        var _request = $T.builder()
                            .bucket(_clientConfig.bucket())
                            .delete($T.builder()
                                .objects($L.stream()
                                    .map(k -> $T.builder()
                                        .key(k)
                                        .build())
                                    .toList())
                                .build())
                            .build()""", CLASS_AWS_DELETES_REQUEST,
                    ClassName.get("software.amazon.awssdk.services.s3.model", "Delete"),
                    firstParameter.getSimpleName().toString(),
                    ClassName.get("software.amazon.awssdk.services.s3.model", "ObjectIdentifier")))
                .add("\n");

            if (MethodUtils.isMono(method)) {
                methodBuilder.beginControlFlow("return $T.fromCompletionStage(() -> ", CommonClassNames.mono);
            }

            methodBuilder
                .addStatement("return $L.deleteObjects(_request)", clientField)
                .build();

            if (MethodUtils.isMono(method)) {
                methodBuilder.endControlFlow(")");
            }

            return new S3Operation(method, operationMeta.annotation, OperationType.DELETE, ImplType.AWS, mode, methodBuilder.build());
        } else {
            throw new ProcessingErrorException("@S3.Delete operation unsupported method return signature, expected any of Void/%s/%s".formatted(
                CLASS_AWS_DELETE_RESPONSE.simpleName(), CLASS_AWS_DELETES_RESPONSE.simpleName()
            ), method);
        }
    }

    record Key(CodeBlock code, List<VariableElement> params) {}

    private Key parseKey(ExecutableElement method, String keyTemplate) {
        int indexStart = keyTemplate.indexOf("{");
        if (indexStart == -1) {
            return new Key(CodeBlock.of("var _key = $S", keyTemplate), Collections.emptyList());
        }

        List<VariableElement> params = new ArrayList<>();
        CodeBlock.Builder builder = CodeBlock.builder();
        builder.add("var _key = ");
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

            final String paramName = keyTemplate.substring(indexStart + 1, indexEnd);
            final VariableElement parameter = method.getParameters().stream()
                .filter(p -> {
                    TypeName bodyType = ClassName.get(p.asType());
                    return !ClassName.get(ByteBuffer.class).equals(bodyType)
                           && !ArrayTypeName.get(byte[].class).equals(bodyType);
                })
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

    private String getPackage(Element element) {
        return processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
    }
}
