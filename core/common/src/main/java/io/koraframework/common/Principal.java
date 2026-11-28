package io.koraframework.common;

import io.koraframework.common.context.*;
import org.jspecify.annotations.*;

import java.util.function.*;

/**
 * <b>Русский</b>: Является интерфейсом контекста авторизации.
 * Реализация авторизации должна наследовать этот интерфейс.
 * <hr>
 * <b>English</b>: Represents interface of the authorization context.
 * An authorization implementation must inherit this interface.
 */
public interface Principal {

    KoraContext.Key<Principal> KEY = KoraContext.Key.create();

    /**
     * @return <b>Русский</b>: Текущий контекст авторизации.
     * <hr>
     * <b>English</b>: The current authorization context.
     */
    @Nullable
    static Principal current() {
        KoraContext current = KoraContext.current();
        return current != null ? current.get(KEY) : null;
    }

    /**
     * Позволяет временно подменить или установить Principal в текущем контексте (например, для внутренних системных вызовов).
     * Адаптирован под синтаксис Java 25 без генерации лямбд.
     */
    static <T, X extends Throwable> T with(Principal principal, SupplierOp<T, X> op) throws X {
        KoraContext current = KoraContext.current();
        KoraContext updatedContext = (current != null ? current : KoraContext.empty())
            .with(KEY, principal);

        try {
            return KoraContext.runWith(updatedContext, new PrincipalExecutionRunner<>(op));
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                @SuppressWarnings("unchecked")
                X throwable = (X) cause;
                throw throwable;
            }
            throw e;
        }
    }

    /**
     * Функциональный интерфейс для операций, способных выбрасывать проверяемые исключения.
     * Заменяет устаревший ScopedValue.CallableOp из ранних Preview-версий Java.
     */
    @FunctionalInterface
    interface SupplierOp<T, X extends Throwable> {
        T get() throws X;
    }

    final class PrincipalExecutionRunner<T, X extends Throwable> implements Supplier<T> {
        private final SupplierOp<T, X> op;

        public PrincipalExecutionRunner(SupplierOp<T, X> op) {
            this.op = op;
        }

        @Override
        public T get() {
            try {
                return op.get();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }
}
