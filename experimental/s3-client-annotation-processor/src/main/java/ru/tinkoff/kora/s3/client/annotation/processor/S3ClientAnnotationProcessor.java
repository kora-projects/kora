package ru.tinkoff.kora.s3.client.annotation.processor;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.IntStream;

// todo generate config for bucket annotations
public class S3ClientAnnotationProcessor extends AbstractKoraProcessor {

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(S3ClassNames.Annotation.CLIENT.canonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var annotation = processingEnv.getElementUtils().getTypeElement(S3ClassNames.Annotation.CLIENT.canonicalName());
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
                var configSpec = generateClientConfig(s3client);
                if (!configSpec.paths.isEmpty()) {
                    var configFile = JavaFile.builder(packageName, configSpec.type).build();
                    configFile.writeTo(processingEnv.getFiler());
                }

                var spec = generateClient(s3client, configSpec);
                var implFile = JavaFile.builder(packageName, spec).build();
                implFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        return false;
    }

    private TypeSpec generateClient(TypeElement s3client, GenerateConfigResult configSpec) {
        var implClassName = ClassName.get(getPackage(s3client), NameUtils.generatedType(s3client, "Impl"));
        var implSpecBuilder = TypeSpec.classBuilder(implClassName)
            .addModifiers(Modifier.FINAL, Modifier.PUBLIC)
            .addAnnotation(AnnotationUtils.generated(S3ClientAnnotationProcessor.class))
            .addSuperinterface(s3client.asType())
            .addOriginatingElement(s3client);

        var constructorBuilder = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        var clientAnnotation = AnnotationUtils.findAnnotation(s3client, S3ClassNames.Annotation.CLIENT);
        var clientTag = AnnotationUtils.<List<TypeMirror>>parseAnnotationValueWithoutDefault(clientAnnotation, "clientFactoryTag");
        if (clientTag != null) {
            constructorBuilder.addParameter(ParameterSpec.builder(S3ClassNames.CLIENT_FACTORY, "clientFactory")
                .addAnnotation(AnnotationSpec.builder(CommonClassNames.tag)
                    .addMember("value", TagUtils.writeTagAnnotationValue(clientTag))
                    .build()
                )
                .build());
        } else {
            constructorBuilder.addParameter(S3ClassNames.CLIENT_FACTORY, "clientFactory");
        }
        if (!configSpec.paths.isEmpty()) {
            var configTypeName = ClassName.get(implClassName.packageName(), configSpec.type.name);
            constructorBuilder.addParameter(configTypeName, "config")
                .addStatement("this.config = config");
            implSpecBuilder.addField(configTypeName, "config", Modifier.PRIVATE, Modifier.FINAL);
        }

        var constructorCode = CodeBlock.builder()
            .addStatement("this.client = clientFactory.create($T.class)", implClassName);
        implSpecBuilder.addField(S3ClassNames.CLIENT, "client", Modifier.PRIVATE, Modifier.FINAL);

        for (var enclosedElement : s3client.getEnclosedElements()) {
            if (enclosedElement instanceof ExecutableElement method) {
                if (method.getModifiers().contains(Modifier.DEFAULT) || method.getModifiers().contains(Modifier.STATIC)) {
                    continue;
                }
                var operation = generateOperation(configSpec, method);
                implSpecBuilder.addMethod(operation);
            }
        }

        constructorBuilder.addCode(constructorCode.build());
        implSpecBuilder.addMethod(constructorBuilder.build());

        return implSpecBuilder.build();
    }

    record GenerateConfigResult(@Nullable TypeSpec type, List<String> paths) {}

