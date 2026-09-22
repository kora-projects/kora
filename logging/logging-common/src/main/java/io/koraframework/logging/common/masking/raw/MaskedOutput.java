package io.koraframework.logging.common.masking.raw;

import io.koraframework.logging.common.masking.MaskingPathRules;
import io.koraframework.logging.common.masking.MaskingStrategy;

import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Growable byte output every {@link DataMasker} writes into, holding the length limit and the fail closed state so
 * that the scanners do not each reimplement them.
 * <p>
 * Writes never grow the buffer past the limit: what does not fit is dropped rather than buffered and trimmed later, so
 * a huge payload costs the limit instead of its own size. Once stopped, every further write is dropped and the suffix
 * explaining why is appended on {@link #toString(Charset)}, which is what makes a scanner that gave up emit a masked
 * tail instead of raw payload.
 */
final class MaskedOutput {

    private final int maxLength;
    private final byte[] truncatedSuffix;

    private byte[] buffer;
    private int length;
    private boolean stopped;
    private byte[] suffix = new byte[0];

    MaskedOutput(int initialCapacity, int maxLength, byte[] truncatedSuffix) {
        this.maxLength = maxLength;
        this.truncatedSuffix = truncatedSuffix;
        // never larger than the limit, so a huge payload does not get buffered just to be trimmed away
        this.buffer = new byte[Math.max(1, Math.min(Math.max(initialCapacity, 32), maxLength + 1))];
    }

    void write(byte b) {
        if (this.stopped) {
            return;
        }
        if (this.length >= this.maxLength) {
            this.stop(this.truncatedSuffix);
            return;
        }
        this.ensure(1);
        this.buffer[this.length++] = b;
    }

    void write(byte[] source, int from, int to) {
        if (this.stopped || to <= from) {
            return;
        }
        var size = Math.min(to - from, this.maxLength - this.length);
        if (size > 0) {
            this.ensure(size);
            System.arraycopy(source, from, this.buffer, this.length, size);
            this.length += size;
        }
        if (size < to - from) {
            this.stop(this.truncatedSuffix);
        }
    }

    void write(byte[] source) {
        this.write(source, 0, source.length);
    }

    boolean isStopped() {
        return this.stopped;
    }

    /**
     * Stops the output and remembers why, the first reason winning.
     */
    void stop(byte[] suffix) {
        if (!this.stopped) {
            this.stopped = true;
            this.suffix = suffix;
        }
    }

    String toString(Charset charset) {
        if (this.suffix.length == 0) {
            return new String(this.buffer, 0, this.length, charset);
        }
        var result = new byte[this.length + this.suffix.length];
        System.arraycopy(this.buffer, 0, result, 0, this.length);
        System.arraycopy(this.suffix, 0, result, this.length, this.suffix.length);
        return new String(result, charset);
    }

    private void ensure(int size) {
        if (this.length + size > this.buffer.length) {
            var capacity = Math.max(this.buffer.length * 2, this.length + size);
            this.buffer = Arrays.copyOf(this.buffer, Math.min(capacity, this.maxLength + 1));
        }
    }
}
