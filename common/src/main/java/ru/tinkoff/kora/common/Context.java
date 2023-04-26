package ru.tinkoff.kora.common;

import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.ThreadContextElementKt;
import kotlinx.coroutines.reactor.ReactorContextKt;
import ru.tinkoff.kora.common.util.CoroutineContextElement;
import ru.tinkoff.kora.common.util.ReactorContextHook;

import javax.annotation.Nullable;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Context {
    private static final ThreadLocal<Context> INSTANCE = ThreadLocal.withInitial(Context::new);
    private final ConcurrentHashMap<Context.Key<?>, Object> values;

    @Override
    public String toString() {
        return "Context{" + values + '}';
    }

    private Context(ConcurrentHashMap<Key<?>, Object> values) {
        this.values = values;
    }

    private Context() {
        this(new ConcurrentHashMap<>(8));
    }

    public static Context current() {
        return INSTANCE.get();
    }

    public Context fork() {
        return new Context(this.values.entrySet()
            .stream()
            .map(e -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), copy(e.getKey(), e.getValue())))
            .filter(e -> e.getValue() != null)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (a, b) -> a,
                ConcurrentHashMap::new
            )));
    }

    public void inject() {
        INSTANCE.set(this);
    }

    public static Context clear() {
        var clean = new Context();
        INSTANCE.set(clean);
        return clean;
    }

    public <T> T set(Key<T> key, T value) {
        this.values.put(key, value);
        return value;
    }

    public <T> void remove(Key<T> key) {
        this.values.remove(key);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        return (T) this.values.get(key);
    }

    public static class Reactor {
        public static Context current(reactor.util.context.ContextView ctx) {
            if (ctx.hasKey(Context.class)) {
                return ctx.get(Context.class);
            }
            return new Context();
        }

        public static reactor.util.context.Context inject(reactor.util.context.ContextView ctxView, Context ctx) {
            return reactor.util.context.Context.of(ctxView).put(Context.class, ctx);
        }
    }

    public static class Kotlin {

        public static Context current(CoroutineContext ctx) {
            var context = ctx.get(CoroutineContextElement.KEY);
            if (context == null) {
                return new Context();
            } else {
                return context.value();
            }
        }

        public static CoroutineContext inject(CoroutineContext cctx, Context context) {
            var reactorContext = Reactor.inject(reactor.util.context.Context.of(Context.class, cctx), context);
            var coroutineContext = (CoroutineContext) (Object) ReactorContextKt.asCoroutineContext(reactorContext);

            return cctx.plus(coroutineContext).plus(asCoroutineContext(context));
        }

        public static CoroutineContext asCoroutineContext(Context ctx) {
            return ThreadContextElementKt.asContextElement(INSTANCE, ctx);
        }
    }


    public static abstract class Key<T> {
        @Override
        public final int hashCode() {
            return super.hashCode();
        }

        @Override
        public final boolean equals(Object obj) {
            return obj == this;
        }

        protected abstract T copy(T object);
    }

    public static abstract class KeyImmutable<T> extends Key<T> {
        protected T copy(T object) {
            return object;
        }
    }


    @SuppressWarnings("unchecked")
    private static <T> T copy(Key<T> key, Object value) {
        return key.copy((T) value);
    }

    static {
        ReactorContextHook.init();
    }
}
