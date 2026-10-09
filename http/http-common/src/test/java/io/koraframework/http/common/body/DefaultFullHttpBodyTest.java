package io.koraframework.http.common.body;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFullHttpBodyTest {

    enum BufferKind {
        HEAP,
        HEAP_WITH_POSITION,
        READ_ONLY,
        DIRECT
    }

    static Stream<Arguments> bodies() {
        var arguments = new ArrayList<Arguments>();
        // around the 1 KiB copy buffer used for buffers without an accessible array
        for (var size : new int[]{0, 1, 1023, 1024, 1025, 2048, 4097}) {
            for (var kind : BufferKind.values()) {
                arguments.add(Arguments.of(size, kind));
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0} bytes, {1} buffer")
    @MethodSource("bodies")
    void writeCopiesExactlyTheRemainingBytesWithoutConsumingThem(int size, BufferKind kind) throws IOException {
        var data = new byte[size];
        new SplittableRandom(size).nextBytes(data);
        var body = new DefaultFullHttpBody(buffer(data, kind), "application/octet-stream");

        assertThat(body.contentLength()).isEqualTo(size);
        for (var i = 0; i < 2; i++) {
            var out = new ByteArrayOutputStream();
            body.write(out);
            assertThat(out.toByteArray()).as("write #%d", i + 1).isEqualTo(data);
        }
        assertThat(body.contentLength()).isEqualTo(size);
        assertThat(body.getFullContentIfAvailable().remaining()).isEqualTo(size);
    }

    private static ByteBuffer buffer(byte[] data, BufferKind kind) {
        return switch (kind) {
            case HEAP -> ByteBuffer.wrap(data);
            case HEAP_WITH_POSITION -> {
                var padded = new byte[data.length + 20];
                System.arraycopy(data, 0, padded, 10, data.length);
                yield ByteBuffer.wrap(padded, 10, data.length);
            }
            case READ_ONLY -> ByteBuffer.wrap(data).asReadOnlyBuffer();
            case DIRECT -> ByteBuffer.allocateDirect(data.length).put(data).flip();
        };
    }
}
