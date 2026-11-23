package io.koraframework.kora.app.annotation.processor.otherpackage;

import io.koraframework.application.graph.All;

/**
 * Module that requests {@code All<T>} of a type that cannot be named outside of this package
 */
public interface HiddenHandlerModule {

    final class Handlers {
        private final int size;

        private Handlers(int size) {
            this.size = size;
        }

        public int size() {
            return size;
        }
    }

    class PublicHandler implements HiddenHandler {}

    default Handlers handlers(All<HiddenHandler> all) {
        var size = 0;
        for (var _ : all) {
            size++;
        }
        return new Handlers(size);
    }
}

interface HiddenHandler {}
