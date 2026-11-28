package io.koraframework.http.server.netty.request;

import io.netty.buffer.*;
import io.netty.handler.codec.*;
import io.netty.handler.codec.http.*;
import io.netty.util.*;
import org.jspecify.annotations.*;

import java.util.*;

import static io.netty.buffer.ByteBufUtil.*;

public final class FlatHttpHeaders extends HttpHeaders {

    private static final byte[] MIN_INT_BYTES = "-2147483648".getBytes(CharsetUtil.US_ASCII);
    private static final int COLON_SPACE_SHORT = (':' << 8) | ' ';
    private static final int CRLF_SHORT = ('\r' << 8) | '\n';

    private Object[] entries;
    private int size;

    public FlatHttpHeaders(int initialPairs) {
        this.entries = new Object[Math.max(4, initialPairs) << 1];
    }

    public void encodeTo(ByteBuf buf) {
        for (int i = 0, len = size << 1; i < len; i += 2) {
            CharSequence name = (CharSequence) entries[i];
            Object rawVal = entries[i + 1];

            writeAscii(buf, name);
            writeShortBE(buf, COLON_SPACE_SHORT);
            if (rawVal instanceof CharSequence cs) {
                writeAscii(buf, cs);
            } else if (rawVal instanceof Integer intVal) {
                writeIntAscii(buf, intVal);
            } else if (rawVal instanceof Short shortVal) {
                writeIntAscii(buf, shortVal);
            } else if (rawVal instanceof Long longVal) {
                writeLongAscii(buf, longVal);
            } else {
                writeAscii(buf, rawVal.toString());
            }
            writeShortBE(buf, CRLF_SHORT);
        }
    }

    private static void writeIntAscii(ByteBuf buf, int value) {
        if (value == 0) {
            buf.writeByte('0');
            return;
        }
        if (value == Integer.MIN_VALUE) {
            buf.writeBytes(MIN_INT_BYTES);
            return;
        }
        if (value < 0) {
            buf.writeByte('-');
            value = -value;
        }
        int digits = stringSize(value);
        int writerIdx = buf.writerIndex();
        buf.ensureWritable(digits);
        int pos = writerIdx + digits - 1;
        while (value >= 10) {
            int q = value / 10;
            buf.setByte(pos--, (byte) ('0' + (value - q * 10)));
            value = q;
        }
        buf.setByte(pos, (byte) ('0' + value));
        buf.writerIndex(writerIdx + digits);
    }

