package io.koraframework.http.server.common.router;

import org.jspecify.annotations.Nullable;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Parsed path template used by {@link HybridPathTemplateMatcher}.
 *
 * <h2>Representation</h2>
 * <ol>
 *   <li>Delegate normalization, semantic ordering, base extraction, and direct suffix matching to
 *       {@link RadixPathTemplate}.</li>
 *   <li>Additionally parse the normalized path into literal, parameter, and wildcard segment
 *       descriptors. These descriptors are retained only so collision-heavy stem groups can be
 *       compiled into suffix decision tries.</li>
 *   <li>Record capture names in route order, including {@code *}, allowing a decision terminal to
 *       materialize primitive capture ranges without reparsing one- and two-parameter routes.</li>
 * </ol>
 *
 * <h2>Matching and allocation</h2>
 * <p>Single-route and explicitly linear buckets use the radix delegate's base-already-matched
 * methods. Decision buckets use segment metadata for structural selection, then this class creates
 * substrings and a compact one- or two-entry parameter map only for the winning route. Routes with
 * more than two captures fall back to the radix generic matcher after structural selection.</p>
 *
 * <h2>Wildcard grammar</h2>
 * <p>{@link RadixPathTemplate#create(String)} accepts at most one {@code *}, only inside the final
 * path segment. Decision segments preserve both literals around that wildcard. For example,
 * {@code /files/static-*.js} anchors {@code static-} at the start of the remaining path and
 * {@code .js} at the end, then captures the range between them. The suffix check is direct: it
 * neither scans wildcard positions nor backtracks, and the empty-suffix form keeps the existing
 * trailing-catch-all path. This independently parsed descriptor must stay aligned with the radix
 * delegate so direct, linear, and decision strategies remain semantically identical.</p>
 *
 * <h2>Ordering</h2>
 * <p>Comparison and equivalence delegate to {@link RadixPathTemplate}, keeping candidate priority
 * identical across the Radix and Hybrid implementations.</p>
 */
final class HybridPathTemplate implements Comparable<HybridPathTemplate> {

    private static final Segment[] EMPTY_SEGMENTS = new Segment[0];

    private final RadixPathTemplate delegate;
    private final boolean trailingSlash;
    private final String[] captureNames;
    private final Segment[] decisionSegments;

    private HybridPathTemplate(RadixPathTemplate delegate,
                               boolean trailingSlash,
                               String[] captureNames,
                               Segment[] decisionSegments) {
        this.delegate = delegate;
        this.trailingSlash = trailingSlash;
        this.captureNames = captureNames;
        this.decisionSegments = decisionSegments;
    }

    static HybridPathTemplate create(String inputPath) {
        var delegate = RadixPathTemplate.create(inputPath);
        var path = delegate.templateString();
        boolean trailingSlash = path.endsWith("/");
        var segments = parseDecisionSegments(path, trailingSlash);
        var captureNames = new ArrayList<String>(delegate.parameterCount());
        for (var segment : segments) {
            if (segment.kind == SegmentKind.PARAMETER) {
                captureNames.add(segment.value);
            } else if (segment.kind == SegmentKind.WILDCARD) {
                captureNames.add("*");
            }
        }
        return new HybridPathTemplate(delegate, trailingSlash, captureNames.toArray(String[]::new), segments);
    }

    private static Segment[] parseDecisionSegments(String path, boolean trailingSlash) {
        if (path.length() == 1) {
            return EMPTY_SEGMENTS;
        }

        int pathEnd = trailingSlash ? path.length() - 1 : path.length();
        var segments = new ArrayList<Segment>(4);
        int start = 1;
        while (start <= pathEnd) {
            int end = path.indexOf('/', start);
            if (end < 0 || end > pathEnd) {
                end = pathEnd;
            }
            var value = path.substring(start, end);
            if (value.length() >= 2 && value.charAt(0) == '{' && value.charAt(value.length() - 1) == '}') {
                segments.add(new Segment(
                    SegmentKind.PARAMETER, value.substring(1, value.length() - 1), null
                ));
            } else {
                int wildcard = value.indexOf('*');
                if (wildcard >= 0) {
                    segments.add(new Segment(
                        SegmentKind.WILDCARD,
                        value.substring(0, wildcard),
                        value.substring(wildcard + 1)
                    ));
                    break;
                }
                segments.add(new Segment(SegmentKind.LITERAL, value, null));
            }
            if (end == pathEnd) {
                break;
            }
            start = end + 1;
        }
        return segments.toArray(Segment[]::new);
    }

    @Nullable
    Map<String, String> matchSingleParameter(String path) {
        return this.delegate.matchSingleParameter(path, true);
    }

    @Nullable
    Map<String, String> matchTwoParameters(String path) {
        return this.delegate.matchTwoParameters(path, true);
    }

    boolean matches(String path, Map<String, String> pathParameters) {
        return this.delegate.matches(path, pathParameters, true);
    }

    @Nullable
    Map<String, String> materializeCaptured(String path, long first, long second, int captured) {
        if (captured != this.captureNames.length) {
            return null;
        }
        if (captured == 1) {
            return Map.of(this.captureNames[0], substring(path, first));
        }
        if (captured == 2) {
            if (this.captureNames[0].equals(this.captureNames[1])) {
                return Map.of(this.captureNames[1], substring(path, second));
            }
            return new TwoParameterMap(
                this.captureNames[0], substring(path, first),
                this.captureNames[1], substring(path, second)
            );
        }
        return null;
    }

    private static String substring(String path, long range) {
        return path.substring((int) (range >>> 32), (int) range);
    }

    String templateString() {
        return this.delegate.templateString();
    }

    String base() {
        return this.delegate.base();
    }

    Set<String> parameterNames() {
        return this.delegate.parameterNames();
    }

    boolean trailingSlash() {
        return this.trailingSlash;
    }

    @Nullable
    String wildcardPrefix() {
        return this.delegate.wildcardPrefix();
    }

    @Nullable
    String wildcardSuffix() {
        return this.delegate.wildcardSuffix();
    }

    int parameterCount() {
        return this.delegate.parameterCount();
    }

    boolean exact() {
        return this.delegate.exact();
    }

    Segment[] decisionSegments() {
        return this.decisionSegments;
    }

    @Override
    public int compareTo(HybridPathTemplate other) {
        return this.delegate.compareTo(other.delegate);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof HybridPathTemplate that && this.delegate.equals(that.delegate);
    }

    @Override
    public int hashCode() {
        return this.delegate.hashCode();
    }

    enum SegmentKind {
        LITERAL,
        PARAMETER,
        WILDCARD
    }

    record Segment(SegmentKind kind, String value, @Nullable String wildcardSuffix) {}

    private static final class TwoParameterMap extends AbstractMap<String, String> {
        private final String firstKey;
        private final String firstValue;
        private final String secondKey;
        private final String secondValue;

        private TwoParameterMap(String firstKey, String firstValue, String secondKey, String secondValue) {
            this.firstKey = firstKey;
            this.firstValue = firstValue;
            this.secondKey = secondKey;
            this.secondValue = secondValue;
        }

        @Override
        public String get(Object key) {
            if (this.firstKey.equals(key)) {
                return this.firstValue;
            }
            return this.secondKey.equals(key) ? this.secondValue : null;
        }

        @Override
        public boolean containsKey(Object key) {
            return this.firstKey.equals(key) || this.secondKey.equals(key);
        }

        @Override
        public int size() {
            return 2;
        }

        @Override
        public Set<Entry<String, String>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public Iterator<Entry<String, String>> iterator() {
                    return new Iterator<>() {
                        private int index;

                        @Override
                        public boolean hasNext() {
                            return this.index < 2;
                        }

                        @Override
                        public Entry<String, String> next() {
                            return switch (this.index++) {
                                case 0 -> Map.entry(firstKey, firstValue);
                                case 1 -> Map.entry(secondKey, secondValue);
                                default -> throw new NoSuchElementException();
                            };
                        }
                    };
                }

                @Override
                public int size() {
                    return 2;
                }
            };
        }
    }
}
