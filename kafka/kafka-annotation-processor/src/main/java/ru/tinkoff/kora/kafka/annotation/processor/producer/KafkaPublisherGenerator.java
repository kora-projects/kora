package ru.tinkoff.kora.kafka.annotation.processor.producer;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.kafka.annotation.processor.KafkaClassNames;
import ru.tinkoff.kora.kafka.annotation.processor.utils.KafkaPublisherUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static ru.tinkoff.kora.kafka.annotation.processor.KafkaClassNames.*;

final class KafkaPublisherGenerator {

    private final Types types;
    private final Elements elements;
    private final ProcessingEnvironment processingEnv;

    KafkaPublisherGenerator(Types types, Elements elements, ProcessingEnvironment processingEnv) {
        this.types = types;
        this.elements = elements;
        this.processingEnv = processingEnv;
    }

    public void generatePublisherModule(TypeElement typeElement, List<ExecutableElement> publishMethods, AnnotationMirror publisherAnnotation) throws IOException {
        var packageName = this.elements.getPackageOf(typeElement).getQualifiedName().toString();
        var moduleName = NameUtils.generatedType(typeElement, "PublisherModule");
        var module = TypeSpec.interfaceBuilder(moduleName)
            .addOriginatingElement(typeElement)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(CommonClassNames.module)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
                .addMember("value", "$S", KafkaPublisherAnnotationProcessor.class.getCanonicalName()).build());

        module.addMethod(this.buildPublisherFactoryFunction(typeElement, publishMethods));
        module.addMethod(this.buildPublisherFactoryImpl(typeElement));
        module.addMethod(this.buildProducerConfigMethod(typeElement, publisherAnnotation));
        module.addMethod(this.buildTopicConfigMethod(typeElement, publishMethods, publisherAnnotation));

