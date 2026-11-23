package io.koraframework.logging.common.masking.raw;

import io.koraframework.logging.common.masking.MaskingFull;
import io.koraframework.logging.common.masking.MaskingPathRules;
import io.koraframework.logging.common.masking.MaskingStrategy;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Soft JSON parser that masks values by path, see {@link MaskingPathRules}.
 * <p>
 * The payload is never parsed into a document, it is walked once over its bytes and copied to the output as it goes,
 * so masking a payload that is not valid JSON never fails. Anything the scanner can not interpret, from an
 * unterminated string to content that is not JSON at all, ends the walk and is replaced by {@value #DAMAGED_SUFFIX},
 * so no byte of an unparseable payload reaches the log.
 * <p>
 * Field names with JSON escapes are decoded before being matched, since {@code "pass\u0077ord"} and
 * {@code "password"} are the same name and only one of them is in the rules. A name with a broken escape can not be
 * checked, so its value is masked.
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
    /** Applied to the value of a name that can not be decoded, so it can not be checked against the rules. */
    private static final MaskingStrategy UNDECODABLE_NAME = new MaskingFull();

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

        /** Where {@link #pathStarts} and {@link #pathEnds} point: {@link #src}, or {@link #names} once a name is escaped. */
        private byte[] pathSource;
        private byte[] names = new byte[0];
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
            this.pathSource = src;
        }

        private void run() {
            this.value();
            if (!this.stopped()) {
                this.copyWhitespace();
                if (this.pos < this.src.length) {
                    // trailing content past a complete value, treat it as damage rather than guessing
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
            switch (this.peek()) {
                case '{' -> this.object();
                case '[' -> this.array();
                case '"' -> this.copyString();
                case -1 -> this.damaged();
                default -> this.copyLiteral();
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
                var strategy = this.push(nameStart, nameEnd)
                    ? this.rules.strategy(this.pathSource, this.pathStarts, this.pathEnds, this.depth)
                    : UNDECODABLE_NAME;
                if (strategy != null) {
                    this.maskValue(strategy);
                } else {
                    this.value();
                }
                if (--this.depth == 0) {
                    this.pathSource = this.src;
                }
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

        /**
         * Puts a name onto the path. A name is matched where it lies, between its quotes, without ever being extracted,
         * unless it has escapes: then it is decoded, and the whole path is kept in {@link #names} until the walk
         * leaves the top level object.
         *
         * @return {@code false} when the name has a broken escape and can not be matched
         */
        private boolean push(int nameStart, int nameEnd) {
            var level = this.depth++;
            var escaped = indexOf(this.src, (byte) '\\', nameStart, nameEnd) >= 0;
            if (!escaped && this.pathSource == this.src) {
                this.pathStarts[level] = nameStart;
                this.pathEnds[level] = nameEnd;
                return true;
            }

            var name = escaped ? this.unescape(nameStart, nameEnd) : null;
            if (escaped && name == null) {
                return false;
            }
            if (this.pathSource == this.src) {
                for (int i = 0; i < level; i++) {
                    this.append(this.src, this.pathStarts[i], this.pathEnds[i], i);
                }
            }
            if (escaped) {
                this.append(name, 0, name.length, level);
            } else {
                this.append(this.src, nameStart, nameEnd, level);
            }
            this.pathSource = this.names;
            return true;
        }

        private void append(byte[] from, int start, int end, int level) {
            var at = level == 0 ? 0 : this.pathEnds[level - 1];
            var length = end - start;
            if (at + length > this.names.length) {
                this.names = Arrays.copyOf(this.names, Math.max(this.names.length * 2, at + length));
            }
            System.arraycopy(from, start, this.names, at, length);
            this.pathStarts[level] = at;
            this.pathEnds[level] = at + length;
        }

        /**
         * Decodes the JSON escapes of the name in {@code [start, end)}.
         *
         * @return the name in the payload encoding, or {@code null} when an escape is broken
         */
        private byte @Nullable [] unescape(int start, int end) {
            var name = new StringBuilder(end - start);
            var from = start;
            for (int i = start; i < end; i++) {
                if (this.src[i] != '\\') {
                    continue;
                }
                name.append(new String(this.src, from, i - from, this.charset));
                if (++i >= end) {
                    return null;
                }
                switch (this.src[i]) {
                    case '"', '\\', '/' -> name.append((char) this.src[i]);
                    case 'b' -> name.append('\b');
                    case 'f' -> name.append('\f');
                    case 'n' -> name.append('\n');
                    case 'r' -> name.append('\r');
                    case 't' -> name.append('\t');
                    case 'u' -> {
                        if (i + 4 >= end) {
                            return null;
                        }
                        var c = 0;
                        for (int k = 1; k <= 4; k++) {
                            var digit = Character.digit(this.src[i + k] & 0xFF, 16);
                            if (digit < 0) {
                                return null;
                            }
                            c = (c << 4) | digit;
                        }
                        name.append((char) c);
                        i += 4;
                    }
                    default -> {
                        return null;
                    }
                }
                from = i + 1;
            }
            name.append(new String(this.src, from, end - from, this.charset));
            return name.toString().getBytes(this.charset);
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
                // elements inherit the path of the array itself, so rules do not have to mention indexes
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

        /**
         * Skips the value at the current position and writes the mask in its place.
         */
        private void maskValue(MaskingStrategy strategy) {
            this.copyWhitespace();
            if (this.stopped()) {
                return;
            }
            var start = this.pos;
            var quoted = this.peek() == '"';
            var container = this.peek() == '{' || this.peek() == '[';
            if (!this.skipValue()) {
                this.damaged();
                return;
            }

            // a container has no scalar to hand to the strategy, and keeping a part of a whole object makes no sense
            var value = container
                ? null
                : new String(this.src, quoted ? start + 1 : start,
                    (quoted ? this.pos - 1 : this.pos) - (quoted ? start + 1 : start), this.charset);

            this.out.write((byte) '"');
            this.writeEscaped(AbstractDataMasker.replacement(strategy, value, this.charset));
            this.out.write((byte) '"');
        }

        private boolean skipValue() {
            return switch (this.peek()) {
                case '{' -> this.skipContainer((byte) '{', (byte) '}');
                case '[' -> this.skipContainer((byte) '[', (byte) ']');
                case '"' -> this.skipString();
                case -1 -> false;
                default -> this.skipLiteral();
            };
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
            this.pos++; // opening quote
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
            for (var b : value) {
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

        private static int indexOf(byte[] content, byte value, int from, int to) {
            for (int i = from; i < to; i++) {
                if (content[i] == value) {
                    return i;
                }
            }
            return -1;
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
