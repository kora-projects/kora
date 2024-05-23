package ru.tinkoff.kora.s3.client.model;


import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

final class InputStreamByteBufferSubscriber extends InputStream implements Flow.Subscriber<ByteBuffer> {

    final static int MAX_BUFFERS_IN_QUEUE = 1;  // lock-step with the producer

    // An immutable ByteBuffer sentinel to mark that the last byte was received.
    private static final ByteBuffer LAST_BUFFER = ByteBuffer.wrap(new byte[0]);

    // A queue of yet unprocessed ByteBuffers received from the flow API.
    private final BlockingQueue<ByteBuffer> buffers;
    private volatile Flow.Subscription subscription;
    private volatile boolean closed;
    private volatile Throwable failed;
    private volatile ByteBuffer currentBuffer;
    private final AtomicBoolean subscribed = new AtomicBoolean();

    public InputStreamByteBufferSubscriber() {
        this(MAX_BUFFERS_IN_QUEUE);
    }

    InputStreamByteBufferSubscriber(int maxBuffers) {
        int capacity = (maxBuffers <= 0 ? MAX_BUFFERS_IN_QUEUE : maxBuffers);
        // 1 additional slot needed for LAST_LIST added by onComplete
        this.buffers = new ArrayBlockingQueue<>(capacity + 1);
    }

    // Returns the current byte buffer to read from.
    // If the current buffer has no remaining data, this method will take the
    // next buffer from the buffers queue, possibly blocking until
    // a new buffer is made available through the Flow API, or the
    // end of the flow has been reached.
    private ByteBuffer current() throws IOException {
        while (currentBuffer == null || !currentBuffer.hasRemaining()) {
            // Check whether the stream is closed or exhausted
            if (closed || failed != null) {
                throw new IOException("closed", failed);
            }
            if (currentBuffer == LAST_BUFFER) {
                break;
            }

            try {
                currentBuffer = buffers.take();

                // Check whether an exception was encountered upstream
                if (closed || failed != null) {
                    throw new IOException("closed", failed);
                }
                // Check whether we're done.
                if (currentBuffer == LAST_BUFFER) {
                    currentBuffer = LAST_BUFFER;
                    break;
                }

                // Request another upstream item ( list of buffers )
                Flow.Subscription s = subscription;
                if (s != null) {
                    s.request(1);
                }
            } catch (InterruptedException ex) {
                // continue
            }
        }

        assert currentBuffer == LAST_BUFFER || currentBuffer.hasRemaining();
        return currentBuffer;
    }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, bytes.length);
        if (len == 0) {
            return 0;
        }
        // get the buffer to read from, possibly blocking if
        // none is available
        ByteBuffer buffer;
        if ((buffer = current()) == LAST_BUFFER) return -1;

        // don't attempt to read more than what is available
        // in the current buffer.
        int read = Math.min(buffer.remaining(), len);
        assert read > 0 && read <= buffer.remaining();

        // buffer.get() will do the boundary check for us.
        buffer.get(bytes, off, read);
        return read;
    }

    @Override
    public int read() throws IOException {
        ByteBuffer buffer;
        if ((buffer = current()) == LAST_BUFFER) return -1;
        return buffer.get() & 0xFF;
    }

    @Override
    public int available() throws IOException {
        // best effort: returns the number of remaining bytes in
        // the current buffer if any, or 1 if the current buffer
        // is null or empty but the queue or current buffer list
        // are not empty. Returns 0 otherwise.
        if (closed) {
            return 0;
        }
        int available = 0;
        ByteBuffer current = currentBuffer;
        if (current == LAST_BUFFER) {
            return 0;
        }
        if (current != null) {
            available = current.remaining();
        }
        if (available != 0) {
            return available;
        }
        if (buffers.isEmpty()) {
            return 0;
        }
        return 1;
    }

    @Override
    public void onSubscribe(Flow.Subscription s) {
        Objects.requireNonNull(s);
        try {
            if (!subscribed.compareAndSet(false, true)) {
                s.cancel();
            } else {
                // check whether the stream is already closed.
                // if so, we should cancel the subscription
                // immediately.
                boolean closed;
                synchronized (this) {
                    closed = this.closed;
                    if (!closed) {
                        this.subscription = s;
                    }
                }
                if (closed) {
                    s.cancel();
                    return;
                }
                assert buffers.remainingCapacity() > 1; // should contain at least 2
                s.request(Math.max(1, buffers.remainingCapacity() - 1));
            }
        } catch (Throwable t) {
            failed = t;
            try {
                close();
            } catch (IOException x) {
                // OK
            } finally {
                onError(t);
            }
        }
    }

    @Override
    public void onNext(ByteBuffer t) {
        Objects.requireNonNull(t);
        try {
            if (!buffers.offer(t)) {
                throw new IllegalStateException("queue is full");
            }
        } catch (Throwable ex) {
            failed = ex;
            try {
                close();
            } catch (IOException ex1) {
                // OK
            } finally {
                onError(ex);
            }
        }
    }

    @Override
    public void onError(Throwable thrwbl) {
        subscription = null;
        failed = Objects.requireNonNull(thrwbl);
        // The client process that reads the input stream might
        // be blocked in queue.take().
        // Tries to offer LAST_LIST to the queue. If the queue is
        // full we don't care if we can't insert this buffer, as
        // the client can't be blocked in queue.take() in that case.
        // Adding LAST_LIST to the queue is harmless, as the client
        // should find failed != null before handling LAST_LIST.
        buffers.offer(LAST_BUFFER);
    }

    @Override
    public void onComplete() {
        subscription = null;
        onNext(LAST_BUFFER);
    }

    @Override
    public void close() throws IOException {
        Flow.Subscription s;
        synchronized (this) {
            if (closed) return;
            closed = true;
            s = subscription;
            subscription = null;
        }
        // s will be null if already completed
        try {
            if (s != null) {
                s.cancel();
            }
        } finally {
            buffers.offer(LAST_BUFFER);
            super.close();
        }
    }
}
