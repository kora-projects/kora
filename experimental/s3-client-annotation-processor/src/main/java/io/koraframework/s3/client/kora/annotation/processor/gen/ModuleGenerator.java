package io.koraframework.s3.client.kora.annotation.processor.gen;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeSpec;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.NameUtils;
import io.koraframework.annotation.processor.common.TagUtils;
import io.koraframework.s3.client.kora.annotation.processor.S3ClassNames;
import io.koraframework.s3.client.kora.annotation.processor.S3ClientAnnotationProcessor;
import io.koraframework.s3.client.kora.annotation.processor.S3ClientUtils;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

public class ModuleGenerator {
    /**
     * Client and buckets config are written as nested classes of the module: the number of generated source files matters for compilation time
     */
    public static TypeSpec generate(ProcessingEnvironment processingEnv, TypeElement s3client, TypeSpec client, @Nullable TypeSpec bucketsConfig) {
        var bucketsType = S3ClientUtils.bucketsConfigName(processingEnv, s3client);
        var b = TypeSpec.interfaceBuilder(S3ClientUtils.moduleName(processingEnv, s3client))
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(CommonClassNames.module)
            .addAnnotation(AnnotationUtils.generated(S3ClientAnnotationProcessor.class))
            .addOriginatingElement(s3client);

        // methods of the module are named as <client>_<Role>
        var methodPrefix = CommonUtils.decapitalize(s3client.getSimpleName().toString());
        var paths = S3ClientUtils.parseConfigBuckets(s3client);
        if (!paths.isEmpty()) {
            b.addMethod(MethodSpec.methodBuilder(methodPrefix + "_BucketsConfig")
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

        var s3ClientConfigPath = S3ClientUtils.clientConfigPath(s3client);
        // config is tagged with the client interface: every client has its own config of the same type
        var clientTag = TagUtils.makeAnnotationSpec(ClassName.get(s3client));
        b.addMethod(MethodSpec.methodBuilder(methodPrefix + "_Config")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(clientTag)
            .returns(configType)
            .addParameter(CommonClassNames.config, "config")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.configValueMapper, configType), "mapper")
            .addStatement("return mapper.mapOrThrow(config.get($S))", s3ClientConfigPath)
            .build());
        var s3ClientAnnotation = AnnotationUtils.findAnnotation(s3client, S3ClassNames.Annotation.CLIENT);
        var factoryTag = AnnotationUtils.<TypeMirror>parseAnnotationValueWithoutDefault(s3ClientAnnotation, "factoryTag");
        // low level client is a component tagged with the client interface, so it is created by the graph and not by the client constructor
        b.addMethod(MethodSpec.methodBuilder(methodPrefix + "_Client")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(clientTag)
            .returns(S3ClassNames.CLIENT)
            .addParameter(clientFactoryParameter(factoryTag))
            .addParameter(ParameterSpec.builder(configType, "clientConfig").addAnnotation(clientTag).build())
            .addStatement("return clientFactory.create($S, $T.class, clientConfig)", s3ClientConfigPath, s3client)
            .build());
        b.addType(client);
        if (bucketsConfig != null) {
            b.addType(bucketsConfig);
        }

        return b.build();
    }

    private static ParameterSpec clientFactoryParameter(TypeMirror factoryTag) {
        var parameter = ParameterSpec.builder(S3ClassNames.CLIENT_FACTORY, "clientFactory");
        var tag = TagUtils.makeAnnotationSpec(factoryTag);
        if (tag != null) {
            parameter.addAnnotation(tag);
        }
        return parameter.build();
    }
}
