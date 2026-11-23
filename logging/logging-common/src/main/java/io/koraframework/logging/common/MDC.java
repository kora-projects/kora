package io.koraframework.logging.common;

import io.koraframework.common.context.*;
import io.koraframework.logging.common.arg.*;
import tools.jackson.core.*;

import java.util.*;

public class MDC {

    public static final KoraContext.Key<MDC> MDC_KEY = KoraContext.Key.create();

    private static final Object[] EMPTY = new Object[0];

    private volatile Object[] entries;

    public MDC() {
        this.entries = EMPTY;
    }

    private MDC(Object[] entries) {
        this.entries = entries;
    }

    public MDC fork() {
        return new MDC(this.entries);
    }

    public Map<String, StructuredArgumentWriter> values() {
        Object[] cur = this.entries;
        int len = cur.length;
        if (len == 0) {
            return Map.of();
        }
        if (len == 2) {
            return Map.of((String) cur[0], (StructuredArgumentWriter) cur[1]);
        }
        Map<String, StructuredArgumentWriter> map = HashMap.newHashMap(len >> 1);
        for (int i = 0; i < len; i += 2) {
            map.put((String) cur[i], (StructuredArgumentWriter) cur[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    public void remove0(String key) {
        Object[] cur = this.entries;
        int len = cur.length;
        if (len == 0) return;

        int idx = indexOf(cur, key);
        if (idx < 0) return;

        if (len == 2) {
            this.entries = EMPTY;
            return;
        }
        var next = new Object[len - 2];
        if (idx > 0) {
            System.arraycopy(cur, 0, next, 0, idx);
        }
        if (idx + 2 < len) {
            System.arraycopy(cur, idx + 2, next, idx, len - idx - 2);
        }
        this.entries = next;
    }

    public void put0(String key, StructuredArgumentWriter value) {
        Object[] cur = this.entries;
        int len = cur.length;
        if (len == 0) {
            this.entries = new Object[] {key, value};
            return;
        }
        int idx = indexOf(cur, key);
        if (idx >= 0) {
            if (cur[idx + 1] == value) return;
            Object[] next = cur.clone();
            next[idx + 1] = value;
            this.entries = next;
        } else {
            Object[] next = Arrays.copyOf(cur, len + 2);
            next[len] = key;
            next[len + 1] = value;
            this.entries = next;
        }
    }

    private static int indexOf(Object[] entries, String key) {
        for (int i = 0; i < entries.length; i += 2) {
            if (entries[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    public void put0(String key, Integer value) {
        this.put0(key, value == null ? NullStructuredArgumentWriter.INSTANCE : new IntStructuredArgumentWriter(value));
    }

    public void put0(String key, int value) {
        this.put0(key, new IntStructuredArgumentWriter(value));
    }

    public void put0(String key, Long value) {
        this.put0(key, value == null ? NullStructuredArgumentWriter.INSTANCE : new LongStructuredArgumentWriter(value));
    }

    public void put0(String key, long value) {
        this.put0(key, new LongStructuredArgumentWriter(value));
    }

    public void put0(String key, String value) {
        this.put0(key, value == null ? NullStructuredArgumentWriter.INSTANCE : new StringStructuredArgumentWriter(value));
    }

    public void put0(String key, Boolean value) {
        if (value == null) {
            this.put0(key, NullStructuredArgumentWriter.INSTANCE);
        } else {
            this.put0(key, value ? BooleanStructuredArgumentWriter.TRUE : BooleanStructuredArgumentWriter.FALSE);
        }
    }

    public void put0(String key, boolean value) {
        this.put0(key, value ? BooleanStructuredArgumentWriter.TRUE : BooleanStructuredArgumentWriter.FALSE);
    }

    public static MDC get() {
        KoraContext current = KoraContext.current();
        return current != null ? current.get(MDC_KEY) : null;
    }

    public static void put(String key, String value) {
        MDC mdc = get();
        if (mdc != null) mdc.put0(key, value);
    }

    public static void put(String key, Integer value) {
        MDC mdc = get();
        if (mdc != null) mdc.put0(key, value);
    }

    public static void put(String key, int value) {
        MDC mdc = get();
        if (mdc != null) mdc.put0(key, value);
    }

    public static void put(String key, Long value) {
        MDC mdc = get();
        if (mdc != null) mdc.put0(key, value);
    }

    public static void put(String key, long value) {
        MDC mdc = get();
        if (mdc != null) mdc.put0(key, value);
    }

    public static void put(String key, Boolean value) {
        MDC mdc = get();
        if (mdc != null) mdc.put0(key, value);
    }

    public static void put(String key, boolean value) {
        MDC mdc = get();
        if (mdc != null) mdc.put0(key, value);
    }

    public static void put(String key, StructuredArgumentWriter value) {
        MDC mdc = get();
        if (mdc != null) mdc.put0(key, value);
    }

    public static void remove(String key) {
        MDC mdc = get();
        if (mdc != null) mdc.remove0(key);
    }

    public static final class NullStructuredArgumentWriter implements StructuredArgumentWriter {
        public static final NullStructuredArgumentWriter INSTANCE = new NullStructuredArgumentWriter();

        private NullStructuredArgumentWriter() {}

        @Override
        public void writeTo(JsonGenerator gen) {
            gen.writeNull();
        }
    }

    public static final class IntStructuredArgumentWriter implements StructuredArgumentWriter {
        private final int val;

        public IntStructuredArgumentWriter(int val) {this.val = val;}

        @Override
        public void writeTo(JsonGenerator gen) {
            gen.writeNumber(this.val);
        }
    }

    public static final class LongStructuredArgumentWriter implements StructuredArgumentWriter {
        private final long val;

        public LongStructuredArgumentWriter(long val) {this.val = val;}

        @Override
        public void writeTo(JsonGenerator gen) {
            gen.writeNumber(this.val);
        }
    }

    public static final class StringStructuredArgumentWriter implements StructuredArgumentWriter {
        private final String val;

        public StringStructuredArgumentWriter(String val) {this.val = val;}

        @Override
        public void writeTo(JsonGenerator gen) {
            gen.writeString(this.val);
        }
    }

    public static final class BooleanStructuredArgumentWriter implements StructuredArgumentWriter {
        public static final BooleanStructuredArgumentWriter TRUE = new BooleanStructuredArgumentWriter(true);
        public static final BooleanStructuredArgumentWriter FALSE = new BooleanStructuredArgumentWriter(false);

        private final boolean val;

        public BooleanStructuredArgumentWriter(boolean val) {this.val = val;}

        @Override
        public void writeTo(JsonGenerator gen) {
            gen.writeBoolean(this.val);
        }
    }
}
