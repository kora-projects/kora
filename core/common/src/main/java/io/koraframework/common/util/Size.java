package io.koraframework.common.util;

import java.util.*;

public final class Size {

    private static final long BYTE = 1L;
    private static final long KiB = BYTE << 10;
    private static final long MiB = KiB << 10;
    private static final long GiB = MiB << 10;
    private static final long TiB = GiB << 10;
    private static final long PiB = TiB << 10;
    private static final long EiB = PiB << 10;

    private static final long KB = BYTE * 1000;
    private static final long MB = KB * 1000;
    private static final long GB = MB * 1000;
    private static final long TB = GB * 1000;
    private static final long PB = TB * 1000;
    private static final long EB = PB * 1000;

    /**
     * Binary format have letter 'i' in its name
     * <a href="https://blocksandfiles.com/2022/04/23/decimal-and-binary-prefixes/">Decimal and Binary Prefixes</a>
     */
    public enum Type {
        BYTES(Size.BYTE),   // 1 byte
        KB(Size.KB),        // 10^3 bytes
        KiB(Size.KiB),      // 2^10 bytes
        MB(Size.MB),        // 10^6 bytes
        MiB(Size.MiB),      // 2^20 bytes
        GB(Size.GB),        // 10^9 bytes
        GiB(Size.GiB),      // 2^30 bytes
        TB(Size.TB),        // 10^12 bytes
        TiB(Size.TiB),      // 2^40 bytes
        PB(Size.PB),        // 10^15 bytes
        PiB(Size.PiB),      // 2^50 bytes
        EB(Size.EB),        // 10^18 bytes
        EiB(Size.EiB);      // 2^60 bytes

        private final long bytes;

        Type(long bytes) {
            this.bytes = bytes;
        }

        public long toBytes() {
            return bytes;
        }
    }

    private static final List<Type> BINARY = List.of(Type.EiB, Type.PiB, Type.TiB, Type.GiB, Type.MiB, Type.KiB, Type.BYTES);
    private static final List<Type> SI = List.of(Type.EB, Type.PB, Type.TB, Type.GB, Type.MB, Type.KB, Type.BYTES);
    private static final List<Type> ALL = List.of(Type.values());

    private final long bytes;
    private final double value;
    private final Type type;

    private Size(long bytes, Type type) {
        this.bytes = bytes;
        this.type = type;

        if (type == Type.BYTES) {
            value = bytes;
        } else {
            var v = (double) bytes / type.toBytes();
            value = Math.round(v * 100.0) / 100.0;
        }
    }

    /**
     * @return exact even value of current size type
     */
    public long valueExact() {
        return (long) value;
    }

    /**
     * @return value of current size type round to scale 2
     */
    public double valueRounded() {
        return value;
    }

    public double valueRounded(int scale) {
        if (scale < 1) {
            return valueExact();
        } else if (scale == 2) {
            return valueRounded();
        }

        var v = (double) bytes / type.toBytes();
        var factor = Math.pow(10, scale);
        return Math.round(v * factor) / factor;
    }

    public Type type() {
        return type;
    }

    public long toBytes() {
        return bytes;
    }

    public Size to(Type type) {
        if (type == this.type) {
            return this;
        } else {
            return new Size(bytes, type);
        }
    }

    public static Size of(long size, Type type) {
        return new Size(size * type.toBytes(), type);
    }

    /**
     * Converts bytes to binary format <a href="https://blocksandfiles.com/2022/04/23/decimal-and-binary-prefixes/">Decimal and Binary Prefixes</a>
     *
     * @param bytes bytes to convert to size
     * @return size in binary format
     */
    public static Size ofBytesBinary(long bytes) throws IllegalArgumentException {
        if (bytes < 0) {
            throw new IllegalArgumentException("Malformed negative value, can't be size: " + bytes);
        }

        for (var sizeType : BINARY) {
            if (bytes >= sizeType.toBytes()) {
                return new Size(bytes, sizeType);
            }
        }

        return new Size(bytes, Type.BYTES);
    }

