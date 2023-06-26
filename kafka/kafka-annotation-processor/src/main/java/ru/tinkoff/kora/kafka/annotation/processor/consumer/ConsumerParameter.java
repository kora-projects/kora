package ru.tinkoff.kora.kafka.annotation.processor.consumer;

import ru.tinkoff.kora.kafka.annotation.processor.utils.KafkaUtils;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

public sealed interface ConsumerParameter {
    VariableElement element();

    record Consumer(VariableElement element, TypeMirror key, TypeMirror value) implements ConsumerParameter {}

    record Records(VariableElement element, TypeMirror key, TypeMirror value) implements ConsumerParameter {}

    record Exception(VariableElement element) implements ConsumerParameter {}

    record KeyDeserializationException(VariableElement element) implements ConsumerParameter {}

    record ValueDeserializationException(VariableElement element) implements ConsumerParameter {}

    record Record(VariableElement element, TypeMirror key, TypeMirror value) implements ConsumerParameter {}

    record RecordsTelemetry(VariableElement element, TypeMirror key, TypeMirror value) implements ConsumerParameter {}

    record Unknown(VariableElement element) implements ConsumerParameter {}

    static List<ConsumerParameter> parseParameters(ExecutableElement executableElement) {
        var result = new ArrayList<ConsumerParameter>(executableElement.getParameters().size());
        for (var parameter : executableElement.getParameters()) {
            var type = parameter.asType();
            if (KafkaUtils.isConsumerRecord(type)) {
                var dt = (DeclaredType) type;
                result.add(new ConsumerParameter.Record(parameter, dt.getTypeArguments().get(0), dt.getTypeArguments().get(1)));
                continue;
            }
            if (KafkaUtils.isConsumerRecords(type)) {
                var dt = (DeclaredType) type;
                result.add(new ConsumerParameter.Records(parameter, dt.getTypeArguments().get(0), dt.getTypeArguments().get(1)));
                continue;
            }
            if (KafkaUtils.isConsumer(type)) {
                var dt = (DeclaredType) type;
                result.add(new ConsumerParameter.Consumer(parameter, dt.getTypeArguments().get(0), dt.getTypeArguments().get(1)));
                continue;
            }
            if (KafkaUtils.isRecordsTelemetry(type)) {
                var dt = (DeclaredType) type;
                result.add(new ConsumerParameter.RecordsTelemetry(parameter, dt.getTypeArguments().get(0), dt.getTypeArguments().get(1)));
                continue;
            }
            if (KafkaUtils.isKeyDeserializationException(type)) {
                result.add(new ConsumerParameter.KeyDeserializationException(parameter));
                continue;
            }
            if (KafkaUtils.isValueDeserializationException(type)) {
                result.add(new ConsumerParameter.ValueDeserializationException(parameter));
                continue;
            }
            if (KafkaUtils.isAnyException(type)) {
                result.add(new ConsumerParameter.Exception(parameter));
                continue;
            }
            result.add(new ConsumerParameter.Unknown(parameter));
        }
        return result;
    }

}
