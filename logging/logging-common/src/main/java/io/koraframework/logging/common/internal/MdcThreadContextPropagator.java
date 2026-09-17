package io.koraframework.logging.common.internal;

import io.koraframework.common.context.ThreadContextPropagator;
import io.koraframework.logging.common.MDC;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class MdcThreadContextPropagator implements ThreadContextPropagator {
    @Override
    @Nullable
    public Snapshot capture() {
        if (!MDC.VALUE.isBound()) {
            return null;
        }
        return new MdcSnapshot(MDC.get());
    }

    private record MdcSnapshot(MDC mdc) implements Snapshot {
        @Override
        public Snapshot fork() {
            return new MdcSnapshot(this.mdc.fork());
        }

        @Override
        public <T> T call(Supplier<T> operation) {
            return ScopedValue.where(MDC.VALUE, this.mdc).call(operation::get);
        }
    }
}
