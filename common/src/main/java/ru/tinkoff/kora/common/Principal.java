package ru.tinkoff.kora.common;

import jakarta.annotation.Nullable;

/**
 * <b>Русский</b>: Является интерфейсом контекста авторизации.
 * Реализация авторизации должна наследовать этот интерфейс.
 * <hr>
 * <b>English</b>: Represents interface of the authorization context.
 * An authorization implementation must inherit this interface.
 */
public interface Principal {

    ScopedValue<Principal> VALUE = ScopedValue.newInstance();

    /**
     * @return <b>Русский</b>: Текущий контекст авторизации.
     * <hr>
     * <b>English</b>: The current authorization context.
     */
    @Nullable
    static Principal current() {
        if (VALUE.isBound()) {
            return VALUE.get();
        } else {
            return null;
        }
    }

    static <T, X extends Throwable> T with(Principal principal, ScopedValue.CallableOp<T, X> op) throws X {
        return ScopedValue.where(VALUE, principal).call(op);
    }
}
