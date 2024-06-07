package ru.tinkoff.kora.application.graph;

public interface Wrapped<T> {

    T value();

    class UnwrappedValue<T> implements ValueOf<T> {
        private final ValueOf<Wrapped<T>> value;

        public UnwrappedValue(ValueOf<Wrapped<T>> value) {
            this.value = value;
        }

        public static <T> UnwrappedValue<T> unwrap(ValueOf<Wrapped<T>> valueOf) {
            return new UnwrappedValue<>(valueOf);
        }

        @Override
        public T get() {
            return this.value.get().value();
        }

        @Override
        public void refresh() {
            this.value.refresh();
        }
    }
}
