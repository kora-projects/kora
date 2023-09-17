package ru.tinkoff.kora.kafka.annotation.processor.producer;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.kafka.annotation.processor.KafkaClassNames;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

final class KafkaPublisherTransactionalGenerator {

    private final Types types;
    private final Elements elements;
    private final ProcessingEnvironment processingEnv;

    KafkaPublisherTransactionalGenerator(Types types, Elements elements, ProcessingEnvironment processingEnv) {
        this.types = types;
        this.elements = elements;
        this.processingEnv = processingEnv;
    }

    public void generatePublisherTransactionalModule(TypeElement typeElement, TypeElement publisherTypeElement, AnnotationMirror annotation) throws IOException {
        var packageName = this.elements.getPackageOf(typeElement).getQualifiedName().toString();
        var implementationName = NameUtils.generatedType(typeElement, "Impl");
        var implementationTypeName = ClassName.get(packageName, implementationName);
        var publisherPackageName = this.elements.getPackageOf(publisherTypeElement).getQualifiedName().toString();
        var publisherImplementationTypeName = ClassName.get(publisherPackageName, NameUtils.generatedType(publisherTypeElement, "Impl"));
        var moduleName = NameUtils.generatedType(typeElement, "Module");
        var module = TypeSpec.interfaceBuilder(moduleName)
            .addOriginatingElement(typeElement)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(CommonClassNames.module)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated).addMember("value", "$S", KafkaPublisherAnnotationProcessor.class.getCanonicalName()).build());

        var configPath = Objects.requireNonNull(AnnotationUtils.<String>parseAnnotationValueWithoutDefault(annotation, "value"));
        var tag = AnnotationSpec.builder(CommonClassNames.tag).addMember("value", "$T.class", ClassName.get(typeElement)).build();

        var config = MethodSpec.methodBuilder(CommonUtils.decapitalize(typeElement.getSimpleName().toString()) + "_PublisherTransactionalConfig")
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .returns(KafkaClassNames.publisherTransactionalConfig)
            .addAnnotation(tag)
            .addParameter(CommonClassNames.config, "config")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.configValueExtractor, KafkaClassNames.publisherTransactionalConfig), "extractor")
            .addStatement("var configValue = config.get($S)", configPath)
            .addStatement("return $T.requireNonNull(extractor.extract(configValue))", Objects.class)
            .build();
        var publisher = MethodSpec.methodBuilder(CommonUtils.decapitalize(typeElement.getSimpleName().toString()) + "_PublisherTransactional")
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameter(ParameterizedTypeName.get(ClassName.get(Function.class), ClassName.get(Properties.class), publisherImplementationTypeName), "factory")
            .addParameter(ParameterSpec.builder(KafkaClassNames.publisherTransactionalConfig, "config").addAnnotation(tag).build())
            .returns(ClassName.get(typeElement))
            .addCode("return new $T(config, () -> {$>\n", implementationTypeName)
            .addStatement("var properties = new $T()", Properties.class)
            .addStatement("properties.put(org.apache.kafka.clients.producer.ProducerConfig.TRANSACTIONAL_ID_CONFIG, config.idPrefix() + \"-\" + $T.randomUUID())", UUID.class)
            .addStatement("return factory.apply(properties)")
            .addCode("$<\n});\n")
            .build();

        module.addMethod(config);
        module.addMethod(publisher);

        JavaFile.builder(packageName, module.build())
            .build()
            .writeTo(this.processingEnv.getFiler());
    }

    public void generatePublisherTransactionalImpl(TypeElement typeElement, ClassName publisherType, TypeElement publisherTypeElement) throws IOException {
        var packageName = this.elements.getPackageOf(typeElement).getQualifiedName().toString();
        var publisherPackageName = this.elements.getPackageOf(publisherTypeElement).getQualifiedName().toString();
        var implementationName = NameUtils.generatedType(typeElement, "Impl");
        var publisherImplementationTypeName = ClassName.get(publisherPackageName, NameUtils.generatedType(publisherTypeElement, "Impl"));
        var b = CommonUtils.extendsKeepAop(typeElement, implementationName)
            .addSuperinterface(CommonClassNames.lifecycle)
            .addOriginatingElement(typeElement)
            .addField(ParameterizedTypeName.get(KafkaClassNames.transactionalPublisherImpl, publisherImplementationTypeName), "delegate", Modifier.PRIVATE, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(KafkaClassNames.publisherTransactionalConfig, "config")
                .addParameter(ParameterizedTypeName.get(ClassName.get(Supplier.class), publisherImplementationTypeName), "factory")
                .addStatement("this.delegate = new $T<>(config, factory)", KafkaClassNames.transactionalPublisherImpl)
                .build()
            )
            .addMethod(MethodSpec.methodBuilder("init")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(Override.class)
                .addStatement("this.delegate.init()")
                .build()
            )
            .addMethod(MethodSpec.methodBuilder("release")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(Override.class)
                .addException(Exception.class)
                .addStatement("this.delegate.release()")
                .build()
            )
            .addMethod(MethodSpec.methodBuilder("begin")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(KafkaClassNames.transaction, WildcardTypeName.subtypeOf(publisherType)))
                .addStatement("return this.delegate.begin()")
                .build()
            )
            .build();

        JavaFile.builder(packageName, b)
            .build()
            .writeTo(this.processingEnv.getFiler());
    }
}
