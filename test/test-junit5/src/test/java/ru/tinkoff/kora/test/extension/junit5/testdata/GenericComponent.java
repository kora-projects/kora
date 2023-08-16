package ru.tinkoff.kora.test.extension.junit5.testdata;

public interface GenericComponent<T> {
    record IntGenericComponent() implements GenericComponent<Integer> {}

    record StringGenericComponent() implements GenericComponent<String> {}
}
