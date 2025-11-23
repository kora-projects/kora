package ru.tinkoff.kora.logging.common;

import ru.tinkoff.kora.logging.common.arg.StructuredArgumentWriter;
import tools.jackson.core.JsonGenerator;

import java.util.HashMap;
import java.util.Map;

public class MDC {

    private volatile Map<String, StructuredArgumentWriter> values;

    public static final ScopedValue<MDC> VALUE = ScopedValue.newInstance();

    public MDC() {
        this.values = Map.of();
    }

    public MDC(Map<String, StructuredArgumentWriter> values) {
        this.values = Map.copyOf(values);
    }

    public MDC fork() {
        return new MDC(values);
    }

    public Map<String, StructuredArgumentWriter> values() {
        return this.values;
    }

    public void remove0(String key) {
        if (this.values.containsKey(key)) {
            var copy = new HashMap<>(this.values);
            copy.remove(key);
            this.values = Map.copyOf(copy);
        }
    }

    public void put0(String key, StructuredArgumentWriter value) {
        var copy = new HashMap<>(this.values);
        copy.put(key, value);
        ;
        this.values = Map.copyOf(copy);
    }

    public void put0(String key, Integer value) {
        if (value == null) {
            this.put0(key, JsonGenerator::writeNull);
        } else {
            this.put0(key, gen -> gen.writeNumber(value));
        }
    }

    public void put0(String key, Long value) {
        if (value == null) {
            this.put0(key, JsonGenerator::writeNull);
        } else {
            this.put0(key, gen -> gen.writeNumber(value));
        }
    }

    public void put0(String key, String value) {
        if (value == null) {
            this.put0(key, JsonGenerator::writeNull);
        } else {
            this.put0(key, gen -> gen.writeString(value));
        }
    }

    public void put0(String key, Boolean value) {
        if (value == null) {
            this.put0(key, JsonGenerator::writeNull);
        } else {
            this.put0(key, gen -> gen.writeBoolean(value));
        }
    }


    public static MDC get() {
        return VALUE.get();
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

    public static void remove(String key) {
        get().remove0(key);
    }
}
