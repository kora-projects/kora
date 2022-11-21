package ru.tinkoff.kora.database.annotation.processor.model;

import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

public sealed interface QueryResult {
    TypeName type();

    interface ReactiveResult {}

    record SimpleResult(TypeName type) implements QueryResult {}

    record ResultWithMapper(TypeName type, CommonUtils.MappingData mappingData) implements QueryResult {}

    record MonoResult(TypeName type, QueryResult result) implements QueryResult, ReactiveResult {
        public MonoResult {
            if (!(result instanceof SimpleResult || result instanceof ResultWithMapper)) {
                throw new ProcessingErrorException("Invalid mono type", null);
            }
        }
    }
    record FluxResult(TypeName type, QueryResult result) implements QueryResult, ReactiveResult {
        public FluxResult {
            if (!(result instanceof SimpleResult || result instanceof ResultWithMapper)) {
                throw new ProcessingErrorException("Invalid flux type", null);
            }
        }
    }
}
