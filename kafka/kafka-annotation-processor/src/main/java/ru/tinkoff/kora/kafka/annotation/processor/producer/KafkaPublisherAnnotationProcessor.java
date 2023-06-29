package ru.tinkoff.kora.kafka.annotation.processor.producer;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.kafka.annotation.processor.KafkaClassNames;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;

public class KafkaPublisherAnnotationProcessor extends AbstractKoraProcessor {
    private TypeElement kafkaProducerAnnotationElement;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.kafkaProducerAnnotationElement = this.elements.getTypeElement(KafkaClassNames.kafkaPublisherAnnotation.canonicalName());
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(KafkaClassNames.kafkaPublisherAnnotation.canonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var producers = roundEnv.getElementsAnnotatedWith(Objects.requireNonNull(this.kafkaProducerAnnotationElement));
        for (var producer : producers) {
            try {
                if (!(producer instanceof TypeElement typeElement) || typeElement.getKind() != ElementKind.INTERFACE) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, "@KafkaPublisher can be placed only on interfaces extending only Producer or TransactionalProducer", producer);
                    continue;
                }
                var supertypes = typeElement.getInterfaces();
                if (supertypes.size() != 1) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, "@KafkaPublisher can be placed only on interfaces extending only Producer or TransactionalProducer", producer);
                    continue;
                }
                var supertypeMirror = (DeclaredType) supertypes.get(0);
                if (!(TypeName.get(supertypeMirror) instanceof ParameterizedTypeName supertypeName)) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, "@KafkaPublisher can be placed only on interfaces extending only Producer or TransactionalProducer", producer);
                    continue;
                }
                if (supertypeName.rawType.equals(KafkaClassNames.producer)) {
                    var keyType = supertypeName.typeArguments.get(0);
                    var valueType = supertypeName.typeArguments.get(1);
                    this.generateProducerModule(typeElement, supertypeMirror, supertypeName, keyType, valueType);
                    this.generateProducerImplementation(typeElement, supertypeMirror, supertypeName, keyType, valueType);
                } else if (supertypeName.rawType.equals(KafkaClassNames.transactionalProducer)) {
                    var keyType = supertypeName.typeArguments.get(0);
                    var valueType = supertypeName.typeArguments.get(1);
                    this.generateProducerModule(typeElement, supertypeMirror, supertypeName, keyType, valueType);
                    this.generateTransactionalProducerImplementation(typeElement, keyType, valueType);
                } else {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, "@KafkaPublisher can be placed only on interfaces extending only Producer or TransactionalProducer", producer);
                    continue;
                }
            } catch (ProcessingErrorException e) {
                e.printError(this.processingEnv);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    private void generateProducerModule(TypeElement typeElement, DeclaredType supertypeMirror, ParameterizedTypeName supertypeName, TypeName keyType, TypeName valueType) throws IOException {
        var packageName = this.elements.getPackageOf(typeElement).getQualifiedName().toString();
        var moduleName = NameUtils.generatedType(typeElement, "Module");
        var module = TypeSpec.interfaceBuilder(moduleName)
            .addOriginatingElement(typeElement)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(CommonClassNames.module)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated).addMember("value", "$S", KafkaPublisherAnnotationProcessor.class.getCanonicalName()).build());
        var implementationName = ClassName.get(packageName, NameUtils.generatedType(typeElement, "Implementation"));
        module.addMethod(this.buildPropertiesMethod(typeElement));
        module.addMethod(this.buildGeneratedProducerMethod(typeElement, supertypeMirror, implementationName, keyType, valueType));
        if (supertypeName.rawType.equals(KafkaClassNames.producer)) {
            module.addMethod(this.buildKafkaProducerMethod(typeElement, implementationName, keyType, valueType));
        }
        JavaFile.builder(packageName, module.build())
            .build()
            .writeTo(this.processingEnv.getFiler());
    }

    private MethodSpec buildGeneratedProducerMethod(TypeElement typeElement, DeclaredType supertypeMirror, ClassName implementationName, TypeName keyType, TypeName valueType) {
        var keySerializer = ParameterSpec.builder(ParameterizedTypeName.get(KafkaClassNames.serializer, keyType), "keySerializer");
        var valueSerializer = ParameterSpec.builder(ParameterizedTypeName.get(KafkaClassNames.serializer, valueType), "valueSerializer");
        var keyTag = TagUtils.parseTagValue(supertypeMirror.getTypeArguments().get(0));
        var valueTag = TagUtils.parseTagValue(supertypeMirror.getTypeArguments().get(1));

        if (!keyTag.isEmpty()) {
            keySerializer.addAnnotation(TagUtils.makeAnnotationSpec(keyTag));
        }
        if (!valueTag.isEmpty()) {
            valueSerializer.addAnnotation(TagUtils.makeAnnotationSpec(valueTag));
        }
        var propertiesTag = AnnotationSpec.builder(CommonClassNames.tag).addMember("value", "$T.class", ClassName.get(typeElement)).build();
        var config = ParameterSpec.builder(KafkaClassNames.publisherConfig, "properties").addAnnotation(propertiesTag).build();

        return MethodSpec.methodBuilder(typeElement.getSimpleName() + "_ProducerImpl")
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .returns(implementationName)
            .addParameter(config)
            .addParameter(keySerializer.build())
            .addParameter(valueSerializer.build())
            .addParameter(KafkaClassNames.producerTelemetryFactory, "telemetryFactory")
            .addStatement("return new $T(telemetryFactory, properties, keySerializer, valueSerializer)", implementationName)
            .build();
    }

    private MethodSpec buildPropertiesMethod(TypeElement typeElement) {
        var producerAnnotation = AnnotationUtils.findAnnotation(typeElement, KafkaClassNames.kafkaPublisherAnnotation);
        var configPath = Objects.requireNonNull(AnnotationUtils.<String>parseAnnotationValueWithoutDefault(producerAnnotation, "value"));

        return MethodSpec.methodBuilder(typeElement.getSimpleName() + "_ProducerProperties")
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .returns(KafkaClassNames.publisherConfig)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.tag).addMember("value", "$T.class", ClassName.get(typeElement)).build())
            .addParameter(CommonClassNames.config, "config")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.configValueExtractor, KafkaClassNames.publisherConfig), "propertiesExtractor")
            .addStatement("var configValue = config.getValue($S)", configPath)
            .addStatement("return $T.requireNonNull(propertiesExtractor.extract(configValue))", Objects.class)
            .build();
    }

    private MethodSpec buildKafkaProducerMethod(TypeElement typeElement, ClassName implementationName, TypeName keyType, TypeName valueType) {
        return MethodSpec.methodBuilder(typeElement.getSimpleName() + "_kafkaProducer")
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(KafkaClassNames.kafkaProducer, keyType, valueType))
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.tag).addMember("value", "$T.class", ClassName.get(typeElement)).build())
            .addParameter(implementationName, "producer")
            .addStatement("return producer.delegate()")
            .build();
    }

    private void generateProducerImplementation(TypeElement typeElement, DeclaredType supertypeMirror, ParameterizedTypeName supertypeName, TypeName keyType, TypeName valueType) throws IOException {
        var packageName = this.elements.getPackageOf(typeElement).getQualifiedName().toString();
        var implementationName = NameUtils.generatedType(typeElement, "Implementation");
        var kafkaProducerType = ParameterizedTypeName.get(KafkaClassNames.kafkaProducer, keyType, valueType);

        var b = CommonUtils.extendsKeepAop(typeElement, implementationName)
            .addOriginatingElement(typeElement)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(CommonClassNames.lifecycle)
            .addField(KafkaClassNames.publisherConfig, "config", Modifier.PRIVATE, Modifier.FINAL)
            .addField(ParameterizedTypeName.get(KafkaClassNames.serializer, keyType), "keySerializer", Modifier.PRIVATE, Modifier.FINAL)
            .addField(ParameterizedTypeName.get(KafkaClassNames.serializer, valueType), "valueSerializer", Modifier.PRIVATE, Modifier.FINAL)
            .addField(KafkaClassNames.producerTelemetryFactory, "telemetryFactory", Modifier.PRIVATE, Modifier.FINAL)
            .addField(kafkaProducerType, "delegate", Modifier.PRIVATE, Modifier.VOLATILE)
            .addField(KafkaClassNames.producerTelemetry, "telemetry", Modifier.PRIVATE, Modifier.VOLATILE)
            .addMethod(MethodSpec.constructorBuilder()
                .addParameter(KafkaClassNames.producerTelemetryFactory, "telemetryFactory")
                .addParameter(KafkaClassNames.publisherConfig, "config")
                .addParameter(ParameterizedTypeName.get(KafkaClassNames.serializer, keyType), "keySerializer")
                .addParameter(ParameterizedTypeName.get(KafkaClassNames.serializer, valueType), "valueSerializer")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("this.config = config")
                .addStatement("this.keySerializer = keySerializer")
                .addStatement("this.valueSerializer = valueSerializer")
                .addStatement("this.telemetryFactory = telemetryFactory")
                .build())
            .addMethod(MethodSpec.methodBuilder("delegate")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .returns(kafkaProducerType)
                .addStatement("return delegate")
                .build()
            )
            .addMethod(MethodSpec.methodBuilder("init")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(CommonClassNames.mono, TypeName.VOID.box()))
                .addCode("return ru.tinkoff.kora.common.util.ReactorUtils.ioMono(() -> {$>\n")
                .addCode("var properties = this.config.driverProperties();\n")// todo process some props?
                .addCode("this.delegate = new $T<>(properties, this.keySerializer, this.valueSerializer);\n", KafkaClassNames.kafkaProducer)
                .addCode("this.telemetry = this.telemetryFactory.get(this.delegate, properties);\n", KafkaClassNames.kafkaProducer)
                .addCode("$<\n});\n")
                .build())
            .addMethod(MethodSpec.methodBuilder("release")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(CommonClassNames.mono, TypeName.VOID.box()))
                .addCode("return ru.tinkoff.kora.common.util.ReactorUtils.ioMono(() -> {$>\n")
                .beginControlFlow("if (this.delegate != null)")
                .addStatement("this.delegate.close()")
                .addStatement("this.delegate = null")
                .beginControlFlow("if (this.telemetry != null)")
                .addStatement("this.telemetry.close()")
                .addStatement("this.telemetry = null")
                .endControlFlow()
                .endControlFlow()
                .addCode("$<\n});\n")
                .build());
        for (var enclosedElement : this.elements.getTypeElement(KafkaClassNames.producer.canonicalName()).getEnclosedElements()) {
            if (enclosedElement instanceof ExecutableElement method) {
                var m = MethodSpec.overriding(method, supertypeMirror, types);
                if (method.getSimpleName().contentEquals("send")) {
                    m.addStatement("var tctx = this.telemetry.record($N)", method.getParameters().get(0).getSimpleName());
                    if (method.getParameters().size() == 1) {
                        m.addStatement("return this.delegate.send($N, tctx)", method.getParameters().get(0).getSimpleName());
                    } else {
                        m.addCode("return this.delegate.send($N, (metadata, error) -> {$>\n", method.getParameters().get(0).getSimpleName());
                        m.addCode("tctx.onCompletion(metadata, error);\n");
                        m.addCode("$N.onCompletion(metadata, error);", method.getParameters().get(1).getSimpleName());
                        m.addCode("$<\n});\n");
                    }
                } else {
                    if (!method.getReturnType().toString().equals("void")) {
                        m.addCode("return ");
                    }
                    m.addCode("this.delegate.$N(", method.getSimpleName());
                    for (int i = 0; i < method.getParameters().size(); i++) {
                        if (i > 0) m.addCode(", ");
                        m.addCode("$N", method.getParameters().get(i).getSimpleName());
                    }
                    m.addCode(");\n");
                }
                b.addMethod(m.build());
            }
        }
        JavaFile.builder(packageName, b.build())
            .build()
            .writeTo(this.processingEnv.getFiler());
    }

    private void generateTransactionalProducerImplementation(TypeElement typeElement, TypeName keyType, TypeName valueType) throws IOException {
        var packageName = this.elements.getPackageOf(typeElement).getQualifiedName().toString();
        var implementationName = NameUtils.generatedType(typeElement, "Implementation");
        var kafkaProducerType = ParameterizedTypeName.get(KafkaClassNames.producer, keyType, valueType);
        var delegateType = ParameterizedTypeName.get(KafkaClassNames.transactionalProducerImpl, keyType, valueType);
        var keySer = ParameterizedTypeName.get(KafkaClassNames.serializer, keyType);
        var valSer = ParameterizedTypeName.get(KafkaClassNames.serializer, valueType);

        var b = CommonUtils.extendsKeepAop(typeElement, implementationName)
            .addOriginatingElement(typeElement)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(CommonClassNames.lifecycle)
            .addField(delegateType, "delegate", Modifier.PRIVATE, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder()
                .addParameter(KafkaClassNames.producerTelemetryFactory, "telemetryFactory")
                .addParameter(KafkaClassNames.publisherConfig, "config")
                .addParameter(keySer, "keySerializer")
                .addParameter(valSer, "valueSerializer")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("this.delegate = new $T<>(telemetryFactory, config, keySerializer, valueSerializer)", KafkaClassNames.transactionalProducerImpl)
                .build())
            .addMethod(MethodSpec.methodBuilder("begin")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .returns(kafkaProducerType)
                .addStatement("return delegate.begin()")
                .build()
            )
            .addMethod(MethodSpec.methodBuilder("init")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(CommonClassNames.mono, WildcardTypeName.subtypeOf(TypeName.OBJECT)))
                .addStatement("return this.delegate.init()")
                .build())
            .addMethod(MethodSpec.methodBuilder("release")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(CommonClassNames.mono, WildcardTypeName.subtypeOf(TypeName.OBJECT)))
                .addStatement("return this.delegate.release()")
                .build());

        JavaFile.builder(packageName, b.build())
            .build()
            .writeTo(this.processingEnv.getFiler());
    }
}
