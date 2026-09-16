package io.koraframework.logging.common.masking.raw;

import io.koraframework.logging.common.masking.MaskingPathRules;
import io.koraframework.logging.common.masking.MaskingStrategy;

import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base of the soft parsers, holding what scanning bytes requires from every format: the limits, and the guarantee that
 * the payload can be walked as bytes at all.
 * <p>
 * Structural characters of every supported format are ASCII, and in an ASCII compatible encoding a byte below
 * {@code 0x80} always means that ASCII character and never a part of some other one, which is what makes a byte
 * scanner correct without decoding. That holds for UTF-8 and for every single byte encoding, but not for UTF-16 and
 * friends, where {@code {} is two bytes. Payloads in such an encoding are decoded once and re-encoded as
 * {@link #DEFAULT_CHARSET} before scanning, which is slower but stays correct.
 */
abstract class AbstractDataMasker implements DataMasker {

    /** Written when a strategy can not produce a replacement. */
    static final String FULL_MASK = "***";

    private static final String PROBE = "{}[]<>\"':,=&/";
    private static final Map<Charset, Boolean> ASCII_COMPATIBLE = new ConcurrentHashMap<>();

    protected final MaskingPathRules rules;
    protected final int maxLength;

    protected AbstractDataMasker(MaskingPathRules rules, int maxLength) {
        this.rules = rules;
        this.maxLength = maxLength;
    }

    @Override
    public final String mask(byte[] content, @Nullable Charset charset) {
        var actual = charset == null ? DEFAULT_CHARSET : charset;
        if (!isAsciiCompatible(actual)) {
            return this.mask(new String(content, actual).getBytes(DEFAULT_CHARSET), DEFAULT_CHARSET);
        }
        return this.mask0(content, actual, this.rules.encoded(actual));
    }

    /**
     * @param content payload to mask, guaranteed to be in an ASCII compatible encoding
     * @param rules   the configured rules, already encoded for {@code charset}
     */
    protected abstract String mask0(byte[] content, Charset charset, MaskingPathRules.Encoded rules);

    protected MaskedOutput output(byte[] content, byte[] truncatedSuffix) {
        return new MaskedOutput(content.length + 16, this.maxLength, truncatedSuffix);
    }

    /**
     * Applies a matched strategy, encoding its replacement back into the payload encoding.
     * <p>
     * A strategy comes from application code and a masker is never allowed to fail on a payload, so anything thrown
     * here falls back to full masking rather than propagating: losing the shape of a value is always better than
     * losing the record or leaking the value.
     *
     * @param value the value being replaced, or {@code null} for an object or an array masked as a whole, which has
     *              no scalar to hand over
     */
    static byte[] replacement(MaskingStrategy strategy, @Nullable String value, Charset charset) {
        try {
            var masked = strategy.mask(value);
            return masked == null ? FULL_MASK.getBytes(charset) : masked.getBytes(charset);
        } catch (RuntimeException e) {
            return FULL_MASK.getBytes(charset);
        }
    }

    private static boolean isAsciiCompatible(Charset charset) {
        return ASCII_COMPATIBLE.computeIfAbsent(charset, c -> {
            try {
                return Arrays.equals(PROBE.getBytes(c), PROBE.getBytes(DEFAULT_CHARSET));
            } catch (Exception e) {
                return false;
            }
        });
    }
}
