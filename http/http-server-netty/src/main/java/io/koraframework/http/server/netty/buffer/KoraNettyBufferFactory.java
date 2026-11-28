package io.koraframework.http.server.netty.buffer;

import io.netty.buffer.*;
import org.jspecify.annotations.*;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public final class KoraNettyBufferFactory {

    public static final int MAX_COMPOSITE_COMPONENTS = 4096;

    private final ByteBufAllocator allocator;

    public KoraNettyBufferFactory(ByteBufAllocator allocator) {
        this.allocator = Objects.requireNonNull(allocator, "allocator");
    }

    public KoraReadBuffer createEmpty() {
        return new KoraReadBuffer(Unpooled.EMPTY_BUFFER);
    }

    public KoraReadBuffer copyOf(CharSequence cs, Charset charset) {
        var byteBuf = charset == StandardCharsets.UTF_8
            ? ByteBufUtil.writeUtf8(allocator, cs)
            : ByteBufUtil.encodeString(allocator, CharBuffer.wrap(cs), charset);
        return adapt(byteBuf);
    }

    public KoraReadBuffer copyOf(InputStream stream) throws IOException {
        var buffer = allocator.buffer();
        boolean free = true;
        try {
            while (buffer.writeBytes(stream, 4096) != -1) {
            }
            free = false;
            return adapt(buffer);
        } finally {
            if (free) {
                buffer.release();
            }
        }
    }

    @Nullable
    public KoraReadBuffer copyOf(ScatteringByteChannel channel, int n) throws IOException {
        var bb = allocator.buffer(n);
        int actual;
        try {
            actual = bb.writeBytes(channel, n);
        } catch (Throwable e) {
            bb.release();
            throw e;
        }
        if (actual < 0) {
            bb.release();
            return null;
        } else if (actual > 0) {
            return adapt(bb);
        } else {
            bb.release();
            return createEmpty();
        }
    }

    public KoraReadBuffer copyOf(ByteBuffer nioBuffer) {
        if (!nioBuffer.hasRemaining()) {
            return createEmpty();
        }
        var bb = allocator.buffer(nioBuffer.remaining());
        boolean done = false;
        try {
            bb.writeBytes(nioBuffer);
            done = true;
            return adapt(bb);
        } finally {
            if (!done) {
                bb.release();
            }
        }
    }

    public KoraReadBuffer adapt(ByteBuffer nioBuffer) {
        if (!nioBuffer.hasRemaining()) {
            return createEmpty();
        }
        return adapt(Unpooled.wrappedBuffer(nioBuffer));
    }

    public KoraReadBuffer adapt(byte[] array) {
        if (array.length == 0) {
            return createEmpty();
        }
        return adapt(Unpooled.wrappedBuffer(array));
    }

    public KoraReadBuffer adapt(ByteBuf buffer) {
        return new KoraReadBuffer(buffer);
    }

    public static ByteBuf toByteBuf(KoraReadBuffer readBuffer) {
        return readBuffer.toByteBuf();
    }

    public KoraReadBuffer compose(Iterable<KoraReadBuffer> buffers) {
        var itr = buffers.iterator();
        if (!itr.hasNext()) {
            return createEmpty();
        }
        var first = itr.next();
        if (!itr.hasNext()) {
            return first;
        }

        var composite = allocator.compositeBuffer(MAX_COMPOSITE_COMPONENTS);
        boolean firstAdded = false;
        try {
            composite.addComponent(true, toByteBuf(first));
            firstAdded = true;
            while (itr.hasNext()) {
                var next = itr.next();
                composite.addComponent(true, toByteBuf(next));
            }
            return adapt(composite);
        } catch (Throwable e) {
            if (!firstAdded) {
                first.close();
            }
            composite.release();
            while (itr.hasNext()) {
                try {
                    itr.next().close();
                } catch (Throwable f) {
                    e.addSuppressed(f);
                }
            }
            throw e;
        }
    }
}
