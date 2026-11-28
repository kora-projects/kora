package io.koraframework.logging.common.masking.raw;

import io.koraframework.logging.common.masking.*;

import java.nio.charset.*;

/**
 * Soft JSON parser that masks values by path, see {@link MaskingPathRules}.
 * <p>
 * The payload is never parsed into a document, it is walked once over its bytes and copied to the output as it goes,
 * so masking a payload that is not valid JSON never fails. Anything the scanner can not interpret, from an
 * unterminated string to content that is not JSON at all, ends the walk and is replaced by {@value #DAMAGED_SUFFIX},
 * so no byte of an unparseable payload reaches the log.
 *
 * @see DataMasker
 */
public final class JsonDataMasker extends AbstractDataMasker {

    public static final String FORMAT = "json";

    /** Written in place of everything the scanner could not interpret. */
    public static final String DAMAGED_SUFFIX = "<masked:unparseable>";
    /** Written in place of everything past the length limit. */
    public static final String TRUNCATED_SUFFIX = "<masked:truncated>";

    public static final int DEFAULT_MAX_DEPTH = 64;
    public static final int DEFAULT_MAX_LENGTH = 64 * 1024;

    private static final byte[] DAMAGED = DAMAGED_SUFFIX.getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TRUNCATED = TRUNCATED_SUFFIX.getBytes(StandardCharsets.US_ASCII);

    private final int maxDepth;

    public JsonDataMasker(MaskingPathRules rules) {
        this(rules, DEFAULT_MAX_DEPTH, DEFAULT_MAX_LENGTH);
    }

    public JsonDataMasker(MaskingPathRules rules, int maxDepth, int maxLength) {
        super(rules, maxLength);
        this.maxDepth = maxDepth;
    }

    @Override
    public String format() {
        return FORMAT;
    }

    @Override
    protected String mask0(byte[] content, Charset charset, MaskingPathRules.Encoded rules) {
        var out = this.output(content, TRUNCATED);
        new Scanner(content, charset, rules, this.maxDepth, out).run();
        return out.toString(charset);
    }

    private static final class Scanner {

        private final byte[] src;
        private final Charset charset;
        private final MaskingPathRules.Encoded rules;
        private final int maxDepth;
        private final MaskedOutput out;
        private final int[] pathStarts;
        private final int[] pathEnds;

        private int pos;
        private int depth;

        private Scanner(byte[] src, Charset charset, MaskingPathRules.Encoded rules, int maxDepth, MaskedOutput out) {
            this.src = src;
            this.charset = charset;
            this.rules = rules;
            this.maxDepth = maxDepth;
            this.out = out;
            this.pathStarts = new int[maxDepth];
            this.pathEnds = new int[maxDepth];
        }

        private void run() {
            this.value();
            if (!this.stopped()) {
                this.copyWhitespace();
                if (this.pos < this.src.length) {
                    this.damaged();
                }
            }
        }

        private void value() {
            if (this.stopped()) {
                return;
            }
            this.copyWhitespace();
            if (this.stopped()) {
                return;
            }
            int peekValue = this.peek();
            if (peekValue == '{') {
                this.object();
            } else if (peekValue == '[') {
                this.array();
            } else if (peekValue == '"') {
                this.copyString();
            } else if (peekValue == -1) {
                this.damaged();
            } else {
                this.copyLiteral();
            }
        }

        private void object() {
            this.copy();
            this.copyWhitespace();
            if (this.stopped()) {
                return;
            }
            if (this.peek() == '}') {
                this.copy();
                return;
            }

            while (!this.stopped()) {
                this.copyWhitespace();
                if (this.stopped()) {
                    return;
                }
                if (this.peek() != '"') {
                    this.damaged();
                    return;
                }

                var keyStart = this.pos;
                this.copyString();
                if (this.stopped()) {
                    return;
                }
                var nameStart = keyStart + 1;
                var nameEnd = this.pos - 1;

                this.copyWhitespace();
                if (this.stopped()) {
                    return;
                }
                if (this.peek() != ':') {
                    this.damaged();
                    return;
                }
                this.copy();

                if (this.depth >= this.maxDepth) {
                    this.damaged();
                    return;
                }
                this.pathStarts[this.depth] = nameStart;
                this.pathEnds[this.depth] = nameEnd;
                this.depth++;
                var strategy = this.rules.strategy(this.src, this.pathStarts, this.pathEnds, this.depth);
                if (strategy != null) {
                    this.maskValue(strategy);
                } else {
                    this.value();
                }
                this.depth--;
                if (this.stopped()) {
                    return;
                }

                this.copyWhitespace();
                if (this.stopped()) {
                    return;
                }
                var next = this.peek();
                if (next == ',') {
                    this.copy();
                } else if (next == '}') {
                    this.copy();
                    return;
                } else {
                    this.damaged();
                    return;
                }
            }
        }

