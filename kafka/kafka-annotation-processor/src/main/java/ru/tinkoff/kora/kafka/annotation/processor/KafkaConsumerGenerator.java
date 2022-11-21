package ru.tinkoff.kora.kafka.annotation.processor;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.common.Tag;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static ru.tinkoff.kora.kafka.annotation.processor.KafkaClassNames.*;
import static ru.tinkoff.kora.kafka.annotation.processor.utils.KafkaUtils.prepareMethodName;
import static ru.tinkoff.kora.kafka.annotation.processor.utils.KafkaUtils.prepareTagName;

public class KafkaConsumerGenerator {
    private final ProcessingEnvironment processingEnv;
    private final Types types;
    private final Elements elements;
    private final TypeMirror exceptionType;
    private final TypeMirror consumerRecordType;
    private final TypeMirror consumerRecordsType;
    private final TypeMirror consumerType;

    public KafkaConsumerGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();

        this.exceptionType = this.elements.getTypeElement(Exception.class.getCanonicalName()).asType();
        this.consumerRecordType = this.types.erasure(this.elements.getTypeElement(KafkaClassNames.consumerRecord.canonicalName()).asType());
        this.consumerRecordsType = this.types.erasure(this.elements.getTypeElement(KafkaClassNames.consumerRecords.canonicalName()).asType());
        this.consumerType = this.types.erasure(this.elements.getTypeElement(KafkaClassNames.consumer.canonicalName()).asType());
    }

    @Nullable
    public MethodSpec generate(ExecutableElement executableElement) {
        var listenerAnnotation = AnnotationUtils.findAnnotation(executableElement, kafkaIncoming);
        if (listenerAnnotation == null) {
            return null;
        }
        var controller = (TypeElement) executableElement.getEnclosingElement();
        var methodName = prepareMethodName(controller.getSimpleName().toString(), executableElement.getSimpleName().toString());

        var methodBuilder = MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addParameter(TypeName.get(controller.asType()), "_controller");

        var tagName = prepareTagName(controller.getSimpleName().toString(), executableElement.getSimpleName().toString());
        var tagsBlock = CodeBlock.builder().add("{");
        tagsBlock.add("$L.class", tagName);
        tagsBlock.add("}");

        var propertiesParameter = ParameterSpec
            .builder(kafkaConsumerConfig, "_consumerConfig");

        propertiesParameter.addAnnotation(
            AnnotationSpec.builder(Tag.class).addMember("value", tagsBlock.build()).build()
        );

        methodBuilder.addParameter(propertiesParameter.build());
        var consumerData = extractConsumerData(executableElement);

        if (consumerData == null) {
            throw new ProcessingErrorException("Unsupported signature for @KafkaIncoming", executableElement);
        }

        methodBuilder.addParameter(ParameterizedTypeName.get(deserializer, consumerData.keyType()), "keyDeserializer");
        methodBuilder.addParameter(ParameterizedTypeName.get(deserializer, consumerData.valueType()), "valueDeserializer");
        methodBuilder.addParameter(ParameterizedTypeName.get(kafkaConsumerTelemetry, consumerData.keyType(), consumerData.valueType()), "telemetry");
        methodBuilder.returns(ParameterizedTypeName.get(kafkaConsumerContainer, consumerData.keyType(), consumerData.valueType()));

        var returnBlock = CodeBlock.of(
            """
                    return new $T<>(
                            _consumerConfig,
                            keyDeserializer,
                            valueDeserializer,
                            $T.wrapHandler(telemetry, _controller::$L)
                );""",
            kafkaConsumerContainer,
            handlerWrapper,
            executableElement.getSimpleName().toString()
        );
        methodBuilder.addCode(returnBlock);

        return methodBuilder.build();
    }

    private ConsumerContainerData extractConsumerData(ExecutableElement executableElement) {
        var params = executableElement.getParameters();

        if (params.size() == 3) {

            if (!this.types.isSameType(params.get(2).asType(), this.exceptionType)) {
                return null;
            }

            return new ConsumerContainerData(
                TypeName.get(params.get(0).asType()),
                TypeName.get(params.get(1).asType())
            );

        } else if (params.size() == 2) {
            TypeMirror erasure = this.types.erasure(params.get(0).asType());
            if (this.types.isSameType(erasure, consumerRecordsType) || this.types.isSameType(erasure, consumerRecordType)) {
                if (!this.types.isSameType(this.types.erasure(params.get(1).asType()), consumerType)) {
                    throw new ProcessingErrorException("Second argument should have type org.apache.kafka.clients.consumer.Consumer", executableElement);
                }

                var args = ((DeclaredType) params.get(0).asType()).getTypeArguments();
                return new ConsumerContainerData(
                    TypeName.get(args.get(0)),
                    TypeName.get(args.get(1))
                );
            }

            if (this.types.isSameType(params.get(1).asType(), this.exceptionType)) {
                return new ConsumerContainerData(
                    TypeName.get(byte[].class),
                    TypeName.get(params.get(0).asType())
                );
            }

            return new ConsumerContainerData(
                TypeName.get(params.get(0).asType()),
                TypeName.get(params.get(1).asType())
            );

        } else if (params.size() == 1) {
            TypeMirror erasure = this.types.erasure(params.get(0).asType());
            if (this.types.isSameType(erasure, consumerRecordsType) || this.types.isSameType(erasure, consumerRecordType)) {
                var args = ((DeclaredType) params.get(0).asType()).getTypeArguments();
                return new ConsumerContainerData(
                    TypeName.get(args.get(0)),
                    TypeName.get(args.get(1))
                );
            }

            return new ConsumerContainerData(
                TypeName.get(byte[].class),
                TypeName.get(params.get(0).asType())
            );
        }

        return null;
    }


}
