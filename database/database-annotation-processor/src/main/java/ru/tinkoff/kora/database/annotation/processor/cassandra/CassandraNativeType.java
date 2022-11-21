package ru.tinkoff.kora.database.annotation.processor.cassandra;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;

import java.util.function.BiFunction;

public interface CassandraNativeType {
    TypeName type();

    CodeBlock extract(String rs, CodeBlock index);

    default CodeBlock bind(String stmt, String variableName, int idx) {
        return bind(stmt, variableName, CodeBlock.of("$L", idx));
    }

    CodeBlock bind(String stmt, String variableName, CodeBlock idx);

    default CassandraNativeType boxed() {
        return of(type().box(), this::extract, this::bind);
    }

    static CassandraNativeType of(
        TypeName type,
        BiFunction<String, CodeBlock, CodeBlock> extract,
        TriFunction<String, String, CodeBlock, CodeBlock> bind) {
        record Impl(
            TypeName type,
            BiFunction<String, CodeBlock, CodeBlock> extract,
            TriFunction<String, String, CodeBlock, CodeBlock> bind
        ) implements CassandraNativeType {
            @Override
            public CodeBlock extract(String rsName, CodeBlock index) {
                return this.extract.apply(rsName, index);
            }

            @Override
            public CodeBlock bind(String stmt, String variableName, CodeBlock idx) {
                return this.bind.apply(stmt, variableName, idx);
            }

            @Override
            public CassandraNativeType boxed() {
                return CassandraNativeType.of(
                    this.type.box(),
                    this.extract,
                    this.bind
                );
            }
        }
        return new Impl(type, extract, bind);
    }

    @FunctionalInterface
    interface TriFunction<P1, P2, P3, R> {
        R apply(P1 p1, P2 p2, P3 p3);
    }
}