        private void array() {
            this.copy();
            this.copyWhitespace();
            if (this.stopped()) {
                return;
            }
            if (this.peek() == ']') {
                this.copy();
                return;
            }

            while (!this.stopped()) {
                this.value();
                if (this.stopped()) {
                    return;
                }
                this.copyWhitespace();
                if (this.stopped()) {
                    return;
                }
                var next = this.peek();
                if (next == ',') {
                    this.copy();
                } else if (next == ']') {
                    this.copy();
                    return;
                } else {
                    this.damaged();
                    return;
                }
            }
        }

        private void maskValue(MaskingStrategy strategy) {
            this.copyWhitespace();
            if (this.stopped()) {
                return;
            }
            var start = this.pos;
            var peekValue = this.peek();
            var quoted = peekValue == '"';
            var container = peekValue == '{' || peekValue == '[';
            if (!this.skipValue()) {
                this.damaged();
                return;
            }

            var value = container
                ? null
                : new String(this.src, quoted ? start + 1 : start,
                (quoted ? this.pos - 1 : this.pos) - (quoted ? start + 1 : start), this.charset);

            this.out.write((byte) '"');
            this.writeEscaped(AbstractDataMasker.replacement(strategy, value, this.charset));
            this.out.write((byte) '"');
        }

        private boolean skipValue() {
            int peekValue = this.peek();
            if (peekValue == '{') {
                return this.skipContainer((byte) '{', (byte) '}');
            } else if (peekValue == '[') {
                return this.skipContainer((byte) '[', (byte) ']');
            } else if (peekValue == '"') {
                return this.skipString();
            } else if (peekValue == -1) {
                return false;
            } else {
                return this.skipLiteral();
            }
        }

        private boolean skipContainer(byte open, byte close) {
            var nesting = 0;
            var guard = 0;
            while (this.pos < this.src.length) {
                var c = this.src[this.pos];
                if (c == '"') {
                    if (!this.skipString()) {
                        return false;
                    }
                    continue;
                }
                this.pos++;
                if (c == open) {
                    nesting++;
                    if (++guard > this.maxDepth) {
                        return false;
                    }
                } else if (c == close) {
                    if (--nesting == 0) {
                        return true;
                    }
                    if (nesting < 0) {
                        return false;
                    }
                }
            }
            return false;
        }

        private boolean skipString() {
            this.pos++;
            while (this.pos < this.src.length) {
                var c = this.src[this.pos++];
                if (c == '\\') {
                    if (this.pos >= this.src.length) {
                        return false;
                    }
                    this.pos++;
                } else if (c == '"') {
                    return true;
                }
            }
            return false;
        }

        private boolean skipLiteral() {
            var start = this.pos;
            while (this.pos < this.src.length && isLiteral(this.src[this.pos])) {
                this.pos++;
            }
            return this.pos > start;
        }

        private void copyString() {
            var start = this.pos;
            if (!this.skipString()) {
                this.damaged();
                return;
            }
            this.out.write(this.src, start, this.pos);
        }

        private void copyLiteral() {
            var start = this.pos;
            if (!this.skipLiteral()) {
                this.damaged();
                return;
            }
            this.out.write(this.src, start, this.pos);
        }

        private void copyWhitespace() {
            var start = this.pos;
            while (this.pos < this.src.length && isWhitespace(this.src[this.pos])) {
                this.pos++;
            }
            this.out.write(this.src, start, this.pos);
        }

        private void copy() {
            this.out.write(this.src[this.pos++]);
        }

        private void writeEscaped(byte[] value) {
            for (int i = 0; i < value.length; i++) {
                byte b = value[i];
                if (b == '"' || b == '\\') {
                    this.out.write((byte) '\\');
                }
                this.out.write(b);
            }
        }

        private int peek() {
            return this.pos < this.src.length ? this.src[this.pos] & 0xFF : -1;
        }

        private boolean stopped() {
            return this.out.isStopped();
        }

        private void damaged() {
            this.out.stop(DAMAGED);
        }

        private static boolean isWhitespace(byte c) {
            return c == ' ' || c == '\t' || c == '\n' || c == '\r';
        }

        private static boolean isLiteral(byte c) {
            return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '-' || c == '+' || c == '.';
        }
    }
}