        JavaFile.builder(packageName, module.build())
            .build()
            .writeTo(this.processingEnv.getFiler());
    }

    private MethodSpec buildProducerConfigMethod(TypeElement publisher, AnnotationMirror publisherAnnotation) {
        var configPath = Objects.requireNonNull(AnnotationUtils.<String>parseAnnotationValueWithoutDefault(publisherAnnotation, "value"));

        var className = ClassName.get(publisher);
        return MethodSpec.methodBuilder(CommonUtils.decapitalize(className.simpleName()) + "_PublisherConfig")
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .returns(KafkaClassNames.publisherConfig)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.tag).addMember("value", "$T.class", className).build())
            .addParameter(CommonClassNames.config, "config")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.configValueExtractor, KafkaClassNames.publisherConfig), "extractor")
            .addStatement("var configValue = config.get($S)", configPath)
            .addStatement("return $T.requireNonNull(extractor.extract(configValue))", Objects.class)
            .build();
    }

    private MethodSpec buildPublisherFactoryImpl(TypeElement publisher) {
        var packageName = this.elements.getPackageOf(publisher).getQualifiedName().toString();
        var implementationName = ClassName.get(packageName, NameUtils.generatedType(publisher, "Impl"));
        var builder = MethodSpec.methodBuilder(CommonUtils.decapitalize(publisher.getSimpleName().toString()) + "_PublisherImpl")
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .returns(ClassName.get(publisher))
            .addParameter(ParameterizedTypeName.get(ClassName.get(Function.class), ClassName.get(Properties.class), implementationName), "factory");

        builder.addStatement("return factory.apply(new $T())", Properties.class);
        return builder.build();
    }

    private MethodSpec buildPublisherFactoryFunction(TypeElement publisher, List<ExecutableElement> publishMethods) {
        var propertiesTag = AnnotationSpec.builder(CommonClassNames.tag).addMember("value", "$T.class", publisher).build();
        var config = ParameterSpec.builder(KafkaClassNames.publisherConfig, "config").addAnnotation(propertiesTag).build();
        var packageName = this.elements.getPackageOf(publisher).getQualifiedName().toString();
        var implementationName = ClassName.get(packageName, NameUtils.generatedType(publisher, "Impl"));
        var topicConfigName = NameUtils.generatedType(publisher, "TopicConfig");
        var topicConfigTypeName = ClassName.get(packageName, topicConfigName);

        var builder = MethodSpec.methodBuilder(CommonUtils.decapitalize(publisher.getSimpleName().toString()) + "_PublisherFactory")
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameter(producerTelemetryFactory, "telemetryFactory")
            .addParameter(config)
            .addParameter(topicConfigTypeName, "topicConfig")
            .returns(ParameterizedTypeName.get(ClassName.get(Function.class), ClassName.get(Properties.class), implementationName));

        builder.addCode("return (additionalProperties) -> {$>\n");
        builder.addStatement("var properties = new $T()", Properties.class);
        builder.addStatement("properties.putAll(config.driverProperties())");
        builder.addStatement("properties.putAll(additionalProperties)");
        builder.addCode("return new $T(telemetryFactory, properties, topicConfig$>", implementationName);

        record TypeWithTag(TypeName typeName, Set<String> tag) {}
        var parameters = new HashMap<TypeWithTag, String>();
        var counter = new AtomicInteger(0);
        for (var publishMethod : publishMethods) {
            var types = KafkaPublisherUtils.parsePublisherType(publishMethod);
            if (types.keyType() != null) {
                var keyType = new TypeWithTag(types.keyType(), types.keyTag());
                var keyParserName = parameters.get(keyType);
                if (keyParserName == null) {
                    keyParserName = "serializer" + counter.incrementAndGet();
                    var parameter = ParameterSpec.builder(ParameterizedTypeName.get(serializer, keyType.typeName()), keyParserName);
                    var tags = keyType.tag();
                    if (!tags.isEmpty()) {
                        parameter.addAnnotation(TagUtils.makeAnnotationSpec(tags));
                    }
                    builder.addParameter(parameter.build());
                    parameters.put(keyType, keyParserName);
                    builder.addCode(", $N", keyParserName);
                }
            }
            var valueType = new TypeWithTag(types.valueType(), types.valueTag());
            var valueParserName = parameters.get(valueType);
            if (valueParserName == null) {
                valueParserName = "serializer" + counter.incrementAndGet();
                var parameter = ParameterSpec.builder(ParameterizedTypeName.get(serializer, valueType.typeName()), valueParserName);
                var tags = valueType.tag();
                if (!tags.isEmpty()) {
                    parameter.addAnnotation(TagUtils.makeAnnotationSpec(tags));
                }
                builder.addParameter(parameter.build());
                parameters.put(valueType, valueParserName);
                builder.addCode(", $N", valueParserName);
            }
        }
        builder.addCode("$<\n);$<\n");
        builder.addCode("};\n");
        return builder.build();
    }

    public void generatePublisherImplementation(TypeElement publisher, List<ExecutableElement> publishMethods, AnnotationMirror publisherAnnotation) throws IOException {
        var packageName = this.elements.getPackageOf(publisher).getQualifiedName().toString();
        var implementationName = NameUtils.generatedType(publisher, "Impl");
        var topicConfigName = NameUtils.generatedType(publisher, "TopicConfig");
        var topicConfigTypeName = ClassName.get(packageName, topicConfigName);

        var b = CommonUtils.extendsKeepAop(publisher, implementationName)
            .addOriginatingElement(publisher)
            .addSuperinterface(generatedPublisher)
            .addField(ClassName.get(Properties.class), "driverProperties", Modifier.PRIVATE, Modifier.FINAL)
            .addField(topicConfigTypeName, "topicConfig", Modifier.PRIVATE, Modifier.FINAL)
            .addField(KafkaClassNames.producerTelemetryFactory, "telemetryFactory", Modifier.PRIVATE, Modifier.FINAL)
            .addField(ParameterizedTypeName.get(producer, ArrayTypeName.of(TypeName.BYTE), ArrayTypeName.of(TypeName.BYTE)), "delegate", Modifier.PRIVATE, Modifier.VOLATILE)
            .addField(KafkaClassNames.producerTelemetry, "telemetry", Modifier.PRIVATE, Modifier.VOLATILE)
            .addMethod(MethodSpec.methodBuilder("init")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(Override.class)
                .addCode("this.delegate = new $T<>(driverProperties, new $T(), new $T());\n", KafkaClassNames.kafkaProducer, byteArraySerializer, byteArraySerializer)
                .addCode("this.telemetry = this.telemetryFactory.get(this.delegate, driverProperties);\n", KafkaClassNames.kafkaProducer)
                .build())
            .addMethod(MethodSpec.methodBuilder("release")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(Override.class)
                .beginControlFlow("if (this.delegate != null)")
                .addStatement("this.delegate.close()")
                .addStatement("this.delegate = null")
                .beginControlFlow("if (this.telemetry != null)")
                .addStatement("this.telemetry.close()")
                .addStatement("this.telemetry = null")
                .endControlFlow()
                .endControlFlow()
                .build())
            .addMethod(MethodSpec.methodBuilder("telemetry")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(Override.class)
                .returns(producerTelemetry)
                .addCode("return telemetry;")
                .build())
            .addMethod(MethodSpec.methodBuilder("producer")
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(producer, ArrayTypeName.of(TypeName.BYTE), ArrayTypeName.of(TypeName.BYTE)))
                .addStatement("return this.delegate")
                .build()
            );
        var constructorBuilder = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(producerTelemetryFactory, "telemetryFactory")
            .addParameter(ClassName.get(Properties.class), "driverProperties")
            .addParameter(topicConfigTypeName, "topicConfig")
            .addStatement("this.driverProperties = driverProperties")
            .addStatement("this.telemetryFactory = telemetryFactory")
            .addStatement("this.topicConfig = topicConfig");
        record TypeWithTag(TypeName typeName, Set<String> tag) {}
        var parameters = new HashMap<TypeWithTag, String>();
        var counter = new AtomicInteger(0);
        for (int i = 0; i < publishMethods.size(); i++) {
            var publishMethod = publishMethods.get(i);
            var publishData = KafkaPublisherUtils.parsePublisherType(publishMethod);
            var keyParserName = (String) null;
            if (publishData.keyType() != null) {
                var keyType = new TypeWithTag(publishData.keyType(), publishData.keyTag());
                keyParserName = parameters.get(keyType);
                if (keyParserName == null) {
                    keyParserName = "serializer" + counter.incrementAndGet();
                    var type = ParameterizedTypeName.get(serializer, keyType.typeName());
                    b.addField(type, keyParserName, Modifier.PRIVATE, Modifier.FINAL);
                    var parameter = ParameterSpec.builder(type, keyParserName);
                    var tags = keyType.tag();
                    if (!tags.isEmpty()) {
                        parameter.addAnnotation(TagUtils.makeAnnotationSpec(tags));
                    }
                    constructorBuilder.addParameter(parameter.build());
                    constructorBuilder.addStatement("this.$N = $N", keyParserName, keyParserName);
                    parameters.put(keyType, keyParserName);
                }
            }

            var valueType = new TypeWithTag(publishData.valueType(), publishData.valueTag());
            var valueParserName = parameters.get(valueType);
            if (valueParserName == null) {
                valueParserName = "serializer" + counter.incrementAndGet();
                var type = ParameterizedTypeName.get(serializer, valueType.typeName());
                b.addField(type, valueParserName, Modifier.PRIVATE, Modifier.FINAL);
                var parameter = ParameterSpec.builder(type, valueParserName);
                var tags = valueType.tag();
                if (!tags.isEmpty()) {
                    parameter.addAnnotation(TagUtils.makeAnnotationSpec(tags));
                }
                constructorBuilder.addParameter(parameter.build());
                constructorBuilder.addStatement("this.$N = $N", valueParserName, valueParserName);
                parameters.put(valueType, valueParserName);
            }
            var topicVariable = "topic" + i;
            var method = generatePublisherExecutableMethod(publishMethod, publishData, topicVariable, keyParserName, valueParserName);
            b.addMethod(method);
        }

        b.addMethod(constructorBuilder.build());

        JavaFile.builder(packageName, b.build())
            .build()
            .writeTo(this.processingEnv.getFiler());
    }

    private MethodSpec generatePublisherExecutableMethod(ExecutableElement publishMethod, KafkaPublisherUtils.PublisherData publishData, String topicVariable, String keyParserName, String valueParserName) {
        var methodBuilder = CommonUtils.overridingKeepAop(publishMethod);
        var b = CodeBlock.builder();
        if (publishData.recordVar() != null) {
            var record = publishData.recordVar().getSimpleName();
            b.addStatement("var _headers = $N.headers()", record);
            b.addStatement("var _key = $N.serialize($N.topic(), _headers, $N.key())", keyParserName, record, record);
            b.addStatement("var _value = $N.serialize($N.topic(), _headers, $N.value())", valueParserName, record, record);
            b.addStatement("var _record = new $T<>($N.topic(), $N.partition(), $N.timestamp(), _key, _value, _headers)", producerRecord, record, record, record);
        } else {
            b.addStatement("var _topic = this.topicConfig.$N().topic()", topicVariable);
            b.addStatement("var _partition = this.topicConfig.$N().partition()", topicVariable);
            if (publishData.headersVar() == null) {
                b.addStatement("var _headers = new $T()", recordHeaders);
            } else {
                b.addStatement("var _headers = $N", publishData.headersVar().getSimpleName());
            }
            if (publishData.keyVar() == null) {
                b.addStatement("var _key = new byte[0]");
            } else {
                b.addStatement("var _key = $N.serialize(_topic, _headers, $N)", keyParserName, publishData.keyVar().getSimpleName());
            }
            b.addStatement("var _value = $N.serialize(_topic, _headers, $N)", valueParserName, publishData.valueVar().getSimpleName());
            b.addStatement("var _record = new $T<>(_topic, _partition, null, _key, _value, _headers)", producerRecord);
        }
        b.addStatement("var _tctx = this.telemetry.record(_record)");

        if (isFuture(publishMethod.getReturnType())) {
            if (publishData.callback() != null) {
                b.add("return this.delegate.send(_record, (_meta, _ex) -> {$>\n");
                b.addStatement("_tctx.onCompletion(_meta, _ex)");
                b.addStatement("$N.onCompletion(_meta, _ex)", publishData.callback().getSimpleName());
                b.add("$<\n});\n");
            } else {
                b.add("return this.delegate.send(_record, _tctx);");
            }
        } else {
            b.add("try {$>\n");
            if (!MethodUtils.isVoid(publishMethod)) {
                b.add("return ");
            }
            if (publishData.callback() != null) {
                b.add("this.delegate.send(_record, (_meta, _ex) -> {$>\n");
                b.addStatement("_tctx.onCompletion(_meta, _ex)");
                b.add("$N.onCompletion(_meta, _ex);", publishData.callback().getSimpleName());
                b.add("$<\n}).get();");
            } else {
                b.add("this.delegate.send(_record, _tctx).get();");
            }
            b.add("$<\n} catch (InterruptedException e) {$>\n");
            b.add("throw new $T(e);", recordPublisherException);
            b.add("$<\n} catch ($T e) {$>\n", ExecutionException.class);
            b.add("if (e.getCause() instanceof RuntimeException re) throw re;\n");
            b.add("if (e.getCause() != null) throw new $T(e.getCause());\n", recordPublisherException);
            b.add("throw new $T(e);", recordPublisherException);
            b.add("$<\n} catch (Exception e) {$>\n");
            b.add("if (e.getCause() != null) throw new $T(e.getCause());\n", recordPublisherException);
            b.add("throw new $T(e);", recordPublisherException);
            b.add("$<\n}\n");
        }
        methodBuilder.addCode(b.build());
        return methodBuilder.build();
    }

    private boolean isFuture(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }

        if (!(type instanceof DeclaredType dt)) {
            return false;
        }

        final String name = dt.asElement().toString();
        return name.equals(Future.class.getCanonicalName());

    }

    public void generateConfig(TypeElement producer, List<ExecutableElement> publishMethods) throws IOException {
        var record = new RecordClassBuilder(NameUtils.generatedType(producer, "TopicConfig"))
            .addModifier(Modifier.PUBLIC)
            .originatingElement(producer);
        for (int i = 0; i < publishMethods.size(); i++) {
            if (AnnotationUtils.isAnnotationPresent(publishMethods.get(0), kafkaTopicAnnotation)) {
                record.addComponent("topic" + i, publisherTopicConfig);
            }
        }
        var packageName = this.elements.getPackageOf(producer).getQualifiedName().toString();
        record.writeTo(processingEnv.getFiler(), packageName);
    }

    public MethodSpec buildTopicConfigMethod(TypeElement producer, List<ExecutableElement> publishMethods, AnnotationMirror publisherAnnotation) {
        var packageName = this.elements.getPackageOf(producer).getQualifiedName().toString();
        var configName = NameUtils.generatedType(producer, "TopicConfig");
        var configTypeName = ClassName.get(packageName, configName);

        var m = MethodSpec.methodBuilder(CommonUtils.decapitalize(configName))
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addParameter(CommonClassNames.config, "config")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.configValueExtractor, publisherTopicConfig), "parser")
            .returns(configTypeName)
            .addCode("return new $T(\n$>", configTypeName);

        var root = Objects.requireNonNull(AnnotationUtils.<String>parseAnnotationValueWithoutDefault(publisherAnnotation, "value"));
        for (int i = 0; i < publishMethods.size(); i++) {
            var method = publishMethods.get(i);
            var annotation = AnnotationUtils.findAnnotation(method, kafkaTopicAnnotation);
            if (annotation != null) {
                var path = Objects.requireNonNull(AnnotationUtils.<String>parseAnnotationValueWithoutDefault(annotation, "value"));
                if (path.startsWith(".")) {
                    path = root + path;
                }
                if (i > 0) {
                    m.addCode(",\n");
                }
                m.addCode("parser.extract(config.get($S))", path);
            }
        }
        m.addCode("$<\n);\n");

        return m.build();
    }
}
