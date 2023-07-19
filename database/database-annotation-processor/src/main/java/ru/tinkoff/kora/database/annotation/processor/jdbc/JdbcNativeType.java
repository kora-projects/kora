package ru.tinkoff.kora.database.annotation.processor.jdbc;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;

import java.util.function.BiFunction;

public interface JdbcNativeType {
    TypeName type();

    CodeBlock extract(String rs, CodeBlock index);

    CodeBlock bind(String stmt, String variableName, int idx);

    CodeBlock bindNull(String stmt, int idx);

    default JdbcNativeType boxed() {
        return of(type().box(), this::extract, this::bind, this::bindNull);
    }

    static JdbcNativeType of(
        TypeName type,
        BiFunction<String, CodeBlock, CodeBlock> extract,
        TriFunction<String, String, Integer, CodeBlock> bind,
        BiFunction<String, Integer, CodeBlock> bindNull) {
        record Impl(
            TypeName type,
            BiFunction<String, CodeBlock, CodeBlock> extract,
            TriFunction<String, String, Integer, CodeBlock> bind,
            BiFunction<String, Integer, CodeBlock> bindNull
        ) implements JdbcNativeType {
            @Override
            public CodeBlock extract(String rsName, CodeBlock index) {
                return this.extract.apply(rsName, index);
            }

            @Override
            public CodeBlock bind(String stmt, String variableName, int idx) {
                return this.bind.apply(stmt, variableName, idx);
            }

            @Override
            public CodeBlock bindNull(String stmt, int idx) {
                return this.bindNull.apply(stmt, idx);
            }

            @Override
            public JdbcNativeType boxed() {
                return JdbcNativeType.of(
                    this.type.box(),
                    this.extract,
                    this.bind,
                    this.bindNull
                );
            }
        }
        return new Impl(type, extract, bind, bindNull);
    }

    @FunctionalInterface
    interface TriFunction<P1, P2, P3, R> {
        R apply(P1 p1, P2 p2, P3 p3);
    }
}
