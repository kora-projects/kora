package ru.tinkoff.kora.avro.common;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

final class ByteBufferInputStream extends InputStream {

    /**
     * The input ByteBuffer that was provided.
     * The ByteBuffer should be supplied with position and limit correctly set as appropriate
     */
    private final ByteBuffer backendBuffer;

    public ByteBufferInputStream(ByteBuffer backendBuffer) {
        Objects.requireNonNull(backendBuffer, "Given backend buffer can not be null!");
        this.backendBuffer = backendBuffer;
        this.backendBuffer.mark(); // to prevent java.nio.InvalidMarkException on InputStream.reset() if mark had not been set
    }

    /**
     * Reads the next byte of data from this ByteBuffer. The value byte is returned as an int in the range 0-255.
     * If no byte is available because the end of the buffer has been reached, the value -1 is returned.
     *
     * @return the next byte of data, or -1 if the limit/end of the buffer has been reached.
     */
    public int read() {
        return backendBuffer.hasRemaining()
            ? (backendBuffer.get() & 0xff)
            : -1;
    }

    /**
     * Reads up to len bytes of data into an array of bytes from this ByteBuffer.
     * If the buffer has no remaining bytes, then -1 is returned to indicate end of file.
     * Otherwise, the number k of bytes read is equal to the smaller of len and buffer remaining.
     *
     * @param b   the buffer into which the data is read.
     * @param off the start offset in the destination array b
     * @param len the maximum number of bytes read.
     * @return the total number of bytes read into the buffer, or -1 if there is no more data because the limit/end of
     * the ByteBuffer has been reached.
     * @throws NullPointerException      If b is null.
     * @throws IndexOutOfBoundsException If off is negative, len is negative, or len is greater than b.length - off
     */
    public int read(byte b[], int off, int len) {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }

        if (!backendBuffer.hasRemaining()) {
            return -1;
        }

        int remaining = backendBuffer.remaining();
        if (len > remaining) {
            len = remaining;
        }

        if (len <= 0) {
            return 0;
        }
        backendBuffer.get(b, off, len);
        return len;
    }

    /**
     * Skips n bytes of input from this ByteBuffer. Fewer bytes might be skipped if the limit is reached.
     *
     * @param n the number of bytes to be skipped.
     * @return the actual number of bytes skipped.
     */
    public long skip(long n) {
        int skipAmount = (n < 0)
            ? 0
            : ((n > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) n);

        if (skipAmount > backendBuffer.remaining()) {
            skipAmount = backendBuffer.remaining();
        }

        int newPos = backendBuffer.position() + skipAmount;
        backendBuffer.position(newPos);
        return skipAmount;
    }

    /**
     * Returns remaining bytes available in this ByteBuffer
     *
     * @return the number of remaining bytes that can be read (or skipped over) from this ByteBuffer.
     */
    public int available() {
        return backendBuffer.remaining();
    }

    public boolean markSupported() {
        return true;
    }

    /**
     * Set the current marked position in the ByteBuffer.
     * <p> Note: The readAheadLimit for this class has no meaning.
     */
    public void mark(int readAheadLimit) {
        backendBuffer.mark();
    }

    /**
     * Resets the ByteBuffer to the marked position.
     */
    public void reset() {
        backendBuffer.reset();
    }

    /**
     * Closing a ByteBuffer has no effect.
     * The methods in this class can be called after the stream has been closed without generating an IOException.
     */
    public void close() {

    }
}
