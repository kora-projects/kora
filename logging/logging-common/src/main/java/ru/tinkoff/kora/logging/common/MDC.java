package ru.tinkoff.kora.logging.common;

import com.fasterxml.jackson.core.JsonGenerator;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.logging.common.arg.StructuredArgumentWriter;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MDC {
    private final ConcurrentHashMap<String, StructuredArgumentWriter> values;


    private static final Context.Key<MDC> MDC = new Context.Key<>() {
        @Override
        protected MDC copy(MDC object) {
            return object == null
                ? new MDC()
                : new MDC(object.values);
        }
    };

    private MDC() {
        this.values = new ConcurrentHashMap<>();
    }

    private MDC(ConcurrentHashMap<String, StructuredArgumentWriter> values) {
        this.values = new ConcurrentHashMap<>(values);
    }

    public Map<String, StructuredArgumentWriter> values() {
        return Collections.unmodifiableMap(this.values);
    }

    public void remove0(String key) {
        this.values.remove(key);
    }

    public void put0(String key, StructuredArgumentWriter value) {
        this.values.put(key, value);
    }

    public void put0(String key, Integer value) {
        if (value == null) {
            this.values.put(key, JsonGenerator::writeNull);
        } else {
            this.values.put(key, gen -> gen.writeNumber(value));
        }
    }

    public void put0(String key, Long value) {
        if (value == null) {
            this.values.put(key, JsonGenerator::writeNull);
        } else {
            this.values.put(key, gen -> gen.writeNumber(value));
        }
    }

    public void put0(String key, String value) {
        if (value == null) {
            this.values.put(key, JsonGenerator::writeNull);
        } else {
            this.values.put(key, gen -> gen.writeString(value));
        }
    }

    public void put0(String key, Boolean value) {
        if (value == null) {
            this.values.put(key, JsonGenerator::writeNull);
        } else {
            this.values.put(key, gen -> gen.writeBoolean(value));
        }
    }


    public static MDC get(Context ctx) {
        var mdc = ctx.get(MDC);
        if (mdc == null) {
            mdc = new MDC();
            ctx.set(MDC, mdc);
        }
        return mdc;
    }

    public static MDC get() {
        return get(Context.current());
    }

    public static void put(String key, String value) {
        get().put0(key, value);
    }

    public static void put(String key, Integer value) {
        get().put0(key, value);
    }

    public static void put(String key, Long value) {
        get().put0(key, value);
    }

    public static void put(String key, Boolean value) {
        get().put0(key, value);
    }

    public static void put(String key, StructuredArgumentWriter value) {
        get().put0(key, value);
    }

    public static void put(Context ctx, String key, StructuredArgumentWriter value) {
        get(ctx).put0(key, value);
    }

    public static void remove(String key) {
        get().remove0(key);
    }
}
