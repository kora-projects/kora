package ru.tinkoff.kora.common.util;

import kotlin.coroutines.CoroutineContext;
import kotlin.jvm.functions.Function2;
import ru.tinkoff.kora.common.Context;

import javax.annotation.Nonnull;

public record CoroutineContextElement(Context value) implements CoroutineContext.Element {
    public static final Key<CoroutineContextElement> KEY = new Key<>() {
    };

    @Nonnull
    @Override
    public Key<?> getKey() {
        return KEY;
    }

    @Override
    public <R> R fold(R r, @Nonnull Function2<? super R, ? super Element, ? extends R> function2) {
        return DefaultImpls.fold(this, r, function2);
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public <E extends Element> E get(@Nonnull Key<E> key) {
        return DefaultImpls.get(this, key);
    }

    @Nonnull
    @Override
    public CoroutineContext minusKey(@Nonnull Key<?> key) {
        return DefaultImpls.minusKey(this, key);
    }

    @Nonnull
    @Override
    public CoroutineContext plus(@Nonnull CoroutineContext coroutineContext) {
        return DefaultImpls.plus(this, coroutineContext);
    }
}
