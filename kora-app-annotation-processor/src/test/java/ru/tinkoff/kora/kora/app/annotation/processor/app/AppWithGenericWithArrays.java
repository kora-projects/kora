package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;

import java.util.List;

@KoraApp
public interface AppWithGenericWithArrays {

    default Generic<Integer> genericInt() {
        return t -> t;
    }

    default Generic<List<Integer>> genericListInt() {
        return t -> t;
    }

    default Generic<List<byte[]>> genericListByteArr() {
        return t -> t;
    }
    default Generic<List<Object[]>> genericListObjectArr() {
        return t -> t;
    }


    interface Generic<T> extends MockLifecycle {
        T to(T t);
    }
}