    private GenerateConfigResult generateClientConfig(TypeElement s3client) {
        var bucketPaths = new LinkedHashSet<String>();
        var onClass = AnnotationUtils.findAnnotation(s3client, S3ClassNames.Annotation.BUCKET);
        if (onClass != null) {
            var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(onClass, "value");
            if (value == null) {
                throw new ProcessingErrorException("@S3.Bucket annotation is missing value", s3client, onClass);
            }
            bucketPaths.add(value);
        }
        for (var enclosedElement : s3client.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD) {
                continue;
            }
            if (enclosedElement.getModifiers().contains(Modifier.DEFAULT) || enclosedElement.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            var onMethod = AnnotationUtils.findAnnotation(enclosedElement, S3ClassNames.Annotation.BUCKET);
            if (onMethod == null) {
                continue;
            }
            var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(onMethod, "value");
            if (value == null) {
                throw new ProcessingErrorException("@S3.Bucket annotation is missing value", enclosedElement, onMethod);
            }
            bucketPaths.add(value);
        }
        var paths = new ArrayList<>(bucketPaths);
        if (bucketPaths.isEmpty()) {
            return new GenerateConfigResult(null, paths);
        }
        var configType = ClassName.get(getPackage(s3client), NameUtils.generatedType(s3client, "ClientConfig"));
        var b = TypeSpec.classBuilder(configType)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(AnnotationUtils.generated(S3ClientAnnotationProcessor.class))
            .addOriginatingElement(s3client);
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(CommonClassNames.config, "config");
        var equals = MethodSpec.methodBuilder("equals")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ClassName.get(Object.class), "o")
            .returns(TypeName.BOOLEAN)
            .beginControlFlow("if (o instanceof $T that)", configType)
            .addStatement("return $L", IntStream.range(0, paths.size())
                .mapToObj(i -> CodeBlock.of("$T.equals(this.bucket_$L, that.bucket_$L)", Objects.class, i, i))
                .collect(CodeBlock.joining("\n  && ")))
            .nextControlFlow("else")
            .addStatement("return false")
            .endControlFlow()
            .build();
        var hashCode = MethodSpec.methodBuilder("hashCode")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.INT)
            .addStatement("return $T.hash($L)", Objects.class, IntStream.range(0, paths.size()).mapToObj(i -> CodeBlock.of("bucket_$L", i)).collect(CodeBlock.joining(", ")))
            .build();
        for (var i = 0; i < paths.size(); i++) {
            var path = paths.get(i);
            b.addField(String.class, "bucket_" + i, Modifier.PUBLIC, Modifier.FINAL);
            constructor.addStatement("this.bucket_$L = config.get($S).asString()", i, path);
        }

        b.addMethod(constructor.build());
        b.addMethod(equals);
        b.addMethod(hashCode);
        return new GenerateConfigResult(b.build(), paths);
    }

    private MethodSpec generateOperation(GenerateConfigResult configSpec, ExecutableElement method) {
        var operationAnnotations = method.getAnnotationMirrors()
            .stream()
            .filter(a -> S3ClassNames.Annotation.OPERATIONS.contains(ClassName.get((TypeElement) a.getAnnotationType().asElement())))
            .toList();
        if (operationAnnotations.size() != 1) {
            throw new ProcessingErrorException("Method " + method.getSimpleName() + " has " + operationAnnotations.size() + " S3 annotations, but should have exactly one", method);
        }
        var operationAnnotation = operationAnnotations.get(0);
        var operationAnnotationClassName = ClassName.get((TypeElement) operationAnnotation.getAnnotationType().asElement());

        if (operationAnnotationClassName.equals(S3ClassNames.Annotation.GET)) {
            return operationGET(configSpec, method, operationAnnotation);
        } else if (operationAnnotationClassName.equals(S3ClassNames.Annotation.LIST)) {
            return operationLIST(configSpec, method, operationAnnotation);
        } else if (operationAnnotationClassName.equals(S3ClassNames.Annotation.PUT)) {
            return operationPUT(configSpec, method, operationAnnotation);
        } else if (operationAnnotationClassName.equals(S3ClassNames.Annotation.DELETE)) {
            return operationDELETE(configSpec, method, operationAnnotation);
        } else {
            throw new IllegalStateException("Unsupported S3 operation type: " + operationAnnotationClassName);
        }
    }

    private MethodSpec operationGET(GenerateConfigResult configSpec, ExecutableElement method, AnnotationMirror operationAnnotation) {
        var returnType = TypeName.get(method.getReturnType());
        var byteArrayTypeName = ArrayTypeName.of(TypeName.BYTE);
        var allowedTypeNames = Set.of(
            S3ClassNames.S3_OBJECT_META,
            ParameterizedTypeName.get(ClassName.get(Optional.class), S3ClassNames.S3_OBJECT_META),
            byteArrayTypeName,
            ParameterizedTypeName.get(ClassName.get(Optional.class), byteArrayTypeName),
            S3ClassNames.S3_BODY,
            S3ClassNames.S3_OBJECT,
            CommonClassNames.inputStream
        );
        if (!allowedTypeNames.contains(returnType)) {
            throw new ProcessingErrorException("Method " + method.getSimpleName() + " has return type " + returnType + ", but should have one of " + allowedTypeNames, method);
        }
        var rangeParams = method.getParameters()
            .stream()
            .filter(p -> S3ClassNames.RANGE_CLASSES.contains(TypeName.get(p.asType())))
            .toList();
        if (rangeParams.size() > 1) {
            throw new ProcessingErrorException("Method " + method.getSimpleName() + " has more than one range parameter", rangeParams.get(1));
        }
        var range = rangeParams.isEmpty() ? null : rangeParams.get(0);
        var bucket = extractBucket(configSpec, method);
        var key = extractKey(method, operationAnnotation, true);
        var methodBuilder = CommonUtils.overridingKeepAop(method)
            .addStatement("var _bucket = $L", bucket)
            .addStatement("var _key = $L", key);
        var isNullable = CommonUtils.isNullable(method);
        if (returnType.equals(S3ClassNames.S3_OBJECT_META)) {
            if (range != null) {
                throw new ProcessingErrorException("Range parameters are not allowed on metadata requests", range);
            }
            if (isNullable) {
                methodBuilder.addStatement("return this.client.getMetaOptional(_bucket, _key)");
            } else {
                methodBuilder.addStatement("return $T.requireNonNull(this.client.getMeta(_bucket, _key))", Objects.class);
            }
            return methodBuilder.build();
        }
        if (returnType.equals(ParameterizedTypeName.get(ClassName.get(Optional.class), S3ClassNames.S3_OBJECT_META))) {
            if (range != null) {
                throw new ProcessingErrorException("Range parameters are not allowed on metadata requests", range);
            }
            methodBuilder.addStatement("var _meta = this.client.getMetaOptional(_bucket, _key)");
            return methodBuilder.addStatement("return $T.ofNullable(_meta)", Optional.class).build();
        }
        if (range != null) {
            methodBuilder.addStatement("var _range = $L", range);
        } else {
            methodBuilder.addStatement("var _range = ($T) null", S3ClassNames.RANGE_DATA);
        }
        if (returnType.equals(byteArrayTypeName) || returnType.equals(ParameterizedTypeName.get(ClassName.get(Optional.class), byteArrayTypeName))) {
            var isOptional = CommonUtils.isOptional(method.getReturnType());
            if (isNullable || isOptional) {
                methodBuilder.beginControlFlow("try (var _object = this.client.getOptional(_bucket, _key, _range))")
                    .beginControlFlow("if (_object == null)")
                    .addStatement("return $L", isOptional ? CodeBlock.of("$T.empty()", Optional.class) : CodeBlock.of("null"))
                    .endControlFlow()
                    .beginControlFlow("try (var _body = _object.body())")
                    .addStatement("var _bytes = _body.asBytes()")
                    .addStatement("return $L", isOptional ? CodeBlock.of("$T.of(_bytes)", Optional.class) : CodeBlock.of("_bytes"))
                    .endControlFlow()
                    .nextControlFlow("catch ($T _e)", IOException.class)
                    .addStatement("throw new $T(_e)", UncheckedIOException.class)
                    .endControlFlow();
            } else {
                methodBuilder.beginControlFlow("try (var _object = this.client.get(_bucket, _key, _range); var _body = _object.body())")
                    .addStatement("return $T.requireNonNull(_body.asBytes())", Objects.class)
                    .nextControlFlow("catch ($T _e)", IOException.class)
                    .addStatement("throw new $T(_e)", UncheckedIOException.class)
                    .endControlFlow();
            }
            return methodBuilder.build();
        }
        if (returnType.equals(S3ClassNames.S3_OBJECT)) {
            if (isNullable) {
                methodBuilder.addStatement("return this.client.getOptional(_bucket, _key, _range)");
            } else {
                methodBuilder.addStatement("return this.client.get(_bucket, _key, _range)");
            }
            return methodBuilder.build();
        }
        if (returnType.equals(S3ClassNames.S3_BODY)) {
            if (isNullable) {
                methodBuilder.addStatement("var _object = this.client.getOptional(_bucket, _key, _range)")
                    .beginControlFlow("if (_object == null)")
                    .addStatement("return null")
                    .nextControlFlow("else")
                    .addStatement("return _object.body()")
                    .endControlFlow();
            } else {
                methodBuilder.addStatement("return this.client.get(_bucket, _key, _range).body()");
            }
            return methodBuilder.build();
        }
        if (returnType.equals(CommonClassNames.inputStream)) {
            if (isNullable) {
                methodBuilder.addStatement("var _object = this.client.getOptional(_bucket, _key, _range)")
                    .beginControlFlow("if (_object == null)")
                    .addStatement("return null")
                    .nextControlFlow("else")
                    .addStatement("return _object.body().asInputStream()")
                    .endControlFlow();
            } else {
                methodBuilder.addStatement("return this.client.get(_bucket, _key, _range).body().asInputStream()");
            }
            return methodBuilder.build();
        }
        throw new IllegalStateException("Not gonna happen");
    }

    private MethodSpec operationLIST(GenerateConfigResult configSpec, ExecutableElement method, AnnotationMirror operationAnnotation) {
        var returnType = TypeName.get(method.getReturnType());
        var isList = returnType.equals(ParameterizedTypeName.get(ClassName.get(List.class), S3ClassNames.S3_OBJECT_META));
        var isIterator = returnType.equals(ParameterizedTypeName.get(ClassName.get(Iterator.class), S3ClassNames.S3_OBJECT_META));
        if (!isList && !isIterator) {
            throw new ProcessingErrorException("Method " + method.getSimpleName() + " has return type " + returnType + ", but should have one of Iterator<S3ObjectMeta> or List<S3ObjectMeta>", method);
        }
        var bucket = extractBucket(configSpec, method);
        var key = extractKey(method, operationAnnotation, false);
        var limit = extractLimit(method);
        var delimiter = extractDelimiter(method);

        var methodBuilder = CommonUtils.overridingKeepAop(method)
            .addStatement("var _bucket = $L", bucket)
            .addStatement("var _key = $L", key)
            .addStatement("var _delimiter = $L", delimiter)
            .addStatement("var _limit = $L", limit);

        if (isList) {
            return methodBuilder
                .addStatement("return this.client.list(_bucket, _key, _delimiter, _limit)")
                .build();
        }
        if (isIterator) {
            return methodBuilder
                .addStatement("return this.client.listIterator(_bucket, _key, _delimiter, _limit)")
                .build();
        }
        throw new IllegalStateException("never gonna happen");
    }

    private CodeBlock extractLimit(ExecutableElement method) {
        var onParameter = method.getParameters()
            .stream()
            .filter(p -> AnnotationUtils.isAnnotationPresent(p, S3ClassNames.Annotation.LIST_LIMIT))
            .toList();
        if (onParameter.size() > 1) {
            throw new ProcessingErrorException("@S3.List operation expected single @S3.List.Limit parameter", method);
        }
        if (!onParameter.isEmpty()) {
            var parameter = onParameter.get(0);
            var annotation = AnnotationUtils.findAnnotation(parameter, S3ClassNames.Annotation.LIST_LIMIT);
            if (AnnotationUtils.parseAnnotationValueWithoutDefault(annotation, "value") != null) {
                throw new ProcessingErrorException("@S3.List.Limit annotation can't have value when annotating parameter", parameter, annotation);
            }
            var parameterType = TypeName.get(parameter.asType());
            if (parameterType.equals(TypeName.INT)) {
                return CodeBlock.of("$T.min(1000, $N)", Math.class, parameter.getSimpleName());
            }
            if (parameterType.equals(ClassName.get(Integer.class))) {
                return CodeBlock.of("$T.min(1000, $N)", Math.class, parameter.getSimpleName());
            }
            if (parameterType.equals(TypeName.LONG) || parameterType.equals(ClassName.get(Long.class))) {
                return CodeBlock.of("$T.min(1000, $T.toIntExact($N))", Math.class, parameter.getSimpleName());
            }
            throw new ProcessingErrorException("@S3.List.Limit annotation can't have parameter of type %s: only int is allowed".formatted(parameterType), parameter);
        }
        var onMethod = AnnotationUtils.findAnnotation(method, S3ClassNames.Annotation.LIST_LIMIT);
        if (onMethod != null) {
            var value = AnnotationUtils.<Integer>parseAnnotationValueWithoutDefault(onMethod, "value");
            if (value != null) {
                if (value <= 0) {
                    throw new ProcessingErrorException("@S3.List.Limit should be more than zero", method, onMethod);
                }
                if (value > 1000) {
                    throw new ProcessingErrorException("@S3.List.Limit should be less than 1000", method, onMethod);
                }
                return CodeBlock.of("$L", value);
            }
            throw new ProcessingErrorException("@S3.List.Limit annotation must have value when annotating method", method, onMethod);
        }
        return CodeBlock.of("1000");
    }

    private CodeBlock extractDelimiter(ExecutableElement method) {
        var onParameter = method.getParameters()
            .stream()
            .filter(p -> AnnotationUtils.isAnnotationPresent(p, S3ClassNames.Annotation.LIST_DELIMITER))
            .toList();
        if (onParameter.size() > 1) {
            throw new ProcessingErrorException("@S3.List operation expected single @S3.List.Delimiter parameter", method);
        }
        if (!onParameter.isEmpty()) {
            var parameter = onParameter.get(0);
            var annotation = AnnotationUtils.findAnnotation(parameter, S3ClassNames.Annotation.LIST_DELIMITER);
            if (AnnotationUtils.parseAnnotationValueWithoutDefault(annotation, "value") != null) {
                throw new ProcessingErrorException("@S3.List.Delimiter annotation can't have value when annotating parameter", parameter, annotation);
            }
            var parameterType = TypeName.get(parameter.asType());
            if (parameterType.equals(ClassName.get(String.class))) {
                return CodeBlock.of("$N", parameter.getSimpleName());
            }
            throw new ProcessingErrorException("@S3.List.Delimiter annotation can't have parameter of type %s: only String is allowed".formatted(parameterType), parameter);
        }
        var onMethod = AnnotationUtils.findAnnotation(method, S3ClassNames.Annotation.LIST_DELIMITER);
        if (onMethod != null) {
            var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(onMethod, "value");
            if (value != null) {
                return CodeBlock.of("$S", value);
            }
            throw new ProcessingErrorException("@S3.List.Delimiter annotation must have value when annotating method", method, onMethod);
        }
        return CodeBlock.of("(String) null");
    }

    private MethodSpec operationPUT(GenerateConfigResult configSpec, ExecutableElement method, AnnotationMirror operationAnnotation) {
        var returnType = TypeName.get(method.getReturnType());
        if (!returnType.equals(TypeName.VOID) && !returnType.equals(S3ClassNames.S3_OBJECT_UPLOAD_RESULT)) {
            throw new ProcessingErrorException("@S3.Put operation return type must be void or S3ObjectUploadResult", method);
        }
        var bodyParams = method.getParameters()
            .stream()
            .filter(p -> S3ClassNames.BODY_TYPES.contains(TypeName.get(p.asType())))
            .toList();
        if (bodyParams.size() != 1) {
            throw new ProcessingErrorException("@S3.Put operation must have exactly one parameter of types S3Body, byte[] or InputStream", method);
        }
        var bodyParam = bodyParams.get(0);
        var bodyParamType = TypeName.get(bodyParam.asType());
        var bucket = extractBucket(configSpec, method);
        var key = extractKey(method, operationAnnotation, true);
        var b = CommonUtils.overridingKeepAop(method)
            .addStatement("var _bucket = $L", bucket)
            .addStatement("var _key = $L", key);
        if (bodyParamType.equals(S3ClassNames.S3_BODY)) {
            b.addStatement("var _body = $L", bodyParam.getSimpleName());
        } else if (bodyParamType.equals(ArrayTypeName.of(TypeName.BYTE))) {
            b.addStatement("var _body = $T.ofBytes($L)", S3ClassNames.S3_BODY, bodyParam.getSimpleName());
        } else if (bodyParamType.equals(ClassName.get(InputStream.class))) {
            b.addStatement("var _body = $T.ofInputStream($L)", S3ClassNames.S3_BODY, bodyParam.getSimpleName());
        } else {
            throw new IllegalStateException("not gonna happen");
        }
        b.addStatement("return this.client.put(_bucket, _key, _body)");
        return b.build();
    }

    private MethodSpec operationDELETE(GenerateConfigResult configSpec, ExecutableElement method, AnnotationMirror operationAnnotation) {
        var returnType = TypeName.get(method.getReturnType());
        if (returnType != TypeName.VOID) {
            throw new ProcessingErrorException("@S3.Delete operation must return void", method);
        }
        var bucket = extractBucket(configSpec, method);
        var nonBucketParams = method.getParameters().stream()
            .filter(p -> !AnnotationUtils.isAnnotationPresent(p, S3ClassNames.Annotation.BUCKET))
            .toList();
        if (nonBucketParams.isEmpty()) {
            throw new ProcessingErrorException("@S3.Delete operation must have key related parameter", method);
        }
        var methodSpec = CommonUtils.overridingKeepAop(method)
            .addStatement("var _bucket = $L", bucket);
        var firstKeyParam = nonBucketParams.get(0);
        var firstKeyParamType = firstKeyParam.asType();
        if (nonBucketParams.size() == 1 && (CommonUtils.isList(firstKeyParamType) || CommonUtils.isCollection(firstKeyParamType))) {
            var collectionTypeName = (ParameterizedTypeName) TypeName.get(firstKeyParam.asType());
            if (collectionTypeName.typeArguments.get(0).equals(ClassName.get(String.class))) {
                methodSpec.addStatement("var _key = $L", firstKeyParam);
            } else {
                methodSpec.addStatement("var _key = $L.stream().map($T::toString).toList()", firstKeyParam, Objects.class);
            }
        } else {
            var key = extractKey(method, operationAnnotation, true);
            methodSpec.addStatement("var _key = $L", key);
        }

        methodSpec.addStatement("this.client.delete(_bucket, _key)");
        // todo map results maybe
        return methodSpec.build();
    }

    record Key(CodeBlock code, List<VariableElement> params) {}

    private CodeBlock extractKey(ExecutableElement method, AnnotationMirror annotation, boolean required) {
        var keyMapping = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(annotation, "value");
        var parameters = method.getParameters()
            .stream()
            .filter(p -> {
                var parameterTypeName = TypeName.get(p.asType());
                return !AnnotationUtils.isAnnotationPresent(p, S3ClassNames.Annotation.BUCKET)
                    && !AnnotationUtils.isAnnotationPresent(p, S3ClassNames.Annotation.LIST_LIMIT)
                    && !AnnotationUtils.isAnnotationPresent(p, S3ClassNames.Annotation.LIST_DELIMITER)
                    && !S3ClassNames.RANGE_CLASSES.contains(parameterTypeName)
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

    private CodeBlock extractBucket(GenerateConfigResult configSpec, ExecutableElement method) {
        var onParameter = method.getParameters()
            .stream()
            .filter(p -> AnnotationUtils.isAnnotationPresent(p, S3ClassNames.Annotation.BUCKET))
            .toList();
        if (onParameter.size() > 1) {
            throw new ProcessingErrorException("@S3 operation can't have multiple method parameters for bucket", method);
        }
        if (onParameter.size() == 1) {
            return CodeBlock.of("$N", onParameter.get(0).getSimpleName());
        }
        var onMethod = AnnotationUtils.findAnnotation(method, S3ClassNames.Annotation.BUCKET);
        if (onMethod != null) {
            var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(onMethod, "value");
            var i = configSpec.paths.indexOf(value);
            if (i < 0) {
                throw new ProcessingErrorException("@S3 operation must have bucket parameter or bucket value in config", method);
            }
            return CodeBlock.of("this.config.bucket_$L", i);
        }
        var onClass = AnnotationUtils.findAnnotation(method.getEnclosingElement(), S3ClassNames.Annotation.BUCKET);
        if (onClass != null) {
            var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(onClass, "value");
            var i = configSpec.paths.indexOf(value);
            if (i < 0) {
                throw new ProcessingErrorException("@S3 operation must have bucket parameter or bucket value in config", method);
            }
            return CodeBlock.of("this.config.bucket_$L", i);
        }
        throw new ProcessingErrorException("S3 operation expected bucket on parameter, method or class but got none", method);
    }

    private Key parseKey(ExecutableElement method, List<? extends VariableElement> parameters, String keyTemplate) {
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

    private String getPackage(Element element) {
        return processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
    }
}
