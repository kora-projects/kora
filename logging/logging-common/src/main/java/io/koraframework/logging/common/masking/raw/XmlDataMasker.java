package io.koraframework.logging.common.masking.raw;

import io.koraframework.logging.common.masking.MaskingPathRules;
import io.koraframework.logging.common.masking.MaskingStrategy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Soft XML parser that masks element text and attribute values by path, see {@link MaskingPathRules}.
 * <p>
 * Elements and attributes both contribute a token to the path, so {@code password} masks the text of
 * {@code <password>secret</password>} as well as the value of {@code <user password="secret"/>}. Namespace prefixes are
 * ignored, {@code <ns:password>} is matched as {@code password}.
 * <p>
 * Like {@link JsonDataMasker}, XML structure is lost as a whole once it stops making sense, so the scanner fails
 * closed: an unterminated tag or a mismatched closing tag ends the walk and everything after it is replaced by
 * {@value #DAMAGED_SUFFIX}.
 * <p>
 * The encoding of the {@code <?xml?>} declaration is ignored, the payload is read in the encoding the transport
 * declared, since a declaration inside a damaged payload is not worth trusting.
 *
 * @see DataMasker
 */
public final class XmlDataMasker extends AbstractDataMasker {

    public static final String FORMAT = "xml";

    /** Written in place of everything the scanner could not interpret. */
    public static final String DAMAGED_SUFFIX = "<!--masked:unparseable-->";
    /** Written in place of everything past the length limit. */
    public static final String TRUNCATED_SUFFIX = "<!--masked:truncated-->";

    public static final int DEFAULT_MAX_DEPTH = 64;
    public static final int DEFAULT_MAX_LENGTH = 64 * 1024;

    private static final byte[] DAMAGED = DAMAGED_SUFFIX.getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TRUNCATED = TRUNCATED_SUFFIX.getBytes(StandardCharsets.US_ASCII);

    private static final byte[] COMMENT_START = "<!--".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] COMMENT_END = "-->".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CDATA_START = "<![CDATA[".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CDATA_END = "]]>".getBytes(StandardCharsets.US_ASCII);

    private final int maxDepth;

    public XmlDataMasker(MaskingPathRules rules) {
        this(rules, DEFAULT_MAX_DEPTH, DEFAULT_MAX_LENGTH);
    }

    public XmlDataMasker(MaskingPathRules rules, int maxDepth, int maxLength) {
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
            while (!this.stopped() && this.pos < this.src.length) {
                if (this.src[this.pos] == '<') {
                    this.markup();
                } else {
                    this.text();
                }
            }
        }

        private void markup() {
            if (this.startsWith(COMMENT_START)) {
                this.copyUntil(COMMENT_END);
            } else if (this.startsWith(CDATA_START)) {
                this.copyUntil(CDATA_END);
            } else if (this.pos + 1 < this.src.length
                && (this.src[this.pos + 1] == '?' || this.src[this.pos + 1] == '!')) {
                this.copyUntil(new byte[]{'>'});
            } else if (this.pos + 1 < this.src.length && this.src[this.pos + 1] == '/') {
                this.closeTag();
            } else {
                this.openTag();
            }
        }

        private void openTag() {
            var nameStart = this.pos + 1;
            var nameEnd = nameStart;
            while (nameEnd < this.src.length && isNameByte(this.src[nameEnd])) {
                nameEnd++;
            }
            if (nameEnd == nameStart || this.depth >= this.maxDepth) {
                this.damaged();
                return;
            }

            this.pathStarts[this.depth] = localStart(this.src, nameStart, nameEnd);
            this.pathEnds[this.depth] = nameEnd;
            this.depth++;
            this.out.write(this.src, this.pos, nameEnd);
            this.pos = nameEnd;

            var selfClosing = this.attributes();
            if (this.stopped()) {
                return;
            }
            if (selfClosing) {
                this.depth--;
                return;
            }

            var strategy = this.rules.strategy(this.src, this.pathStarts, this.pathEnds, this.depth);
            if (strategy != null) {
                this.maskElementBody(strategy);
            }
        }

        /**
         * @return whether the tag was self closing
         */
        private boolean attributes() {
            while (this.pos < this.src.length && !this.stopped()) {
                this.copyWhitespace();
                if (this.pos >= this.src.length) {
                    this.damaged();
                    return false;
                }

                var c = this.src[this.pos];
                if (c == '>') {
                    this.copy();
                    return false;
                }
                if (c == '/') {
                    if (this.pos + 1 < this.src.length && this.src[this.pos + 1] == '>') {
                        this.out.write(this.src, this.pos, this.pos + 2);
                        this.pos += 2;
                        return true;
                    }
                    this.damaged();
                    return false;
                }
                if (!isNameByte(c)) {
                    this.damaged();
                    return false;
                }

                var nameStart = this.pos;
                while (this.pos < this.src.length && isNameByte(this.src[this.pos])) {
                    this.pos++;
                }
                var nameEnd = this.pos;
                this.out.write(this.src, nameStart, nameEnd);

                this.copyWhitespace();
                if (this.pos >= this.src.length || this.src[this.pos] != '=') {
                    // an attribute without a value carries nothing to mask
                    continue;
                }
                this.copy();
                this.copyWhitespace();
                if (!this.attributeValue(localStart(this.src, nameStart, nameEnd), nameEnd)) {
                    return false;
                }
            }
            this.damaged();
            return false;
        }

        private boolean attributeValue(int nameStart, int nameEnd) {
            if (this.pos >= this.src.length) {
                this.damaged();
                return false;
            }
            var quote = this.src[this.pos];
            if (quote != '"' && quote != '\'') {
                this.damaged();
                return false;
            }
            var end = indexOf(this.src, quote, this.pos + 1);
            if (end < 0 || this.depth >= this.maxDepth) {
                this.damaged();
                return false;
            }

            this.pathStarts[this.depth] = nameStart;
            this.pathEnds[this.depth] = nameEnd;
            var strategy = this.rules.strategy(this.src, this.pathStarts, this.pathEnds, this.depth + 1);

            this.out.write(quote);
            if (strategy != null) {
                var value = new String(this.src, this.pos + 1, end - this.pos - 1, this.charset);
                this.out.write(AbstractDataMasker.replacement(strategy, value, this.charset));
            } else {
                this.out.write(this.src, this.pos + 1, end);
            }
            this.out.write(quote);
            this.pos = end + 1;
            return !this.stopped();
        }

        /**
         * Replaces everything up to the matching closing tag of the current element with the mask.
         */
        private void maskElementBody(MaskingStrategy strategy) {
            var nameStart = this.pathStarts[this.depth - 1];
            var nameEnd = this.pathEnds[this.depth - 1];
            var nesting = 1;
            var cursor = this.pos;
            while (cursor < this.src.length) {
                var next = indexOf(this.src, (byte) '<', cursor);
                if (next < 0) {
                    break;
                }
                if (startsWith(this.src, next, COMMENT_START)) {
                    var end = indexOf(this.src, next, COMMENT_END);
                    if (end < 0) {
                        break;
                    }
                    cursor = end + COMMENT_END.length;
                    continue;
                }
                if (startsWith(this.src, next, CDATA_START)) {
                    var end = indexOf(this.src, next, CDATA_END);
                    if (end < 0) {
                        break;
                    }
                    cursor = end + CDATA_END.length;
                    continue;
                }

                var tagEnd = indexOf(this.src, (byte) '>', next);
                if (tagEnd < 0) {
                    break;
                }
                var closing = next + 1 < this.src.length && this.src[next + 1] == '/';
                var tagNameStart = closing ? next + 2 : next + 1;
                var tagNameEnd = tagNameStart;
                while (tagNameEnd < tagEnd && isNameByte(this.src[tagNameEnd])) {
                    tagNameEnd++;
                }
                var sameName = sameName(this.src,
                    localStart(this.src, tagNameStart, tagNameEnd), tagNameEnd,
                    nameStart, nameEnd);

                if (closing) {
                    if (sameName && --nesting == 0) {
                        var value = new String(this.src, this.pos, next - this.pos, this.charset);
                        this.out.write(AbstractDataMasker.replacement(strategy, value, this.charset));
                        this.out.write(this.src, next, tagEnd + 1);
                        this.pos = tagEnd + 1;
                        this.depth--;
                        return;
                    }
                } else if (sameName && this.src[tagEnd - 1] != '/') {
                    nesting++;
                }
                cursor = tagEnd + 1;
            }
            this.damaged();
        }

        private void closeTag() {
            var end = indexOf(this.src, (byte) '>', this.pos);
            if (end < 0 || this.depth == 0) {
                this.damaged();
                return;
            }
            var nameStart = this.pos + 2;
            var nameEnd = nameStart;
            while (nameEnd < end && isNameByte(this.src[nameEnd])) {
                nameEnd++;
            }
            if (!sameName(this.src, localStart(this.src, nameStart, nameEnd), nameEnd,
                this.pathStarts[this.depth - 1], this.pathEnds[this.depth - 1])) {
                this.damaged();
                return;
            }
            this.depth--;
            this.out.write(this.src, this.pos, end + 1);
            this.pos = end + 1;
        }

        private void text() {
            var next = indexOf(this.src, (byte) '<', this.pos);
            var end = next < 0 ? this.src.length : next;
            this.out.write(this.src, this.pos, end);
            this.pos = end;
        }

        private void copyUntil(byte[] terminator) {
            var end = indexOf(this.src, this.pos, terminator);
            if (end < 0) {
                this.damaged();
                return;
            }
            var to = end + terminator.length;
            this.out.write(this.src, this.pos, to);
            this.pos = to;
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

        private boolean startsWith(byte[] prefix) {
            return startsWith(this.src, this.pos, prefix);
        }

        private boolean stopped() {
            return this.out.isStopped();
        }

        private void damaged() {
            this.out.stop(DAMAGED);
        }

        private static boolean sameName(byte[] src, int start, int end, int otherStart, int otherEnd) {
            if (end - start != otherEnd - otherStart) {
                return false;
            }
            for (int i = 0; i < end - start; i++) {
                if (toLowerAscii(src[start + i]) != toLowerAscii(src[otherStart + i])) {
                    return false;
                }
            }
            return true;
        }

        /**
         * @return index the local name starts at, skipping a namespace prefix if there is one
         */
        private static int localStart(byte[] src, int start, int end) {
            for (int i = end - 1; i >= start; i--) {
                if (src[i] == ':') {
                    return i + 1;
                }
            }
            return start;
        }

        private static boolean startsWith(byte[] src, int at, byte[] prefix) {
            if (at + prefix.length > src.length) {
                return false;
            }
            for (int i = 0; i < prefix.length; i++) {
                if (src[at + i] != prefix[i]) {
                    return false;
                }
            }
            return true;
        }

        private static int indexOf(byte[] src, byte value, int from) {
            for (int i = from; i < src.length; i++) {
                if (src[i] == value) {
                    return i;
                }
            }
            return -1;
        }

        private static int indexOf(byte[] src, int from, byte[] value) {
            for (int i = from; i + value.length <= src.length; i++) {
                if (startsWith(src, i, value)) {
                    return i;
                }
            }
            return -1;
        }

        private static byte toLowerAscii(byte b) {
            return b >= 'A' && b <= 'Z' ? (byte) (b + ('a' - 'A')) : b;
        }

        private static boolean isWhitespace(byte c) {
            return c == ' ' || c == '\t' || c == '\n' || c == '\r';
        }

        private static boolean isNameByte(byte c) {
            return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '_' || c == '-' || c == '.' || c == ':'
                || (c & 0xFF) >= 0x80;
        }
    }
}
