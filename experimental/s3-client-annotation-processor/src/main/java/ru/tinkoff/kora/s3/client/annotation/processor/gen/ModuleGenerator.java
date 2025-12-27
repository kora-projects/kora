package ru.tinkoff.kora.s3.client.annotation.processor.gen;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeSpec;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.s3.client.annotation.processor.S3ClassNames;
import ru.tinkoff.kora.s3.client.annotation.processor.S3ClientAnnotationProcessor;
import ru.tinkoff.kora.s3.client.annotation.processor.S3ClientUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public class ModuleGenerator {
    public static TypeSpec generate(ProcessingEnvironment processingEnv, TypeElement s3client) {
        var packageName = processingEnv.getElementUtils().getPackageOf(s3client).getQualifiedName().toString();
        var bucketsType = ClassName.get(packageName, NameUtils.generatedType(s3client, "BucketsConfig"));
        var clientType = ClassName.get(packageName, NameUtils.generatedType(s3client, "ClientImpl"));
        var b = TypeSpec.interfaceBuilder(NameUtils.generatedType(s3client, "Module"))
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationUtils.generated(S3ClientAnnotationProcessor.class))
            .addOriginatingElement(s3client);

        var paths = S3ClientUtils.parseConfigBuckets(s3client);
        if (!paths.isEmpty()) {
            b.addMethod(MethodSpec.methodBuilder("bucketsConfig")
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .returns(bucketsType)
                .addParameter(CommonClassNames.config, "config")
                .addStatement("return new $T(config)", bucketsType)
                .build());
        }
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

        var s3ClientAnnotation = AnnotationUtils.findAnnotation(s3client, S3ClassNames.CLIENT);
        var s3ClientConfigPath = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(s3ClientAnnotation, "value");
        if (s3ClientConfigPath == null || s3ClientConfigPath.isEmpty()) {
            s3ClientConfigPath = s3client.getSimpleName().toString();
        }
        b.addMethod(MethodSpec.methodBuilder("clientConfig")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(configType)
            .addParameter(CommonClassNames.config, "config")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.configValueExtractor, configType), "extractor")
            .addStatement("var configValue = config.get($S)", s3ClientConfigPath)
            .addStatement("var parsed = extractor.extract(configValue)")
            .addCode("if (parsed == null) $T.missingValueAfterParse(configValue);\n", CommonClassNames.configValueExtractionException)
            .addStatement("return parsed")
            .build());
        var clientImpl = MethodSpec.methodBuilder("clientImpl")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(clientType)
            .addParameter(S3ClassNames.CLIENT_FACTORY, "clientFactory")
            .addParameter(configType, "clientConfig");
        if (paths.isEmpty()) {
            clientImpl
                .addStatement("return new $T(clientFactory, clientConfig)", clientType);
        } else {
            clientImpl.addParameter(bucketsType, "bucketsConfig")
                .addStatement("return new $T(clientFactory, clientConfig, bucketsConfig)", clientType);
        }
        b.addMethod(clientImpl.build());

        return b.build();
    }
}