    /**
     * Converts bytes to decimal format <a href="https://blocksandfiles.com/2022/04/23/decimal-and-binary-prefixes/">Decimal and Binary Prefixes</a>
     *
     * @param bytes bytes to convert to size
     * @return size in decimal format
     */
    public static Size ofBytesDecimal(long bytes) throws IllegalArgumentException {
        if (bytes < 0) {
            throw new IllegalArgumentException("Malformed negative value, can't be size: " + bytes);
        }

        for (var sizeType : SI) {
            if (bytes >= sizeType.toBytes()) {
                return new Size(bytes, sizeType);
            }
        }

        return new Size(bytes, Type.BYTES);
    }

    public static Size parse(String value) throws IllegalArgumentException {
        Long size = null;
        Type type = null;

        int len = value.length();
        for (var i = 0; i < len; i++) {
            var c = value.charAt(i);
            if (Character.isLetter(c)) {
                try {
                    int numStart = 0;
                    while (numStart < i && value.charAt(numStart) <= ' ') numStart++;
                    int numEnd = i;
                    while (numEnd > numStart && value.charAt(numEnd - 1) <= ' ') numEnd--;

                    size = parseLongRaw(value, numStart, numEnd);
                    if (size < 0) {
                        throw new IllegalArgumentException("Malformed negative value, can't be size: " + value);
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Can't extract size number part from: " + value);
                }

                int typeStart = i;
                while (typeStart < len && value.charAt(typeStart) <= ' ') typeStart++;
                int typeEnd = len;
                while (typeEnd > typeStart && value.charAt(typeEnd - 1) <= ' ') typeEnd--;
                int typeLen = typeEnd - typeStart;

                if (typeLen == 1 && (value.charAt(typeStart) == 'b' || value.charAt(typeStart) == 'B')) {
                    type = Type.BYTES;
                } else {
                    type = matchEnumRaw(value, typeStart, typeEnd);
                }

                if (type == null) {
                    throw new IllegalArgumentException("Can't extract size type part from: " + value);
                }
                break;
            }
        }

        if (size == null) {
            try {
                int start = 0;
                while (start < len && value.charAt(start) <= ' ') start++;
                int end = len;
                while (end > start && value.charAt(end - 1) <= ' ') end--;

                var bytes = parseLongRaw(value, start, end);
                if (bytes < 0) {
                    throw new IllegalArgumentException("Malformed negative value, can't be size: " + value);
                }
                return new Size(bytes, Type.BYTES);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Malformed value, can't be size: " + value);
            }
        } else {
            return of(size, type);
        }
    }

    private static long parseLongRaw(String s, int start, int end) {
        if (start >= end) throw new NumberFormatException();
        long result = 0;
        boolean negative = false;
        int i = start;
        char firstChar = s.charAt(start);
        if (firstChar < '0') {
            if (firstChar == '-') { negative = true; i++; }
            else if (firstChar == '+') { i++; }
            if (i == end) throw new NumberFormatException();
        }
        while (i < end) {
            char c = s.charAt(i++);
            if (c < '0' || c > '9') throw new NumberFormatException();
            result = result * 10 + (c - '0');
        }
        return negative ? -result : result;
    }

    private static Type matchEnumRaw(String s, int start, int end) {
        int targetLen = end - start;
        for (Type t : Type.values()) {
            String name = t.name();
            if (name.length() == targetLen && s.regionMatches(true, start, name, 0, targetLen)) {
                return t;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Size size)) return false;
        return bytes == size.bytes;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(bytes);
    }

    @Override
    public String toString() {
        if (type == Type.BYTES) {
            return bytes + "B";
        }
        if (value == (long) value) {
            return String.format(java.util.Locale.ROOT, "%d%s", (long) value, type.name());
        }
        return String.format(java.util.Locale.ROOT, "%.2f%s", value, type.name());
    }
}
