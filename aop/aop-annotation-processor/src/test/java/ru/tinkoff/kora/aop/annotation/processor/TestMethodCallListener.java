package ru.tinkoff.kora.aop.annotation.processor;

public interface TestMethodCallListener {
    void before(String annotationValue);

    void after(String annotationValue, Object result);

    void thrown(String annotationValue, Throwable throwable);
}
