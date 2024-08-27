package ru.tinkoff.kora.common.naming;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.tinkoff.kora.common.util.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SizeTests {

    static Stream<Arguments> binaryArguments() {
        List<Arguments> arguments = new ArrayList<>();
        arguments.add(Arguments.of(Size.Type.BYTES, 10, 10, 10));
        arguments.add(Arguments.of(Size.Type.BYTES, 1000, 1000, 1000));

        for (Size.Type value : Size.Type.values()) {
            if (value.name().contains("i")) {
                arguments.add(Arguments.of(value, 1L * value.toBytes(), 1L, 1.00));
                arguments.add(Arguments.of(value, 2L * value.toBytes() + value.toBytes() / 2, 2L, 2.50));
            }
        }

        return arguments.stream();
    }

    @MethodSource("binaryArguments")
    @ParameterizedTest
    void ofBytesBinary(Size.Type type, long bytes, long expectedExact, double expectedRound) {
        Size size = Size.ofBytesBinary(bytes);

        assertEquals(type, size.type());
        assertEquals(bytes, size.toBytes());
        assertEquals(expectedExact, size.valueExact());
        assertEquals(expectedRound, size.valueRounded());
    }

    @Test
    void checkBinary() {
        Size size = Size.ofBytesBinary(1024 * 1024 + 512 * 1024);

        Size kib = size.to(Size.Type.KiB);
        Size mib = size.to(Size.Type.MiB);
        assertEquals(1536, kib.valueRounded());
        assertEquals(1536, kib.valueExact());
        assertEquals(1, mib.valueExact());
        assertEquals(1.5, mib.valueRounded());
    }


    static Stream<Arguments> binaryParseArguments() {
        return Stream.of(
            Arguments.of("1MiB", Size.of(1, Size.Type.MiB)),
            Arguments.of("1024KiB", Size.of(1, Size.Type.MiB)),
            Arguments.of("1048576B", Size.of(1, Size.Type.MiB)),
            Arguments.of("   1MiB", Size.of(1, Size.Type.MiB)),
            Arguments.of("1024KiB   ", Size.of(1, Size.Type.MiB)),
            Arguments.of("   1048576B   ", Size.of(1, Size.Type.MiB)),
            Arguments.of("1mib", Size.of(1, Size.Type.MiB)),
            Arguments.of("1024kib", Size.of(1, Size.Type.MiB)),
            Arguments.of("1048576b", Size.of(1, Size.Type.MiB)),
            Arguments.of("1048576", Size.of(1, Size.Type.MiB))
        );
    }

    @MethodSource("binaryParseArguments")
    @ParameterizedTest
    void parseBinary(String actual, Size expected) {
        Size actualSize = Size.parse(actual);
        assertEquals(expected, actualSize);
    }

    static Stream<Arguments> siArguments() {
        List<Arguments> arguments = new ArrayList<>();
        arguments.add(Arguments.of(Size.Type.BYTES, 10, 10, 10));
        arguments.add(Arguments.of(Size.Type.BYTES, 100, 100, 100));

        for (Size.Type value : Size.Type.values()) {
            if (!value.name().contains("i") && value != Size.Type.BYTES) {
                arguments.add(Arguments.of(value, 1L * value.toBytes(), 1L, 1.00));
                arguments.add(Arguments.of(value, 2L * value.toBytes() + value.toBytes() / 2, 2L, 2.50));
            }
        }

        return arguments.stream();
    }

    @MethodSource("siArguments")
    @ParameterizedTest
    void ofBytesSI(Size.Type type, long bytes, long expectedExact, double expectedRound) {
        Size size = Size.ofBytesDecimal(bytes);

        assertEquals(type, size.type());
        assertEquals(bytes, size.toBytes());
        assertEquals(expectedExact, size.valueExact());
        assertEquals(expectedRound, size.valueRounded());
    }

    @Test
    void checkSI() {
        Size size = Size.ofBytesDecimal(1024 * 1024 + 512 * 1024);

        Size kib = size.to(Size.Type.KB);
        Size mib = size.to(Size.Type.MB);
        assertEquals(1572.86, kib.valueRounded());
        assertEquals(1572, kib.valueExact());
        assertEquals(1, mib.valueExact());
        assertEquals(1.57, mib.valueRounded());
    }

    static Stream<Arguments> siParseArguments() {
        return Stream.of(
            Arguments.of("1MB", Size.of(1, Size.Type.MB)),
            Arguments.of("1000KB", Size.of(1, Size.Type.MB)),
            Arguments.of("1000000B", Size.of(1, Size.Type.MB)),
            Arguments.of("   1MB", Size.of(1, Size.Type.MB)),
            Arguments.of("1000KB   ", Size.of(1, Size.Type.MB)),
            Arguments.of("   1000000b   ", Size.of(1, Size.Type.MB)),
            Arguments.of("1mb", Size.of(1, Size.Type.MB)),
            Arguments.of("1000kb", Size.of(1, Size.Type.MB)),
            Arguments.of("1000000b", Size.of(1, Size.Type.MB)),
            Arguments.of("1 mb", Size.of(1, Size.Type.MB)),
            Arguments.of("   1 mb", Size.of(1, Size.Type.MB)),
            Arguments.of("1 mb   ", Size.of(1, Size.Type.MB)),
            Arguments.of("1000000", Size.of(1, Size.Type.MB))
        );
    }

    @MethodSource("siParseArguments")
    @ParameterizedTest
    void parseSI(String actual, Size expected) {
        Size actualSize = Size.parse(actual);
        assertEquals(expected, actualSize);
    }
}
