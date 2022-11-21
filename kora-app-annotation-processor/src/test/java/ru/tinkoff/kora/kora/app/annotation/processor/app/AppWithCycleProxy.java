package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface AppWithCycleProxy {
    default MockLifecycle someClass(JsonWriter<Class1> w1, JsonWriter<Class3> w3) {
        return new MockLifecycle() {};
    }

    default Writer1 writer1(JsonWriter<Class2> w) {
        return new Writer1();
    }

    default Writer2 writer2(JsonWriter<Class1> w) {
        return new Writer2();
    }

    default Writer3 writer3(JsonWriter<Class4> w) {
        return new Writer3();
    }

    default Writer4 writer4(JsonWriter<Class3> w) {
        return new Writer4();
    }

    interface JsonWriter<T> {
        void write(T test);

        String writeWithReturn(T test);
    }

    class Class1 {}

    class Class2 {}

    class Class3 {}

    class Class4 {}

    class Writer1 implements JsonWriter<Class1> {
        @Override
        public void write(Class1 test) {

        }

        @Override
        public String writeWithReturn(Class1 test) {
            return "";
        }
    }

    class Writer2 implements JsonWriter<Class2> {
        @Override
        public void write(Class2 test) {

        }

        @Override
        public String writeWithReturn(Class2 test) {
            return "";
        }
    }

    class Writer3 implements JsonWriter<Class3> {
        @Override
        public void write(Class3 test) {

        }

        @Override
        public String writeWithReturn(Class3 test) {
            return "";
        }
    }

    class Writer4 implements JsonWriter<Class4> {
        @Override
        public void write(Class4 test) {

        }

        @Override
        public String writeWithReturn(Class4 test) {
            return "";
        }
    }


}
