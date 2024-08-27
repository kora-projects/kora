package ru.tinkoff.kora.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

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
     * <p/>
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
            this.value = bytes;
        } else {
            double v = (double) bytes / type.toBytes();
            BigDecimal bd = BigDecimal.valueOf(v);
            bd = bd.setScale(2, RoundingMode.HALF_UP);
            this.value = bd.doubleValue();
        }
    }

    /**
     * @return exact even value of current size type
     */
    public long valueExact() {
        return ((long) value);
    }

    /**
     * @return value of current size type round to scale 2
     */
    public double valueRounded() {
        return value;
    }

    public double valueRounded(int scale) {
        if(scale < 1) {
            return valueExact();
        }

        double v = (double) bytes / type.toBytes();
        BigDecimal bd = BigDecimal.valueOf(v);
        bd = bd.setScale(scale, RoundingMode.HALF_UP);
        return bd.doubleValue();
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

        for (Type sizeType : BINARY) {
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

        for (Type sizeType : SI) {
            if (bytes >= sizeType.toBytes()) {
                return new Size(bytes, sizeType);
            }
        }

        return new Size(bytes, Type.BYTES);
    }

    public static Size parse(String value) throws IllegalArgumentException {
        Long size = null;
        Type type = null;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c)) {
                try {
                    String v = value.substring(0, i).strip();
                    size = Long.parseLong(v);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Can't extract size number part from: " + value);
                }

                String typeAsStr = value.substring(i).strip();
                for (Type sizeType : ALL) {
                    if (sizeType == Type.BYTES && typeAsStr.equalsIgnoreCase("B")) {
                        type = sizeType;
                        break;
                    } else if (sizeType.name().equalsIgnoreCase(typeAsStr)) {
                        type = sizeType;
                        break;
                    }
                }

                if (type == null) {
                    throw new IllegalArgumentException("Can't extract size type part from: " + value);
                }
                break;
            }
        }

        if (size == null) {
            try {
                long bytes = Long.parseLong(value);
                return new Size(bytes, Type.BYTES);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Malformed value, can't be size: " + value);
            }
        } else if (size < 0) {
            throw new IllegalArgumentException("Malformed negative value, can't be size: " + size);
        } else {
            return of(size, type);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Size size = (Size) o;
        return bytes == size.bytes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bytes);
    }

    @Override
    public String toString() {
        if (type == Type.BYTES) {
            return bytes + "B";
        } else {
            return BigDecimal.valueOf(valueRounded()).stripTrailingZeros().toPlainString() + type.name();
        }
    }
}
