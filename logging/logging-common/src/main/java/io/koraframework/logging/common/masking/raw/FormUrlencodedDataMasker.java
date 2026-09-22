package io.koraframework.logging.common.masking.raw;

import io.koraframework.logging.common.masking.MaskingPathRules;
import io.koraframework.logging.common.masking.MaskingStrategy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Soft {@code application/x-www-form-urlencoded} parser that masks values by field name, see {@link MaskingPathRules}.
 * <p>
 * The format is flat and its separators are unambiguous, so damage stays local: a parameter whose name can not be
 * decoded has its value masked, and scanning resumes at the next {@code &} instead of giving up on the rest of the
 * payload the way {@link JsonDataMasker} has to.
 * <p>
 * Names are percent decoded into a small reusable buffer before being matched, since {@code pass%77ord} and
 * {@code password} are the same name and only one of them is in the rules.
 *
 * @see DataMasker
 */
public final class FormUrlencodedDataMasker extends AbstractDataMasker {

    public static final String FORMAT = "form-urlencoded";

    /** Written in place of everything past the length limit. */
    public static final String TRUNCATED_SUFFIX = "<masked:truncated>";

    public static final int DEFAULT_MAX_LENGTH = 64 * 1024;
    /** Longest parameter name that is decoded to be matched, a longer one is masked instead. */
    public static final int MAX_NAME_LENGTH = 256;

    private static final byte[] TRUNCATED = TRUNCATED_SUFFIX.getBytes(StandardCharsets.US_ASCII);

    public FormUrlencodedDataMasker(MaskingPathRules rules) {
        this(rules, DEFAULT_MAX_LENGTH);
    }

    public FormUrlencodedDataMasker(MaskingPathRules rules, int maxLength) {
        super(rules, maxLength);
    }

    @Override
    public String format() {
        return FORMAT;
    }

    @Override
    protected String mask0(byte[] content, Charset charset, MaskingPathRules.Encoded rules) {
        var out = this.output(content, TRUNCATED);
        var name = new byte[MAX_NAME_LENGTH];
        var starts = new int[]{0};
        var ends = new int[1];

        var from = 0;
        while (from <= content.length && !out.isStopped()) {
            var separator = indexOf(content, (byte) '&', from);
            var to = separator < 0 ? content.length : separator;
            maskParameter(content, from, to, charset, rules, name, starts, ends, out);

            if (separator < 0) {
                break;
            }
            out.write((byte) '&');
            from = separator + 1;
        }
        return out.toString(charset);
    }

    private static void maskParameter(byte[] content,
                                      int from,
                                      int to,
                                      Charset charset,
                                      MaskingPathRules.Encoded rules,
                                      byte[] name,
                                      int[] starts,
                                      int[] ends,
                                      MaskedOutput out) {
        var equals = indexOf(content, (byte) '=', from);
        if (equals < 0 || equals >= to) {
            // a parameter without a value carries nothing to mask
            out.write(content, from, to);
            return;
        }

        out.write(content, from, equals + 1);

        var length = decode(content, from, equals, name);
        if (length < 0) {
            // the name is undecodable or absurdly long, so it can not be checked and the value is masked
            out.write(AbstractDataMasker.FULL_MASK.getBytes(charset));
            return;
        }

        ends[0] = length;
        var strategy = rules.strategy(name, starts, ends, 1);
        if (strategy == null) {
            out.write(content, equals + 1, to);
            return;
        }

        var value = new byte[to - equals - 1];
        var valueLength = decode(content, equals + 1, to, value);
        var decoded = valueLength < 0
            ? new String(content, equals + 1, to - equals - 1, charset)
            : new String(value, 0, valueLength, charset);
        out.write(AbstractDataMasker.replacement(strategy, decoded, charset));
    }

    /**
     * Percent decodes {@code [from, to)} into {@code name}.
     *
     * @return length written, or {@code -1} when the name can not be decoded or does not fit
     */
    private static int decode(byte[] content, int from, int to, byte[] name) {
        var length = 0;
        for (int i = from; i < to; i++) {
            if (length == name.length) {
                return -1;
            }
            var c = content[i];
            if (c == '+') {
                name[length++] = ' ';
            } else if (c == '%') {
                if (i + 2 >= to) {
                    return -1;
                }
                var high = hex(content[i + 1]);
                var low = hex(content[i + 2]);
                if (high < 0 || low < 0) {
                    return -1;
                }
                name[length++] = (byte) ((high << 4) | low);
                i += 2;
            } else {
                name[length++] = c;
            }
        }
        return length;
    }

    private static int hex(byte c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    private static int indexOf(byte[] content, byte value, int from) {
        for (int i = from; i < content.length; i++) {
            if (content[i] == value) {
                return i;
            }
        }
        return -1;
    }
}
