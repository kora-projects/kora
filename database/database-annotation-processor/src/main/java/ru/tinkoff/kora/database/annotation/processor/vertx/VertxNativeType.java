package ru.tinkoff.kora.database.annotation.processor.vertx;

import com.squareup.javapoet.CodeBlock;

public interface VertxNativeType {
    CodeBlock extract(String rowName, String indexName);
}
