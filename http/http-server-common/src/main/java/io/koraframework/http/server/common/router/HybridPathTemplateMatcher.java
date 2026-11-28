package io.koraframework.http.server.common.router;

import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Hybrid matcher combining compressed radix stem lookup with per-stem decision compilation.
 *
 * <h2>Build algorithm</h2>
 * <ol>
 *   <li>A mutable {@link Builder} groups registrations by static stem and keeps candidates in
 *       semantic priority order without compiling lookup state after each registration.</li>
 *   <li>Store exact paths as cached match results.</li>
 *   <li>Compile dynamic stems into the same compressed, flat radix layout as
 *       {@link RadixPathTemplateMatcher}.</li>
 *   <li>Choose the terminal strategy from the number and shape of candidates:
 *     <ul>
 *       <li>one candidate: store its holder directly on the radix node, avoiding collection and
 *           strategy dispatch;</li>
 *       <li>fewer candidates than {@code decisionThreshold}, or a stem ending inside a segment:
 *           keep a priority-ordered linear bucket;</li>
 *       <li>otherwise: compile only the suffix after the already-matched stem into a segment
 *           decision trie.</li>
 *     </ul>
 *   </li>
 *   <li>{@link Builder#build()} publishes exact paths, radix arrays, and terminal strategies
 *       through one final {@code LookupState}; the resulting matcher is immutable.</li>
 * </ol>
 *
 * <h2>Lookup algorithm</h2>
 * <ol>
 *   <li>Check the exact map, then walk compressed radix edges to find the longest matching stem.</li>
 *   <li>For a direct terminal, run its template matcher immediately.</li>
 *   <li>For a decision terminal, begin segment matching at {@code stem.length()}; the static prefix
 *       is never parsed again. Literal, parameter, and wildcard branches use route ranks and
 *       backtracking exactly like the full decision matcher. A wildcard branch anchors its
 *       literal prefix at the current position and its suffix at the request-path end. Large
 *       wildcard buckets use a reverse suffix trie instead of a linear candidate scan.</li>
 *   <li>If the selected terminal produces no match, follow the radix fallback link to the next
 *       shorter terminal and repeat.</li>
 *   <li>Carry up to two captures as primitive ranges and allocate their substrings and map only
 *       when the winning route is known.</li>
 * </ol>
 *
 * <h2>Why the strategy is detectable at build time</h2>
 * <p>The registration map is already keyed by trimmed static stem, so the candidate count is simply
 * the size of that stem group. No runtime sampling or request-dependent heuristic is needed.
 * The default threshold is two: a unique stem keeps the radix direct path, while any genuine
 * collision uses a suffix decision trie.</p>
 *
 * <h2>Performance characteristics</h2>
 * <p>Ordinary unique-stem routes retain radix behavior. Collision-heavy groups replace linear
 * candidate scans with work proportional mainly to suffix segment depth and fanout. Exact hits and
 * misses allocate nothing; successful matches allocate only the public result and final captures.
 * The builder compiles the complete route table once, and lookup needs neither synchronization nor
 * a volatile snapshot read.</p>
 *
 * @param <T> matched value type
 */
class HybridPathTemplateMatcher<T> {

    /**
     * Default number of candidates in one static-stem group at which the group is compiled into a
     * suffix decision trie.
     *
     * <p>The value is based on JMH crossover measurements rather than an assumed heuristic.
     * Benchmarks kept the total route count fixed and varied candidates per stem across
     * {@code 1, 2, 4, 8, 16, 32, 64, 128}. They covered early literal fanout, deeper parameter and
     * literal chains, wildcard suffixes, successful lookup of the last-priority candidate, and
     * complete misses. A direct holder was consistently best for a unique stem. Starting with two
     * candidates, the suffix decision trie was already at least competitive and usually faster;
     * the advantage increased rapidly with group size. It also avoided allocations caused by
     * partially matched candidates in deeper routes.</p>
     *
     * <p>Therefore {@code 2} expresses the measured policy directly: keep the zero-dispatch radix
     * fast path when the stem identifies exactly one route, and compile every genuine stem
     * collision into a decision trie. {@link #builder(int)} accepts an explicit threshold for
     * route sets, JVMs, or memory constraints with a different measured crossover.</p>
     */
    static final int DEFAULT_DECISION_THRESHOLD = 2;

    /**
     * Largest wildcard bucket kept as a priority-ordered linear scan inside a decision node.
     *
     * <p>Focused JMH measurements covered 1, 4, 16, and 64 suffix routes sharing one stem. Linear
     * matching remained cheaper for the small buckets, while reverse suffix lookup removed the
     * route-count-dependent cost at 16 and 64 routes. Eight is the conservative midpoint between
     * the measured 4-route linear case and 16-route trie win. The threshold affects build-time
     * representation only: both strategies preserve identical rank and capture semantics.</p>
     */
    private static final int WILDCARD_SUFFIX_TRIE_THRESHOLD = 8;

    private final LookupState<T> lookupState;

    private HybridPathTemplateMatcher(LookupState<T> lookupState) {
        this.lookupState = lookupState;
    }

    static <T> Builder<T> builder() {
        return new Builder<>(DEFAULT_DECISION_THRESHOLD);
    }

    static <T> Builder<T> builder(int decisionThreshold) {
        return new Builder<>(decisionThreshold);
    }

    record PathTemplateMatch<T>(String matchedTemplate, Map<String, String> parameters, T value) {}

    @Nullable
    PathTemplateMatch<T> match(String path) {
        var state = lookupState;
        var exact = state.exactPaths.get(path);
        if (exact != null) {
            return exact;
        }
        final String normalizedPath = path.isEmpty() ? "/" : path;
        if (normalizedPath != path) {
            exact = state.exactPaths.get(normalizedPath);
            if (exact != null) {
                return exact;
            }
        }

        var trie = state.prefixTrie;
        int node = 0;
        int pathIndex = 0;
        int candidate = -1;
        while (pathIndex < normalizedPath.length()) {
            int child = trie.child(node, normalizedPath.charAt(pathIndex));
            if (child < 0) {
                break;
            }
            int edgeLength = trie.edgeLength[child];
            if (edgeLength > normalizedPath.length() - pathIndex
                || edgeLength > 1 && !normalizedPath.regionMatches(
                    pathIndex + 1, trie.edgeSource[child], trie.edgeStart[child] + 1, edgeLength - 1
                )) {
                break;
            }
            pathIndex += edgeLength;
            node = child;
            if (trie.singles[node] != null || trie.matchers[node] != null) {
                candidate = node;
            }
        }

        while (candidate >= 0) {
            var single = trie.singles[candidate];
            var match = single == null
                ? trie.matchers[candidate].match(normalizedPath)
                : matchHolder(single, normalizedPath, null);
            if (match != null) {
                return match;
            }
            candidate = trie.fallback[candidate];
        }
        return null;
    }

    private static String trimBase(HybridPathTemplate template) {
        var wildcardPrefix = template.wildcardPrefix();
        return wildcardPrefix == null ? template.base() : wildcardPrefix;
    }

    private static <T> LookupState<T> buildLookupState(
        Map<String, Set<PathTemplateHolder<T>>> pathTemplateMap,
        int decisionThreshold
    ) {
        var exactPaths = new HashMap<String, PathTemplateMatch<T>>(pathTemplateMap.size());
        var prefixTrie = new PrefixRadixBuilder<T>();
        int singleStems = 0;
        int linearStems = 0;
        int decisionStems = 0;

        for (var entry : pathTemplateMap.entrySet()) {
            ArrayList<PathTemplateHolder<T>> dynamic = null;
            for (var holder : entry.getValue()) {
                if (holder.template.exact()) {
                    exactPaths.put(
                        holder.template.templateString(),
                        new PathTemplateMatch<>(holder.template.templateString(), Map.of(), holder.value)
                    );
                } else {
                    if (dynamic == null) {
                        dynamic = new ArrayList<>(entry.getValue().size());
                    }
                    dynamic.add(holder);
                }
            }
            if (dynamic == null) {
                continue;
            }

            var holders = List.copyOf(dynamic);
            if (holders.size() == 1) {
                prefixTrie.put(entry.getKey(), holders.get(0), null);
                singleStems++;
            } else if (holders.size() < decisionThreshold || !decisionCompatible(entry.getKey())) {
                prefixTrie.put(entry.getKey(), null, StemMatcher.linear(holders));
                linearStems++;
            } else {
                prefixTrie.put(entry.getKey(), null, StemMatcher.decision(entry.getKey(), holders));
                decisionStems++;
            }
        }

        return new LookupState<>(
            Map.copyOf(exactPaths), prefixTrie.build(), singleStems, linearStems, decisionStems
        );
    }

    private static boolean decisionCompatible(String stem) {
        return stem.equals("/") || stem.endsWith("/");
    }

    /**
     * Single-threaded mutable registration phase for producing immutable hybrid matchers.
     */
    static final class Builder<T> {
        private final int decisionThreshold;
        private final Map<String, Set<PathTemplateHolder<T>>> pathTemplateMap = new HashMap<>();

        private Builder(int decisionThreshold) {
            if (decisionThreshold < 2) {
                throw new IllegalArgumentException("decisionThreshold must be >= 2");
            }
            this.decisionThreshold = decisionThreshold;
        }

        Map.@Nullable Entry<HybridPathTemplate, T> add(HybridPathTemplate template, T value) {
            var base = trimBase(template);
            var values = pathTemplateMap.get(base);
            var newValues = values == null
                ? new TreeSet<PathTemplateHolder<T>>()
                : new TreeSet<>(values);
            var holder = new PathTemplateHolder<>(value, template);
            var equivalent = newValues.ceiling(holder);
            if (equivalent != null && equivalent.compareTo(holder) == 0) {
                return Map.entry(equivalent.template, equivalent.value);
            }
            newValues.add(holder);
            pathTemplateMap.put(base, newValues);
            return null;
        }

        Map.@Nullable Entry<HybridPathTemplate, T> add(String template, T value) {
            return add(HybridPathTemplate.create(template), value);
        }

        Builder<T> addAll(Builder<T> builder) {
            for (var entry : builder.pathTemplateMap.entrySet()) {
                for (var holder : entry.getValue()) {
                    add(holder.template, holder.value);
                }
            }
            return this;
        }

        Builder<T> remove(String template) {
            var pathTemplate = HybridPathTemplate.create(template);
            var base = trimBase(pathTemplate);
            var values = pathTemplateMap.get(base);
            if (values == null) {
                return this;
            }
            var newValues = new TreeSet<>(values);
            var removed = false;
            var it = newValues.iterator();
            while (it.hasNext()) {
                var holder = it.next();
                if (holder.template.templateString().equals(pathTemplate.templateString())) {
                    it.remove();
                    removed = true;
                }
            }
            if (!removed) {
                return this;
            }
            if (newValues.isEmpty()) {
                pathTemplateMap.remove(base);
            } else {
                pathTemplateMap.put(base, newValues);
            }
            return this;
        }

        @Nullable
        T get(String template) {
            var pathTemplate = HybridPathTemplate.create(template);
            var values = pathTemplateMap.get(trimBase(pathTemplate));
            if (values == null) {
                return null;
            }
            for (var holder : values) {
                if (holder.template.equals(pathTemplate)) {
                    return holder.value;
                }
            }
            return null;
        }

        Set<HybridPathTemplate> getPathTemplates() {
            var result = new HashSet<HybridPathTemplate>();
            for (var holders : pathTemplateMap.values()) {
                for (var holder : holders) {
                    result.add(holder.template);
                }
            }
            return result;
        }

        HybridPathTemplateMatcher<T> build() {
            return new HybridPathTemplateMatcher<>(
                buildLookupState(pathTemplateMap, decisionThreshold)
            );
        }
    }

    int singleStemCount() {
        return lookupState.singleStems;
    }

    int linearStemCount() {
        return lookupState.linearStems;
    }

    int decisionStemCount() {
        return lookupState.decisionStems;
    }

    @Nullable
    private static <T> PathTemplateMatch<T> matchHolder(PathTemplateHolder<T> holder,
                                                        String path,
                                                        @Nullable LinkedHashMap<String, String> reusable) {
        var count = holder.template.parameterCount();
        final Map<String, String> parameters;
        if (count == 1) {
            parameters = holder.template.matchSingleParameter(path);
        } else if (count == 2) {
            parameters = holder.template.matchTwoParameters(path);
        } else {
            var target = reusable == null ? new LinkedHashMap<String, String>(mapCapacity(count)) : reusable;
            if (!holder.template.matches(path, target)) {
                target.clear();
                return null;
            }
            parameters = target;
        }
        return parameters == null
            ? null
            : new PathTemplateMatch<>(holder.template.templateString(), parameters, holder.value);
    }

    private static int mapCapacity(int size) {
        return size < 3 ? size + 1 : (int) (size / 0.75f) + 1;
    }

    private record StemMatcher<T>(int strategy, @Nullable List<PathTemplateHolder<T>> linear, @Nullable DecisionStemMatcher<T> decision) {

        private static final int LINEAR = 0;
        private static final int DECISION = 1;

        private static <T> StemMatcher<T> linear(List<PathTemplateHolder<T>> holders) {
            return new StemMatcher<>(LINEAR, holders, null);
        }

        private static <T> StemMatcher<T> decision(String stem, List<PathTemplateHolder<T>> holders) {
            return new StemMatcher<>(DECISION, null, new DecisionStemMatcher<>(stem, holders));
        }

        @Nullable
        private PathTemplateMatch<T> match(String path) {
            if (strategy == DECISION && decision != null) {
                return decision.match(path);
            }

            if (linear != null) {
                LinkedHashMap<String, String> reusable = null;
                for (var holder : linear) {
                    if (holder.template.parameterCount() > 2 && reusable == null) {
                        reusable = new LinkedHashMap<>(mapCapacity(holder.template.parameterCount()));
                    }
                    var match = matchHolder(holder, path, reusable);
                    if (match != null) {
                        return match;
                    }
                }
            }
            return null;
        }
    }

    private static final class DecisionStemMatcher<T> {
        private final int startPosition;
        private final DecisionTrie<T> trie;

        private DecisionStemMatcher(String stem, List<PathTemplateHolder<T>> holders) {
            startPosition = stem.length();
            var completedSegments = 0;
            for (var i = 1; i < stem.length(); i++) {
                if (stem.charAt(i) == '/') {
                    completedSegments++;
                }
            }
            var builder = new DecisionTrieBuilder<T>(completedSegments);
            var rank = 0;
            for (var holder : holders) {
                builder.put(new DecisionRoute<>(holder, rank++));
            }
            trie = builder.build();
        }

        private PathTemplateMatch<T> match(String path) {
            return trie.match(path, startPosition);
        }
    }

    private record PathTemplateHolder<T>(T value, HybridPathTemplate template) implements Comparable<PathTemplateHolder<T>> {

        @Override
        public int compareTo(PathTemplateHolder<T> other) {
            return template.compareTo(other.template);
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof PathTemplateHolder<?> that && template.equals(that.template);
        }

        @Override
        public int hashCode() {
            return template.hashCode();
        }
    }

    private static final class DecisionRoute<T> {
        private final PathTemplateHolder<T> holder;
        private final int rank;
        private final @Nullable String wildcardLiteralPrefix;
        private final @Nullable String wildcardLiteralSuffix;

        private DecisionRoute(PathTemplateHolder<T> holder, int rank) {
            this.holder = holder;
            this.rank = rank;
            var segments = holder.template.decisionSegments();
            if (segments.length == 0) {
                wildcardLiteralPrefix = null;
                wildcardLiteralSuffix = null;
            } else {
                var segment = segments[segments.length - 1];
                wildcardLiteralPrefix = segment.kind() == HybridPathTemplate.SegmentKind.WILDCARD
                    ? holder.template.wildcardPrefix() == null ? segment.value() : ""
                    : null;
                wildcardLiteralSuffix = segment.kind() == HybridPathTemplate.SegmentKind.WILDCARD
                    ? segment.wildcardSuffix()
                    : null;
            }
        }

        private int wildcardLiteralPrefixLength() {
            return wildcardLiteralPrefix == null ? 0 : wildcardLiteralPrefix.length();
        }

        private boolean wildcardMatches(String path, int position) {
            var suffix = wildcardLiteralSuffix;
            var suffixLength = suffix == null ? 0 : suffix.length();
            var captureEnd = path.length() - suffixLength;
            return wildcardPrefixMatches(path, position, captureEnd)
                && (suffixLength == 0 || path.regionMatches(captureEnd, suffix, 0, suffixLength));
        }

        private boolean wildcardPrefixMatches(String path, int position, int captureEnd) {
            var prefix = wildcardLiteralPrefix;
            return prefix != null
                && captureEnd >= position + prefix.length()
                && path.regionMatches(position, prefix, 0, prefix.length());
        }

        private String wildcardLiteralSuffix() {
            return wildcardLiteralSuffix;
        }

        private int wildcardCaptureEnd(String path) {
            var suffix = wildcardLiteralSuffix;
            return path.length() - (suffix == null ? 0 : suffix.length());
        }
    }

    private record LookupState<T>(Map<String, PathTemplateMatch<T>> exactPaths,
                                  PrefixRadix<T> prefixTrie,
                                  int singleStems,
                                  int linearStems,
                                  int decisionStems) {}

    private record PrefixRadix<T>(String[] edgeSource, int[] edgeStart, int[] edgeLength, int[] childOffset, int[] childCount, char[] childChars, int[] childNodes, PathTemplateHolder<T>[] singles,
                                  StemMatcher<T>[] matchers, int[] fallback) {

        private int child(int node, char value) {
            var offset = childOffset[node];
            var count = childCount[node];
            if (count <= 4) {
                var limit = offset + count;
                for (var i = offset; i < limit; i++) {
                    if (childChars[i] == value) {
                        return childNodes[i];
                    }
                }
                return -1;
            }
            var low = offset;
            var high = offset + count - 1;
            while (low <= high) {
                var middle = (low + high) >>> 1;
                var current = childChars[middle];
                if (current < value) {
                    low = middle + 1;
                } else if (current > value) {
                    high = middle - 1;
                } else {
                    return childNodes[middle];
                }
            }
            return -1;
        }
    }

    private static final class PrefixRadixBuilder<T> {
        private final RadixBuilderNode<T> root = new RadixBuilderNode<>(null, 0, 0, null);

        private void put(String prefix,
                         @Nullable PathTemplateHolder<T> single,
                         @Nullable StemMatcher<T> matcher) {
            var node = root;
            var prefixIndex = 0;
            while (prefixIndex < prefix.length()) {
                var first = prefix.charAt(prefixIndex);
                var child = node.children.get(first);
                if (child == null) {
                    var leaf = new RadixBuilderNode<T>(prefix, prefixIndex, prefix.length(), node);
                    leaf.single = single;
                    leaf.matcher = matcher;
                    node.children.put(first, leaf);
                    return;
                }
                var common = commonPrefixLength(prefix, prefixIndex, child.edgeSource, child.edgeStart, child.edgeEnd);
                var childLength = child.edgeEnd - child.edgeStart;
                if (common == childLength) {
                    prefixIndex += common;
                    node = child;
                    continue;
                }
                var split = new RadixBuilderNode<T>(child.edgeSource, child.edgeStart, child.edgeStart + common, node);
                node.children.put(first, split);
                child.edgeStart += common;
                child.parent = split;
                split.children.put(child.edgeSource.charAt(child.edgeStart), child);
                prefixIndex += common;
                if (prefixIndex == prefix.length()) {
                    split.single = single;
                    split.matcher = matcher;
                } else {
                    var leaf = new RadixBuilderNode<T>(prefix, prefixIndex, prefix.length(), split);
                    leaf.single = single;
                    leaf.matcher = matcher;
                    split.children.put(prefix.charAt(prefixIndex), leaf);
                }
                return;
            }
            node.single = single;
            node.matcher = matcher;
        }

        private static int commonPrefixLength(String first,
                                              int firstStart,
                                              String second,
                                              int secondStart,
                                              int secondEnd) {
            var limit = Math.min(first.length() - firstStart, secondEnd - secondStart);
            var common = 0;
            while (common < limit && first.charAt(firstStart + common) == second.charAt(secondStart + common)) {
                common++;
            }
            return common;
        }

        private PrefixRadix<T> build() {
            var nodes = new ArrayList<RadixBuilderNode<T>>();
            root.id = 0;
            nodes.add(root);
            for (var i = 0; i < nodes.size(); i++) {
                for (var child : nodes.get(i).children.values()) {
                    child.id = nodes.size();
                    nodes.add(child);
                }
            }

            var size = nodes.size();
            var edgeSource = new String[size];
            var edgeStart = new int[size];
            var edgeLength = new int[size];
            var childOffset = new int[size];
            var childCount = new int[size];
            var childChars = new char[Math.max(0, size - 1)];
            var childNodes = new int[Math.max(0, size - 1)];
            @SuppressWarnings("unchecked")
            var singles = (PathTemplateHolder<T>[]) new PathTemplateHolder<?>[size];
            @SuppressWarnings("unchecked")
            var matchers = (StemMatcher<T>[]) new StemMatcher<?>[size];
            var fallback = new int[size];
            var childIndex = 0;
            for (var i = 0; i < size; i++) {
                var source = nodes.get(i);
                edgeSource[i] = source.edgeSource;
                edgeStart[i] = source.edgeStart;
                edgeLength[i] = source.edgeEnd - source.edgeStart;
                childOffset[i] = childIndex;
                childCount[i] = source.children.size();
                for (var child : source.children.values()) {
                    childChars[childIndex] = child.edgeSource.charAt(child.edgeStart);
                    childNodes[childIndex++] = child.id;
                }
                singles[i] = source.single;
                matchers[i] = source.matcher;
                var parent = source.parent;
                while (parent != null && parent.single == null && parent.matcher == null) {
                    parent = parent.parent;
                }
                fallback[i] = parent == null ? -1 : parent.id;
            }
            return new PrefixRadix<>(
                edgeSource, edgeStart, edgeLength, childOffset, childCount,
                childChars, childNodes, singles, matchers, fallback
            );
        }
    }

    private static final class RadixBuilderNode<T> {
        private final String edgeSource;
        private int edgeStart;
        private final int edgeEnd;
        private RadixBuilderNode<T> parent;
        private final TreeMap<Character, RadixBuilderNode<T>> children = new TreeMap<>();
        private @Nullable PathTemplateHolder<T> single;
        private @Nullable StemMatcher<T> matcher;
        private int id;

        private RadixBuilderNode(String edgeSource, int edgeStart, int edgeEnd, RadixBuilderNode<T> parent) {
            this.edgeSource = edgeSource;
            this.edgeStart = edgeStart;
            this.edgeEnd = edgeEnd;
            this.parent = parent;
        }
    }

    private record DecisionTrie<T>(int[] staticOffset, int[] staticCount, String[] staticSegments, int[] staticHashes, int[] staticChildren, int[] parameterChild,
                                   DecisionRoute<T>[] terminalWithoutSlash, DecisionRoute<T>[] terminalWithSlash, WildcardRoutes<T>[] wildcards, int[] minimumRank) {

        @Nullable
        private PathTemplateMatch<T> match(String path, int startPosition) {
            return match(0, path, startPosition, startPosition == path.length(), 0, 0, 0);
        }

        @Nullable
        private PathTemplateMatch<T> match(int node,
                                           String path,
                                           int position,
                                           boolean endedWithSlash,
                                           long firstCapture,
                                           long secondCapture,
                                           int captureCount) {
            var length = path.length();
            if (position == length) {
                var terminal = endedWithSlash ? terminalWithSlash[node] : terminalWithoutSlash[node];
                var wildcard = node == 0 || endedWithSlash ? wildcard(node, path, position) : null;
                if (terminal == null || (wildcard != null && wildcard.rank < terminal.rank)) {
                    return wildcard == null
                        ? null
                        : materialize(wildcard, path, firstCapture, secondCapture, captureCount, position);
                }
                return materialize(terminal, path, firstCapture, secondCapture, captureCount, -1);
            }

            var end = position;
            var hash = 0;
            while (end < length && path.charAt(end) != '/') {
                hash = 31 * hash + path.charAt(end++);
            }
            var nextPosition = end == length ? end : end + 1;
            var nextEndedWithSlash = end < length && nextPosition == length;
            var staticChild = this.staticChild(node, hash, path, position, end);
            var parameter = end > position || end < length ? parameterChild[node] : -1;
            var wildcard = wildcard(node, path, position);

            var staticTried = staticChild < 0;
            var parameterTried = parameter < 0;
            var wildcardTried = wildcard == null;
            for (var attempt = 0; attempt < 3; attempt++) {
                var staticRank = staticTried ? Integer.MAX_VALUE : minimumRank[staticChild];
                var parameterRank = parameterTried ? Integer.MAX_VALUE : minimumRank[parameter];
                var wildcardRank = wildcardTried ? Integer.MAX_VALUE : wildcard.rank;
                var bestRank = Math.min(staticRank, Math.min(parameterRank, wildcardRank));
                if (bestRank == Integer.MAX_VALUE) {
                    return null;
                }

                final PathTemplateMatch<T> match;
                if (staticRank == bestRank) {
                    staticTried = true;
                    match = this.match(
                        staticChild, path, nextPosition, nextEndedWithSlash,
                        firstCapture, secondCapture, captureCount
                    );
                } else if (parameterRank == bestRank) {
                    parameterTried = true;
                    var range = range(position, end);
                    var nextFirst = captureCount == 0 ? range : firstCapture;
                    var nextSecond = captureCount == 1 ? range : secondCapture;
                    match = this.match(
                        parameter, path, nextPosition, nextEndedWithSlash,
                        nextFirst, nextSecond, captureCount + 1
                    );
                } else {
                    wildcardTried = true;
                    match = materialize(wildcard, path, firstCapture, secondCapture, captureCount, position);
                }
                if (match != null) {
                    return match;
                }
            }
            return null;
        }

        private int staticChild(int node, int hash, String path, int start, int end) {
            var offset = staticOffset[node];
            var count = staticCount[node];
            if (count <= 4) {
                var limit = offset + count;
                for (var i = offset; i < limit; i++) {
                    if (staticHashes[i] == hash && segmentEquals(path, start, end, staticSegments[i])) {
                        return staticChildren[i];
                    }
                }
                return -1;
            }
            var low = offset;
            var high = offset + count - 1;
            while (low <= high) {
                var middle = (low + high) >>> 1;
                var current = staticHashes[middle];
                if (current < hash) {
                    low = middle + 1;
                } else if (current > hash) {
                    high = middle - 1;
                } else {
                    var first = middle;
                    while (first > offset && staticHashes[first - 1] == hash) {
                        first--;
                    }
                    var limit = offset + count;
                    for (var i = first; i < limit && staticHashes[i] == hash; i++) {
                        if (segmentEquals(path, start, end, staticSegments[i])) {
                            return staticChildren[i];
                        }
                    }
                    return -1;
                }
            }
            return -1;
        }

        private static boolean segmentEquals(String path, int start, int end, String expected) {
            return expected.length() == end - start && path.regionMatches(start, expected, 0, end - start);
        }

        @Nullable
        private DecisionRoute<T> wildcard(int node, String path, int position) {
            var routes = wildcards[node];
            if (routes == null) {
                return null;
            }
            return routes.match(path, position);
        }

        private static long range(int start, int end) {
            return (long) start << 32 | end & 0xffffffffL;
        }

        @Nullable
        private static <T> PathTemplateMatch<T> materialize(DecisionRoute<T> route,
                                                            String path,
                                                            long firstCapture,
                                                            long secondCapture,
                                                            int captureCount,
                                                            int wildcardStart) {
            var holder = route.holder;
            if (wildcardStart >= 0) {
                var wildcardPrefix = holder.template.wildcardPrefix();
                var wildcardRange = range(
                    wildcardPrefix == null
                        ? wildcardStart + route.wildcardLiteralPrefixLength()
                        : wildcardPrefix.length(),
                    route.wildcardCaptureEnd(path)
                );
                if (wildcardPrefix != null) {
                    firstCapture = wildcardRange;
                    captureCount = 1;
                } else {
                    if (captureCount == 0) {
                        firstCapture = wildcardRange;
                    } else if (captureCount == 1) {
                        secondCapture = wildcardRange;
                    }
                    captureCount++;
                }
            }

            var parameters = holder.template.materializeCaptured(
                path, firstCapture, secondCapture, captureCount
            );
            if (parameters != null) {
                return new PathTemplateMatch<>(holder.template.templateString(), parameters, holder.value);
            }
            return matchHolder(holder, path, null);
        }
    }

    /**
         * Keeps small wildcard buckets linear and compiles larger buckets into a reverse suffix trie.
         * Suffix traversal starts at the end of the request path, so matching is allocation-free and
         * does not scan possible positions of {@code *}. Empty suffixes stay at the trie root and
         * therefore preserve trailing-catch-all behavior.
         */
    private record WildcardRoutes<T>(int minimumRank, DecisionRoute<T> firstRoute, @Nullable List<DecisionRoute<T>> linear, @Nullable SuffixNode<T> suffixRoot) {

        private static <T> WildcardRoutes<T> create(List<DecisionRoute<T>> routes) {
            if (routes.size() <= WILDCARD_SUFFIX_TRIE_THRESHOLD) {
                return new WildcardRoutes<>(
                    routes.get(0).rank, routes.get(0), List.copyOf(routes), null
                );
            }
            var root = new SuffixBuilderNode<T>();
            for (var route : routes) {
                var node = root;
                var suffix = route.wildcardLiteralSuffix();
                for (var i = suffix.length() - 1; i >= 0; i--) {
                    var ch = suffix.charAt(i);
                    var nextNode = node.children.get(ch);
                    if (nextNode == null) {
                        nextNode = new SuffixBuilderNode<>();
                        node.children.put(ch, nextNode);
                    }
                    node = nextNode;
                }
                node.routes.add(route);
            }
            return new WildcardRoutes<>(
                routes.get(0).rank, routes.get(0), null, SuffixNode.freeze(root)
            );
        }

        @Nullable
        private DecisionRoute<T> match(String path, int position) {
            var routes = linear;
            if (routes != null) {
                for (var route : routes) {
                    if (route.wildcardMatches(path, position)) {
                        return route;
                    }
                }
                return null;
            }
            if (firstRoute.wildcardMatches(path, position)) {
                return firstRoute;
            }

            var node = suffixRoot;
            if (node == null) {
                return null;
            }
            var best = node.firstPrefixMatch(path, position, path.length());
            for (var i = path.length() - 1; i >= position; i--) {
                node = node.child(path.charAt(i));
                if (node == null) {
                    break;
                }
                var route = node.firstPrefixMatch(path, position, i);
                if (route != null && (best == null || route.rank < best.rank)) {
                    best = route;
                    if (best.rank == minimumRank) {
                        break;
                    }
                }
            }
            return best;
        }
    }

    private static final class SuffixBuilderNode<T> {
        private final TreeMap<Character, SuffixBuilderNode<T>> children = new TreeMap<>();
        private final List<DecisionRoute<T>> routes = new ArrayList<>();
    }

    private record SuffixNode<T>(char[] childChars, SuffixNode<T>[] children, List<DecisionRoute<T>> routes) {

        private static <T> SuffixNode<T> freeze(SuffixBuilderNode<T> source) {
            var size = source.children.size();
            var childChars = new char[size];
            @SuppressWarnings("unchecked")
            var children = (SuffixNode<T>[]) new SuffixNode<?>[size];
            var index = 0;
            for (var entry : source.children.entrySet()) {
                childChars[index] = entry.getKey();
                children[index] = freeze(entry.getValue());
                index++;
            }
            return new SuffixNode<>(childChars, children, List.copyOf(source.routes));
        }

        @Nullable
        private SuffixNode<T> child(char value) {
            if (childChars.length <= 4) {
                for (var i = 0; i < childChars.length; i++) {
                    if (childChars[i] == value) {
                        return children[i];
                    }
                }
                return null;
            }
            var index = Arrays.binarySearch(childChars, value);
            return index < 0 ? null : children[index];
        }

        @Nullable
        private DecisionRoute<T> firstPrefixMatch(String path, int position, int captureEnd) {
            for (var route : routes) {
                if (route.wildcardPrefixMatches(path, position, captureEnd)) {
                    return route;
                }
            }
            return null;
        }
    }

    private static final class DecisionTrieBuilder<T> {
        private final int segmentOffset;
        private final DecisionBuilderNode<T> root = new DecisionBuilderNode<>();

        private DecisionTrieBuilder(int segmentOffset) {
            this.segmentOffset = segmentOffset;
        }

        private void put(DecisionRoute<T> route) {
            var node = root;
            var segments = route.holder.template.decisionSegments();
            for (var i = segmentOffset; i < segments.length; i++) {
                var segment = segments[i];
                var kind = segment.kind();
                if (kind == HybridPathTemplate.SegmentKind.LITERAL) {
                    var val = segment.value();
                    var nextNode = node.staticChildren.get(val);
                    if (nextNode == null) {
                        nextNode = new DecisionBuilderNode<>();
                        node.staticChildren.put(val, nextNode);
                    }
                    node = nextNode;
                } else if (kind == HybridPathTemplate.SegmentKind.PARAMETER) {
                    if (node.parameterChild == null) {
                        node.parameterChild = new DecisionBuilderNode<>();
                    }
                    node = node.parameterChild;
                } else if (kind == HybridPathTemplate.SegmentKind.WILDCARD) {
                    node.wildcards.add(route);
                    return;
                }
            }
            if (route.holder.template.trailingSlash()) {
                node.terminalWithSlash = route;
            } else {
                node.terminalWithoutSlash = route;
            }
        }

        private DecisionTrie<T> build() {
            var nodes = new ArrayList<DecisionBuilderNode<T>>();
            root.id = 0;
            nodes.add(root);
            var entryComparator = new EntryComparator<T>();
            for (var i = 0; i < nodes.size(); i++) {
                var node = nodes.get(i);
                node.orderedStaticChildren = new ArrayList<>(node.staticChildren.entrySet());
                node.orderedStaticChildren.sort(entryComparator);
                for (var entry : node.orderedStaticChildren) {
                    entry.getValue().id = nodes.size();
                    nodes.add(entry.getValue());
                }
                if (node.parameterChild != null) {
                    node.parameterChild.id = nodes.size();
                    nodes.add(node.parameterChild);
                }
            }

            var nodeCount = nodes.size();
            var edgeCount = 0;
            for (var i = 0; i < nodeCount; i++) {
                edgeCount += nodes.get(i).orderedStaticChildren.size();
            }

            var staticOffset = new int[nodeCount];
            var staticCount = new int[nodeCount];
            var staticSegments = new String[edgeCount];
            var staticHashes = new int[edgeCount];
            var staticChildren = new int[edgeCount];
            var parameterChild = new int[nodeCount];
            Arrays.fill(parameterChild, -1);
            @SuppressWarnings("unchecked")
            var terminalWithoutSlash = (DecisionRoute<T>[]) new DecisionRoute<?>[nodeCount];
            @SuppressWarnings("unchecked")
            var terminalWithSlash = (DecisionRoute<T>[]) new DecisionRoute<?>[nodeCount];
            @SuppressWarnings("unchecked")
            var wildcards = (WildcardRoutes<T>[]) new WildcardRoutes<?>[nodeCount];
            var minimumRank = new int[nodeCount];
            Arrays.fill(minimumRank, Integer.MAX_VALUE);

            var edge = 0;
            var routeComparator = new RouteComparator<T>();
            for (var i = 0; i < nodeCount; i++) {
                var node = nodes.get(i);
                staticOffset[i] = edge;
                staticCount[i] = node.orderedStaticChildren.size();
                for (var entry : node.orderedStaticChildren) {
                    staticSegments[edge] = entry.getKey();
                    staticHashes[edge] = entry.getKey().hashCode();
                    staticChildren[edge++] = entry.getValue().id;
                }
                if (node.parameterChild != null) {
                    parameterChild[i] = node.parameterChild.id;
                }
                terminalWithoutSlash[i] = node.terminalWithoutSlash;
                terminalWithSlash[i] = node.terminalWithSlash;
                if (!node.wildcards.isEmpty()) {
                    node.wildcards.sort(routeComparator);
                    wildcards[i] = WildcardRoutes.create(node.wildcards);
                }
            }

            for (var i = nodeCount - 1; i >= 0; i--) {
                if (terminalWithoutSlash[i] != null) {
                    minimumRank[i] = Math.min(minimumRank[i], terminalWithoutSlash[i].rank);
                }
                if (terminalWithSlash[i] != null) {
                    minimumRank[i] = Math.min(minimumRank[i], terminalWithSlash[i].rank);
                }
                if (wildcards[i] != null) {
                    minimumRank[i] = Math.min(minimumRank[i], wildcards[i].minimumRank);
                }
                var limit = staticOffset[i] + staticCount[i];
                for (var j = staticOffset[i]; j < limit; j++) {
                    minimumRank[i] = Math.min(minimumRank[i], minimumRank[staticChildren[j]]);
                }
                if (parameterChild[i] >= 0) {
                    minimumRank[i] = Math.min(minimumRank[i], minimumRank[parameterChild[i]]);
                }
            }
            return new DecisionTrie<>(
                staticOffset, staticCount, staticSegments, staticHashes, staticChildren,
                parameterChild, terminalWithoutSlash, terminalWithSlash, wildcards, minimumRank
            );
        }

        private static final class EntryComparator<T> implements Comparator<Map.Entry<String, DecisionBuilderNode<T>>> {
            @Override
            public int compare(Map.Entry<String, DecisionBuilderNode<T>> e1, Map.Entry<String, DecisionBuilderNode<T>> e2) {
                var hash1 = e1.getKey().hashCode();
                var hash2 = e2.getKey().hashCode();
                if (hash1 != hash2) {
                    return Integer.compare(hash1, hash2);
                }
                return e1.getKey().compareTo(e2.getKey());
            }
        }

        private static final class RouteComparator<T> implements Comparator<DecisionRoute<T>> {
            @Override
            public int compare(DecisionRoute<T> r1, DecisionRoute<T> r2) {
                return Integer.compare(r1.rank, r2.rank);
            }
        }
    }

    private static final class DecisionBuilderNode<T> {
        private final Map<String, DecisionBuilderNode<T>> staticChildren = new HashMap<>();
        private @Nullable DecisionBuilderNode<T> parameterChild;
        private final List<DecisionRoute<T>> wildcards = new ArrayList<>();
        private @Nullable DecisionRoute<T> terminalWithoutSlash;
        private @Nullable DecisionRoute<T> terminalWithSlash;
        private List<Map.Entry<String, DecisionBuilderNode<T>>> orderedStaticChildren = List.of();
        private int id;
    }
}