    private static void writeLongAscii(ByteBuf buf, long value) {
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            writeIntAscii(buf, (int) value);
            return;
        }
        writeAscii(buf, Long.toString(value));
    }

    private static int stringSize(int x) {
        int d = 1;
        if (x >= 0) {
            d = 0;
            x = -x;
        }
        int p = -10;
        for (int i = 1; i < 10; i++) {
            if (x > p) {
                return i + d;
            }
            p = 10 * p;
        }
        return 10 + d;
    }

    @Override
    public HttpHeaders add(CharSequence name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        int idx = size << 1;
        if (idx == entries.length) {
            entries = Arrays.copyOf(entries, entries.length << 1);
        }
        entries[idx] = name;
        entries[idx + 1] = value;
        size++;
        return this;
    }

    @Override
    public HttpHeaders add(String name, Object value) {
        return add((CharSequence) name, value);
    }

    @Override
    public HttpHeaders add(CharSequence name, Iterable<?> values) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(values, "values");
        for (Object value : values) {
            add(name, value);
        }
        return this;
    }

    @Override
    public HttpHeaders add(String name, Iterable<?> values) {
        return add((CharSequence) name, values);
    }

    @Override
    public HttpHeaders set(CharSequence name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        int len = size << 1;
        for (int i = 0; i < len; i += 2) {
            if (AsciiString.contentEqualsIgnoreCase(name, (CharSequence) entries[i])) {
                entries[i + 1] = value;
                for (int j = i + 2; j < len; j += 2) {
                    if (AsciiString.contentEqualsIgnoreCase(name, (CharSequence) entries[j])) {
                        System.arraycopy(entries, j + 2, entries, j, len - j - 2);
                        entries[len - 2] = null;
                        entries[len - 1] = null;
                        len -= 2;
                        size--;
                        j -= 2;
                    }
                }
                return this;
            }
        }
        return add(name, value);
    }

    @Override
    public HttpHeaders set(String name, Object value) {
        return set((CharSequence) name, value);
    }

    @Override
    public HttpHeaders set(CharSequence name, Iterable<?> values) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(values, "values");
        remove(name);
        for (Object value : values) {
            add(name, value);
        }
        return this;
    }

    @Override
    public HttpHeaders set(String name, Iterable<?> values) {
        return set((CharSequence) name, values);
    }

    @Override
    public HttpHeaders setShort(CharSequence name, short value) {
        return set(name, value);
    }

    @Override
    public HttpHeaders addInt(CharSequence name, int value) {
        return add(name, value);
    }

    @Override
    public HttpHeaders addShort(CharSequence name, short value) {
        return add(name, value);
    }

    @Override
    public HttpHeaders setInt(CharSequence name, int value) {
        return set(name, value);
    }

    @Override
    @Nullable
    public String get(CharSequence name) {
        for (int i = 0, len = size << 1; i < len; i += 2) {
            if (AsciiString.contentEqualsIgnoreCase(name, (CharSequence) entries[i])) {
                return entries[i + 1].toString();
            }
        }
        return null;
    }

    @Override
    @Nullable
    public String get(String name) {
        return get((CharSequence) name);
    }

    @Override
    @Nullable
    public Integer getInt(CharSequence name) {
        String v = get(name);
        return v != null ? Integer.valueOf(v) : null;
    }

    @Override
    public int getInt(CharSequence name, int defaultValue) {
        String v = get(name);
        return v != null ? Integer.parseInt(v) : defaultValue;
    }

    @Override
    @Nullable
    public Short getShort(CharSequence name) {
        String v = get(name);
        return v != null ? Short.valueOf(v) : null;
    }

    @Override
    public short getShort(CharSequence name, short defaultValue) {
        String v = get(name);
        return v != null ? Short.parseShort(v) : defaultValue;
    }

    @Override
    @Nullable
    public Long getTimeMillis(CharSequence name) {
        String v = get(name);
        if (v == null) {
            return null;
        }
        Date date = DateFormatter.parseHttpDate(v);
        return date != null ? date.getTime() : null;
    }

    @Override
    public long getTimeMillis(CharSequence name, long defaultValue) {
        Long v = getTimeMillis(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public List<String> getAll(CharSequence name) {
        String first = null;
        List<String> result = null;
        for (int i = 0, len = size << 1; i < len; i += 2) {
            if (AsciiString.contentEqualsIgnoreCase(name, (CharSequence) entries[i])) {
                String val = entries[i + 1].toString();
                if (first == null) {
                    first = val;
                } else {
                    if (result == null) {
                        result = new ArrayList<>(4);
                        result.add(first);
                    }
                    result.add(val);
                }
            }
        }
        if (result != null) {
            return result;
        }
        return first != null ? List.of(first) : List.of();
    }

    @Override
    public List<String> getAll(String name) {
        return getAll((CharSequence) name);
    }

    @Override
    public List<Map.Entry<String, String>> entries() {
        if (size == 0) {
            return Collections.emptyList();
        }
        List<Map.Entry<String, String>> list = new ArrayList<>(size);
        for (int i = 0, len = size << 1; i < len; i += 2) {
            list.add(Map.entry(entries[i].toString(), entries[i + 1].toString()));
        }
        return list;
    }

    @Override
    public boolean contains(CharSequence name) {
        for (int i = 0, len = size << 1; i < len; i += 2) {
            if (AsciiString.contentEqualsIgnoreCase(name, (CharSequence) entries[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(String name) {
        return contains((CharSequence) name);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
        for (int i = 0, len = size << 1; i < len; i += 2) {
            var k = (CharSequence) entries[i];
            if (name == k || AsciiString.contentEqualsIgnoreCase(name, k)) {
                Object raw = entries[i + 1];
                if (value == raw) {
                    return true;
                }
                CharSequence v = raw instanceof CharSequence cs ? cs : raw.toString();
                if (ignoreCase ? AsciiString.contentEqualsIgnoreCase(value, v) : AsciiString.contentEquals(value, v)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean contains(String name, String value, boolean ignoreCase) {
        return contains((CharSequence) name, (CharSequence) value, ignoreCase);
    }

    @Override
    public HttpHeaders remove(CharSequence name) {
        int len = size << 1;
        for (int i = 0; i < len; i += 2) {
            if (AsciiString.contentEqualsIgnoreCase(name, (CharSequence) entries[i])) {
                System.arraycopy(entries, i + 2, entries, i, len - i - 2);
                entries[len - 2] = null;
                entries[len - 1] = null;
                len -= 2;
                size--;
                i -= 2;
            }
        }
        return this;
    }

    @Override
    public HttpHeaders remove(String name) {
        return remove((CharSequence) name);
    }

    @Override
    public HttpHeaders clear() {
        Arrays.fill(entries, 0, size << 1, null);
        size = 0;
        return this;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public Set<String> names() {
        if (size == 0) {
            return Collections.emptySet();
        }
        Set<String> names = new LinkedHashSet<>(size);
        for (int i = 0, len = size << 1; i < len; i += 2) {
            names.add(entries[i].toString());
        }
        return names;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return new Iterator<>() {
            int idx = 0;

            @Override
            public boolean hasNext() {
                return idx < (size << 1);
            }

            @Override
            public Map.Entry<String, String> next() {
                if (idx >= (size << 1)) {
                    throw new NoSuchElementException();
                }
                String k = entries[idx].toString();
                String v = entries[idx + 1].toString();
                idx += 2;
                return Map.entry(k, v);
            }
        };
    }

    @Override
    public Iterator<Map.Entry<CharSequence, CharSequence>> iteratorCharSequence() {
        return new Iterator<>() {
            int idx = 0;

            @Override
            public boolean hasNext() {
                return idx < (size << 1);
            }

            @Override
            public Map.Entry<CharSequence, CharSequence> next() {
                if (idx >= (size << 1)) {
                    throw new NoSuchElementException();
                }
                CharSequence k = (CharSequence) entries[idx];
                Object rawVal = entries[idx + 1];
                CharSequence v = rawVal instanceof CharSequence cs ? cs : rawVal.toString();
                idx += 2;
                return Map.entry(k, v);
            }
        };
    }
}
