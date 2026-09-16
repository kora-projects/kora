package io.koraframework.logging.common.masking;

import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Masking rules that are not bound to a logged type, the matching engine behind {@link MaskingRules} and the rules a
 * {@link io.koraframework.logging.common.masking.raw.DataMasker} is configured with.
 * <p>
 * A rule targets either a field name or a path from the root of the payload:
 * <ul>
 *     <li>{@code password} masks every field named {@code password} wherever it appears;</li>
 *     <li>{@code user.password} masks only {@code password} reached through {@code user} from the root;</li>
 *     <li>{@code users.*.password} matches one dynamic segment with {@code *}.</li>
 * </ul>
 * Elements of an array or a collection do not add a segment of their own, so {@code items.password} matches
 * {@code {"items":[{"password":"secret"}]}}.
 */
public class MaskingPathRules {

    private final List<Rule> fields;
    private final List<Rule> paths;
    private final Map<Charset, Encoded> encoded = new ConcurrentHashMap<>();

    protected MaskingPathRules(List<Rule> fields, List<Rule> paths) {
        this.fields = List.copyOf(fields);
        this.paths = List.copyOf(paths);
    }

    public static Builder<?> builder() {
        return new Builder<>();
    }

    /**
     * Matching against names that have already been decoded, used when writing a logged object.
     *
     * @param path      segments from the root, the last one being the field being written
     * @param fieldName name of the field being written
     * @return strategy to replace the value with, or {@code null} when it must be written as is
     */
    @Nullable
    public MaskingStrategy strategy(List<String> path, String fieldName) {
        for (var rule : this.paths) {
            if (rule.matches(path)) {
                return rule.strategy();
            }
        }
        for (var rule : this.fields) {
            if (rule.matchesField(fieldName)) {
                return rule.strategy();
            }
        }
        return null;
    }

    /**
     * Encodes the rules once per charset, so that a masker walking raw bytes never has to decode a name to match it.
     */
    public Encoded encoded(Charset charset) {
        return this.encoded.computeIfAbsent(charset, c -> new Encoded(this.fields, this.paths, c));
    }

    protected List<Rule> fields() {
        return this.fields;
    }

    protected List<Rule> paths() {
        return this.paths;
    }

    /**
     * Rules encoded for a single charset, matched against ranges of a payload instead of extracted names.
     * <p>
     * Names are compared case insensitively for ASCII letters. A payload walked as bytes is untrusted and its field
     * names are whatever the sender chose, so {@code Password} must not slip past a rule spelled {@code password},
     * while case folding beyond ASCII would depend on an encoding a damaged payload may not even honour.
     */
    public static final class Encoded {

        private final byte[][] fieldNames;
        private final MaskingStrategy[] fieldStrategies;
        private final byte[][][] pathTokens;
        private final MaskingStrategy[] pathStrategies;

        private Encoded(List<Rule> fields, List<Rule> paths, Charset charset) {
            this.fieldNames = new byte[fields.size()][];
            this.fieldStrategies = new MaskingStrategy[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                this.fieldNames[i] = fields.get(i).path().getFirst().toLowerCase(Locale.ROOT).getBytes(charset);
                this.fieldStrategies[i] = fields.get(i).strategy();
            }

            this.pathTokens = new byte[paths.size()][][];
            this.pathStrategies = new MaskingStrategy[paths.size()];
            for (int i = 0; i < paths.size(); i++) {
                var segments = paths.get(i).path();
                var tokens = new byte[segments.size()][];
                for (int s = 0; s < segments.size(); s++) {
                    var segment = segments.get(s);
                    // a wildcard matches without comparing, and is kept as a null to say so
                    tokens[s] = "*".equals(segment) ? null : segment.toLowerCase(Locale.ROOT).getBytes(charset);
                }
                this.pathTokens[i] = tokens;
                this.pathStrategies[i] = paths.get(i).strategy();
            }
        }

        public boolean isEmpty() {
            return this.fieldNames.length == 0 && this.pathTokens.length == 0;
        }

        /**
         * @param source payload being walked
         * @param starts index of the first byte of every name in {@code source}
         * @param ends   index past the last byte of every name in {@code source}
         * @param depth  number of meaningful segments, the last one being the field being written
         * @return strategy to replace the value with, or {@code null} when it must be written as is
         */
        @Nullable
        public MaskingStrategy strategy(byte[] source, int[] starts, int[] ends, int depth) {
            if (depth <= 0) {
                return null;
            }
            for (int i = 0; i < this.pathTokens.length; i++) {
                if (matchesPath(this.pathTokens[i], source, starts, ends, depth)) {
                    return this.pathStrategies[i];
                }
            }
            for (int i = 0; i < this.fieldNames.length; i++) {
                if (regionMatches(source, starts[depth - 1], ends[depth - 1], this.fieldNames[i])) {
                    return this.fieldStrategies[i];
                }
            }
            return null;
        }

        private static boolean matchesPath(byte[][] tokens, byte[] source, int[] starts, int[] ends, int depth) {
            if (tokens.length != depth) {
                return false;
            }
            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i] != null && !regionMatches(source, starts[i], ends[i], tokens[i])) {
                    return false;
                }
            }
            return true;
        }

        private static boolean regionMatches(byte[] source, int start, int end, byte[] token) {
            if (end - start != token.length) {
                return false;
            }
            for (int i = 0; i < token.length; i++) {
                if (toLowerAscii(source[start + i]) != token[i]) {
                    return false;
                }
            }
            return true;
        }

        private static byte toLowerAscii(byte b) {
            return b >= 'A' && b <= 'Z' ? (byte) (b + ('a' - 'A')) : b;
        }
    }

    public static class Builder<B extends Builder<B>> {

        protected final List<Rule> fields = new ArrayList<>();
        protected final List<Rule> paths = new ArrayList<>();

        protected Builder() { }

        /**
         * Adds a masking rule for a field name or a dotted path.
         * <p>
         * A single segment such as {@code password} is matched by field name globally. A multi segment value such as
         * {@code user.password} is matched from the root of the payload. The {@code *} segment matches exactly one
         * segment, which is useful for map values whose field names are not known ahead of time.
         *
         * @param fieldOrPath field name or dotted path
         * @param strategy    strategy used to replace matched values
         */
        @SuppressWarnings("unchecked")
        public B mask(String fieldOrPath, MaskingStrategy strategy) {
            var segments = List.of(fieldOrPath.split("\\."));
            if (segments.size() == 1) {
                this.fields.add(new Rule(segments, strategy));
            } else {
                this.paths.add(new Rule(segments, strategy));
            }
            return (B) this;
        }

        public MaskingPathRules build() {
            return new MaskingPathRules(this.fields, this.paths);
        }
    }

    protected record Rule(List<String> path, MaskingStrategy strategy) {

        boolean matches(List<String> path) {
            if (this.path.size() != path.size()) {
                return false;
            }
            for (int i = 0; i < this.path.size(); i++) {
                var segment = this.path.get(i);
                if (!segment.equals("*") && !segment.equals(path.get(i))) {
                    return false;
                }
            }
            return true;
        }

        boolean matchesField(String field) {
            return this.path.size() == 1 && this.path.getFirst().equals(field);
        }
    }
}
