package io.koraframework.http.server.netty.buffer;

import io.netty.buffer.*;
import io.netty.util.*;
import org.jspecify.annotations.*;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.concurrent.atomic.*;

public final class ReleasableHeapByteBuf extends AbstractByteBuf {
    private static final AtomicIntegerFieldUpdater<ReleasableHeapByteBuf> REF_CNT_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(ReleasableHeapByteBuf.class, "refCnt");

    private final ByteBufAllocator alloc;
    private final byte[] array;
    private final @Nullable Runnable releaser;
    private volatile int refCnt = 1;

    public ReleasableHeapByteBuf(ByteBufAllocator alloc, byte[] array, int offset, int length, @Nullable Runnable releaser) {
        super(array.length);
        this.alloc = alloc;
        this.array = array;
        this.releaser = releaser;
        setIndex(offset, offset + length);
    }

    @Override
    public int refCnt() {
        return refCnt;
    }

    @Override
    public ByteBuf retain() {
        return retain0(1);
    }

    @Override
    public ByteBuf retain(int increment) {
        if (increment <= 0) {
            throw new IllegalArgumentException("increment: " + increment + " (expected: > 0)");
        }
        return retain0(increment);
    }

    private ByteBuf retain0(int increment) {
        int oldRef = REF_CNT_UPDATER.getAndAdd(this, increment);
        if (oldRef <= 0 || oldRef + increment < oldRef) {
            REF_CNT_UPDATER.getAndAdd(this, -increment);
            throw new IllegalReferenceCountException(oldRef, increment);
        }
        return this;
    }

    @Override
    public ByteBuf touch() {
        return this;
    }

    @Override
    public ByteBuf touch(Object hint) {
        return this;
    }

    @Override
    public boolean release() {
        return release0(1);
    }

    @Override
    public boolean release(int decrement) {
        if (decrement <= 0) {
            throw new IllegalArgumentException("decrement: " + decrement + " (expected: > 0)");
        }
        return release0(decrement);
    }

    private boolean release0(int decrement) {
        int oldRef = REF_CNT_UPDATER.getAndAdd(this, -decrement);
        if (oldRef == decrement) {
            if (releaser != null) {
                releaser.run();
            }
            return true;
        }
        if (oldRef < decrement || oldRef - decrement > oldRef) {
            REF_CNT_UPDATER.getAndAdd(this, decrement);
            throw new IllegalReferenceCountException(oldRef, -decrement);
        }
        return false;
    }

    @Override
    public ByteBufAllocator alloc() {
        return alloc;
    }

    @Override
    public ByteOrder order() {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    public boolean isDirect() {
        return false;
    }

    @Override
    public int capacity() {
        return array.length;
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasArray() {
        return true;
    }

    @Override
    public byte[] array() {
        return array;
    }

    @Override
    public int arrayOffset() {
        return 0;
    }

    @Override
    public boolean hasMemoryAddress() {
        return false;
    }

    @Override
    public long memoryAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        dst.setBytes(dstIndex, array, index, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        System.arraycopy(array, index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        dst.put(array, index, dst.remaining());
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        out.write(array, index, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return out.write(ByteBuffer.wrap(array, index, length));
    }

    @Override
    public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        return out.write(ByteBuffer.wrap(array, index, length), position);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int setBytes(int index, InputStream in, int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int setBytes(int index, FileChannel in, long position, int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int nioBufferCount() {
        return 1;
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return ByteBuffer.wrap(array, index, length).slice();
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        return ByteBuffer.wrap(array, index, length);
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return new ByteBuffer[] {nioBuffer(index, length)};
    }

    @Override
    public boolean isContiguous() {
        return true;
    }

    @Override
    public ByteBuf copy(int index, int length) {
        return alloc.heapBuffer(length, maxCapacity()).writeBytes(array, index, length);
    }

    @Override
    protected byte _getByte(int index) {
        return array[index];
    }

    @Override
    protected short _getShort(int index) {
        return (short) (array[index] << 8 | array[index + 1] & 0xff);
    }

    @Override
    protected short _getShortLE(int index) {
        return (short) (array[index] & 0xff | array[index + 1] << 8);
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return (array[index] & 0xff) << 16 | (array[index + 1] & 0xff) << 8 | array[index + 2] & 0xff;
    }

    @Override
    protected int _getUnsignedMediumLE(int index) {
        return array[index] & 0xff | (array[index + 1] & 0xff) << 8 | (array[index + 2] & 0xff) << 16;
    }

    @Override
    protected int _getInt(int index) {
        return (array[index] & 0xff) << 24 | (array[index + 1] & 0xff) << 16 | (array[index + 2] & 0xff) << 8 | array[index + 3] & 0xff;
    }

    @Override
    protected int _getIntLE(int index) {
        return array[index] & 0xff | (array[index + 1] & 0xff) << 8 | (array[index + 2] & 0xff) << 16 | (array[index + 3] & 0xff) << 24;
    }

    @Override
    protected long _getLong(int index) {
        return ((long) array[index] & 0xff) << 56 | ((long) array[index + 1] & 0xff) << 48

            | ((long) array[index + 2] & 0xff) << 40 | ((long) array[index + 3] & 0xff) << 32
            | ((long) array[index + 4] & 0xff) << 24 | ((long) array[index + 5] & 0xff) << 16
            | ((long) array[index + 6] & 0xff) << 8 | (long) array[index + 7] & 0xff;
    }

    @Override
    protected long _getLongLE(int index) {
        return (long) array[index] & 0xff | ((long) array[index + 1] & 0xff) << 8

            | ((long) array[index + 2] & 0xff) << 16 | ((long) array[index + 3] & 0xff) << 24
            | ((long) array[index + 4] & 0xff) << 32 | ((long) array[index + 5] & 0xff) << 40
            | ((long) array[index + 6] & 0xff) << 48 | ((long) array[index + 7] & 0xff) << 56;
    }

    @Override
    protected void _setByte(int index, int value) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void _setShort(int index, int value) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void _setShortLE(int index, int value) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void _setMedium(int index, int value) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void _setMediumLE(int index, int value) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void _setInt(int index, int value) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void _setIntLE(int index, int value) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void _setLong(int index, long value) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void _setLongLE(int index, long value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf unwrap() {
        return null;
    }
}
