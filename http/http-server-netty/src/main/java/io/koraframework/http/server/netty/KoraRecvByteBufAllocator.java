package io.koraframework.http.server.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.util.UncheckedBooleanSupplier;

public final class KoraRecvByteBufAllocator implements RecvByteBufAllocator {

    private final int minimum;
    private final int initial;
    private final int maximum;
    private final int maxMessagesPerRead;

    public KoraRecvByteBufAllocator(int minimum, int initial, int maximum, int maxMessagesPerRead) {
        if (minimum <= 0 || initial < minimum || maximum < initial || maxMessagesPerRead <= 0) {
            throw new IllegalArgumentException("Invalid recv buffer parameters");
        }
        this.minimum = minimum;
        this.initial = initial;
        this.maximum = maximum;
        this.maxMessagesPerRead = maxMessagesPerRead;
    }

    @SuppressWarnings("deprecation")
    @Override
    public Handle newHandle() {
        return new FastHandle(minimum, initial, maximum, maxMessagesPerRead);
    }

    private static final class FastHandle implements ExtendedHandle {
        private final int minimum;
        private final int maximum;
        private final int maxMessagesPerRead;

        private ChannelConfig config;
        private int nextSize;
        private int totalMessages;
        private int totalBytesRead;
        private int attemptedBytesRead;
        private int lastBytesRead;
        private boolean decreaseNow;

        private FastHandle(int minimum, int initial, int maximum, int maxMessagesPerRead) {
            this.minimum = minimum;
            this.maximum = maximum;
            this.maxMessagesPerRead = maxMessagesPerRead;
            this.nextSize = initial;
        }

        @Override
        public void reset(ChannelConfig config) {
            this.config = config;
            this.totalMessages = 0;
            this.totalBytesRead = 0;
        }

        @Override
        public int guess() {
            return nextSize;
        }

        @Override
        public ByteBuf allocate(ByteBufAllocator alloc) {
            return alloc.ioBuffer(nextSize);
        }

        @Override
        public void attemptedBytesRead(int bytes) {
            this.attemptedBytesRead = bytes;
        }

        @Override
        public int attemptedBytesRead() {
            return attemptedBytesRead;
        }

        @Override
        public void lastBytesRead(int bytes) {
            this.lastBytesRead = bytes;
            if (bytes > 0) {
                this.totalBytesRead += bytes;
                if (bytes == attemptedBytesRead) {
                    nextSize = Math.min(nextSize << 2, maximum);
                    decreaseNow = false;
                }
            }
        }

        @Override
        public int lastBytesRead() {
            return lastBytesRead;
        }

        @Override
        public void incMessagesRead(int amt) {
            this.totalMessages += amt;
        }

        @Override
        public boolean continueReading() {
            return config.isAutoRead()
                && attemptedBytesRead == lastBytesRead
                && totalMessages < maxMessagesPerRead
                && totalBytesRead > 0;
        }

        @Override
        public boolean continueReading(UncheckedBooleanSupplier maybeMoreDataSupplier) {
            return config.isAutoRead()
                && maybeMoreDataSupplier.get()
                && totalMessages < maxMessagesPerRead
                && totalBytesRead > 0;
        }

        @Override
        public void readComplete() {
            if (totalBytesRead > 0 && totalBytesRead <= (nextSize >>> 1)) {
                if (decreaseNow) {
                    nextSize = Math.max(nextSize >>> 1, minimum);
                    decreaseNow = false;
                } else {
                    decreaseNow = true;
                }
            } else {
                decreaseNow = false;
            }
        }
    }
}
