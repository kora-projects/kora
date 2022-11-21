package ru.tinkoff.kora.kafka.annotation.processor;

import com.squareup.javapoet.TypeName;

public record ConsumerContainerData(
    TypeName keyType,
    TypeName valueType
) {
}
