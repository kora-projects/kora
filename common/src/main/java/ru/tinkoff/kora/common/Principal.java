package ru.tinkoff.kora.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.naming.NameConverter;

/**
 * <b>Русский</b>: Является интерфейсом контекста авторизации в рамках запроса от HTTP сервера {@link Context}.
 * Реализация авторизации должна наследовать этот интерфейс.
 * <hr>
 * <b>English</b>: Represents interface of the authorisation context within the request from the HTTP server {@link Context}.
 * An authorisation implementation must inherit this interface.
 */
public interface Principal {

    Context.Key<Principal> KEY = new Context.KeyImmutable<>() {};

    /**
     * @return <b>Русский</b>: Текущий контекст авторизации в рамках контекста {@link Context} текущего HTTP запроса.
     * <hr>
     * <b>English</b>: The current authorisation context within the context {@link Context} of the current HTTP request.
     */
    @Nullable
    static Principal current() {
        return current(Context.current());
    }

    /**
     * @return <b>Русский</b>: Контекст авторизации в рамках переданного контекста {@link Context}.
     * <hr>
     * <b>English</b>: Authorisation context within the passed context {@link Context}.
     */
    @Nullable
    static Principal current(Context context) {
        return context.get(KEY);
    }

    /**
     * @return <b>Русский</b>: Проставляет контекст авторизации в рамках переданного контекста {@link Context} HTTP запроса.
     * <hr>
     * <b>English</b>: Sets the authorisation context within the passed context {@link Context} of the HTTP request.
     */
    @Nullable
    static Principal set(Context context, Principal principal) {
        return context.set(KEY, principal);
    }
}
