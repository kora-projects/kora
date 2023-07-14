package ru.tinkoff.kora.kora.app.annotation.processor.app;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;

import java.util.List;

@KoraApp
public interface AppWithGenericWithArrays {

    @Root
    default Generic<Integer> genericInt() {
        return t -> t;
    }

    @Root
    default Generic<List<Integer>> genericListInt() {
        return t -> t;
    }

    @Root
    default Generic<List<byte[]>> genericListByteArr() {
        return t -> t;
    }

    @Root
    default Generic<List<Object[]>> genericListObjectArr() {
        return t -> t;
    }


    interface Generic<T> {
        T to(T t);
    }
}
