package ru.tinkoff.kora.test.extension.junit5.testdata;

public class TestComponent3 implements LifecycleComponent {

    public String get() {
        return "3";
    }

    @Override
    public void init() {
        // do nothing
    }
}
