package io.koraframework.logging.common.masking.raw;

import io.koraframework.logging.common.masking.MaskingPathRules;
import io.koraframework.logging.common.masking.MaskingStrategy;

import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Masks sensitive values inside a payload that is about to be logged, without ever failing on it.
 * <p>
 * Implementations are soft parsers working on the raw bytes of a payload: they walk it once, copying it to the output
 * as they go, and are never allowed to throw, however damaged the input is. Nothing is decoded while scanning, and
 * only the masked result is turned into a {@link String}, so a payload far larger than the limit costs the limit
 * rather than its own size.
 * <p>
 * The payload is untrusted, so the contract is fail closed: as soon as an implementation loses track of the structure,
 * everything from that point on is masked instead of being written out. That makes an unparseable payload degrade
 * into a fully masked one rather than into a leak.
 * <p>
 * The output is meant for a log record and is not required to stay parseable, although masking a well formed payload
 * does produce a well formed one. Implementations are stateless and safe to share.
 * <p>
 * {@link #DEFAULT_CHARSET} is assumed everywhere an encoding is not known, which covers every transport carrying no
 * encoding of its own, a Kafka record value for one.
 *
 * @see MaskingPathRules
 */
public interface DataMasker {

    /**
     * Assumed wherever a payload comes without a declared encoding.
     */
    Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /**
     * @return name of the payload format this masker understands, for example {@code json}, lower case
     */
    String format();

    /**
     * Masks a payload read as {@link #DEFAULT_CHARSET}.
     *
     * @param content payload to mask, of any size and of any validity, never modified
     * @return masked payload, truncated when it exceeds the limit of the implementation
     */
    default String mask(byte[] content) {
        return this.mask(content, DEFAULT_CHARSET);
    }

    /**
     * Masks a payload in the encoding the transport declared, an HTTP {@code Content-Type} charset for one.
     *
     * @param content payload to mask, of any size and of any validity, never modified
     * @param charset encoding of the payload, {@link #DEFAULT_CHARSET} when it is not known
     * @return masked payload, truncated when it exceeds the limit of the implementation
     */
    String mask(byte[] content, @Nullable Charset charset);
}
