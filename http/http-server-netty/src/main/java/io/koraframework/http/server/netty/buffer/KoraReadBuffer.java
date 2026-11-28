package io.koraframework.http.server.netty.buffer;

import io.netty.buffer.*;
import io.netty.util.*;
import org.jspecify.annotations.*;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.function.*;

public final class KoraReadBuffer implements AutoCloseable {

    private static final boolean STRICT_REFCNT = Boolean.getBoolean("io.kora.buffer.netty.KoraReadBuffer.STRICT_REFCNT");

    @Nullable
    private ByteBuf buf;

    public KoraReadBuffer(ByteBuf buf) {
        checkAccessible(buf);
        if (STRICT_REFCNT) {
            ByteBuf copy = buf.copy();
            buf.release();
            this.buf = copy;
        } else {
            this.buf = buf;
        }
    }

    private static void checkAccessible(ByteBuf buf) {
        if (buf.refCnt() <= 0) {
            throw new IllegalReferenceCountException(buf.refCnt());
        }
    }

    private ByteBuf getBuf() {
        ByteBuf currentBuf = this.buf;
        if (currentBuf == null) {
            throw new IllegalStateException("Buffer has already been consumed or released");
        }
        checkAccessible(currentBuf);
        return currentBuf;
    }

    public int readable() {
        return getBuf().readableBytes();
    }

    public KoraReadBuffer duplicate() {
        return new KoraReadBuffer(getBuf().retainedDuplicate());
    }

    public KoraReadBuffer split(int splitPosition) {
        return new KoraReadBuffer(getBuf().readRetainedSlice(splitPosition));
    }

    public KoraReadBuffer move() {
        ByteBuf b = getBuf();
        this.buf = null;
        return new KoraReadBuffer(b);
    }

    public void toArray(byte[] destination, int offset) throws IndexOutOfBoundsException {
        ByteBuf b = getBuf();
        try {
            this.buf = null;
            if (offset > destination.length) {
                throw new IndexOutOfBoundsException("Offset exceeds length");
            }
            b.readBytes(destination, offset, b.readableBytes());
        } finally {
            b.release();
        }
    }

    public byte[] toArray() {
        ByteBuf b = getBuf();
        try {
            this.buf = null;
            return ByteBufUtil.getBytes(b);
        } finally {
            b.release();
        }
    }

    public String toString(Charset charset) {
        ByteBuf b = getBuf();
        try {
            this.buf = null;
            return b.toString(charset);
        } finally {
            b.release();
        }
    }

    public InputStream toInputStream() {
        ByteBuf b = getBuf();
        this.buf = null;
        return new ByteBufInputStream(b, true);
    }

    @Nullable
    public <R> R useFastHeapBuffer(Function<ByteBuffer, R> function) {
        ByteBuf b = getBuf();
        if (b.hasArray()) {
            this.buf = null;
            try {
                return function.apply(ByteBuffer.wrap(b.array(), b.arrayOffset() + b.readerIndex(), b.readableBytes()));
            } finally {
                b.release();
            }
        } else {
            byte[] bytes = toArray();
            return function.apply(ByteBuffer.wrap(bytes));
        }
    }

    public void transferTo(OutputStream stream) throws IOException {
        ByteBuf b = getBuf();
        this.buf = null;
        try {
            b.readBytes(stream, b.readableBytes());
        } finally {
            b.release();
        }
    }

    public ByteBuf toByteBuf() {
        ByteBuf b = getBuf();
        this.buf = null;
        return b;
    }

    @Override
    public void close() {
        ByteBuf currentBuf = this.buf;
        if (currentBuf != null) {
            currentBuf.release();
            this.buf = null;
        }
    }

    public boolean isConsumed() {
        return buf == null;
    }

    public byte[] peekArray(int n) {
        ByteBuf b = getBuf();
        byte[] arr = new byte[n];
        b.getBytes(b.readerIndex(), arr);
        return arr;
    }
}
