package io.koraframework.http.server.common.router;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
 *       backtracking exactly like the full decision matcher. A wildcard branch with a literal
 *       prefix is eligible only when that prefix matches at the current path position.</li>
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
        final String normalizedPath = path.isEmpty() ? "/" : path;
        var state = this.lookupState;
        var exact = state.exactPaths.get(normalizedPath);
        if (exact != null) {
            return exact;
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
            var values = this.pathTemplateMap.get(base);
            var newValues = values == null
                ? new TreeSet<PathTemplateHolder<T>>()
                : new TreeSet<>(values);
            var holder = new PathTemplateHolder<>(value, template);
            var equivalent = newValues.ceiling(holder);
            if (equivalent != null && equivalent.compareTo(holder) == 0) {
                return Map.entry(equivalent.template, equivalent.value);
            }
            newValues.add(holder);
            this.pathTemplateMap.put(base, newValues);
            return null;
        }

        Map.@Nullable Entry<HybridPathTemplate, T> add(String template, T value) {
            return this.add(HybridPathTemplate.create(template), value);
        }

        Builder<T> addAll(Builder<T> builder) {
            for (var entry : builder.pathTemplateMap.entrySet()) {
                for (var holder : entry.getValue()) {
                    this.add(holder.template, holder.value);
                }
            }
            return this;
        }

        Builder<T> remove(String template) {
            var pathTemplate = HybridPathTemplate.create(template);
            var base = trimBase(pathTemplate);
            var values = this.pathTemplateMap.get(base);
            if (values == null) {
                return this;
            }
            var newValues = new TreeSet<>(values);
            boolean removed = newValues.removeIf(
                holder -> holder.template.templateString().equals(pathTemplate.templateString())
            );
            if (!removed) {
                return this;
            }
            if (newValues.isEmpty()) {
                this.pathTemplateMap.remove(base);
            } else {
                this.pathTemplateMap.put(base, newValues);
            }
            return this;
        }

        @Nullable
        T get(String template) {
            var pathTemplate = HybridPathTemplate.create(template);
            var values = this.pathTemplateMap.get(trimBase(pathTemplate));
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
            for (var holders : this.pathTemplateMap.values()) {
                for (var holder : holders) {
                    result.add(holder.template);
                }
            }
            return result;
        }

        HybridPathTemplateMatcher<T> build() {
            return new HybridPathTemplateMatcher<>(
                buildLookupState(this.pathTemplateMap, this.decisionThreshold)
            );
        }
    }

    int singleStemCount() {
        return this.lookupState.singleStems;
    }

    int linearStemCount() {
        return this.lookupState.linearStems;
    }

    int decisionStemCount() {
        return this.lookupState.decisionStems;
    }

    @Nullable
    private static <T> PathTemplateMatch<T> matchHolder(PathTemplateHolder<T> holder,
                                                        String path,
                                                        @Nullable LinkedHashMap<String, String> reusable) {
        int count = holder.template.parameterCount();
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

    private static final class StemMatcher<T> {
        private static final int LINEAR = 0;
        private static final int DECISION = 1;

        private final int strategy;
        private final @Nullable List<PathTemplateHolder<T>> linear;
        private final @Nullable DecisionStemMatcher<T> decision;

        private StemMatcher(int strategy,
                            @Nullable List<PathTemplateHolder<T>> linear,
                            @Nullable DecisionStemMatcher<T> decision) {
            this.strategy = strategy;
            this.linear = linear;
            this.decision = decision;
        }

        private static <T> StemMatcher<T> linear(List<PathTemplateHolder<T>> holders) {
            return new StemMatcher<>(LINEAR, holders, null);
        }

        private static <T> StemMatcher<T> decision(String stem, List<PathTemplateHolder<T>> holders) {
            return new StemMatcher<>(DECISION, null, new DecisionStemMatcher<>(stem, holders));
        }

        @Nullable
        private PathTemplateMatch<T> match(String path) {
            if (this.strategy == DECISION) {
                return this.decision.match(path);
            }

            LinkedHashMap<String, String> reusable = null;
            for (var holder : this.linear) {
                if (holder.template.parameterCount() > 2 && reusable == null) {
                    reusable = new LinkedHashMap<>(mapCapacity(holder.template.parameterCount()));
                }
                var match = matchHolder(holder, path, reusable);
                if (match != null) {
                    return match;
                }
            }
            return null;
        }
    }

    private static final class DecisionStemMatcher<T> {
        private final int startPosition;
        private final DecisionTrie<T> trie;

        private DecisionStemMatcher(String stem, List<PathTemplateHolder<T>> holders) {
            this.startPosition = stem.length();
            int completedSegments = 0;
            for (int i = 1; i < stem.length(); i++) {
                if (stem.charAt(i) == '/') {
                    completedSegments++;
                }
            }
            var builder = new DecisionTrieBuilder<T>(completedSegments);
            int rank = 0;
            for (var holder : holders) {
                builder.put(new DecisionRoute<>(holder, rank++));
            }
            this.trie = builder.build();
        }

        private PathTemplateMatch<T> match(String path) {
            return this.trie.match(path, this.startPosition);
        }
    }

    private static final class PathTemplateHolder<T> implements Comparable<PathTemplateHolder<T>> {
        private final T value;
        private final HybridPathTemplate template;

        private PathTemplateHolder(T value, HybridPathTemplate template) {
            this.value = value;
            this.template = template;
        }

        @Override
        public int compareTo(PathTemplateHolder<T> other) {
            return this.template.compareTo(other.template);
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof PathTemplateHolder<?> that && this.template.equals(that.template);
        }

        @Override
        public int hashCode() {
            return this.template.hashCode();
        }
    }

    private static final class DecisionRoute<T> {
        private final PathTemplateHolder<T> holder;
        private final int rank;
        private final @Nullable String wildcardLiteralPrefix;

        private DecisionRoute(PathTemplateHolder<T> holder, int rank) {
            this.holder = holder;
            this.rank = rank;
            var segments = holder.template.decisionSegments();
            if (segments.length == 0) {
                this.wildcardLiteralPrefix = null;
            } else {
                var segment = segments[segments.length - 1];
                this.wildcardLiteralPrefix = segment.kind() == HybridPathTemplate.SegmentKind.WILDCARD
                    ? segment.value()
                    : null;
            }
        }

        private int wildcardLiteralPrefixLength() {
            return this.wildcardLiteralPrefix == null ? 0 : this.wildcardLiteralPrefix.length();
        }

        private boolean wildcardMatches(String path, int position) {
            var prefix = this.wildcardLiteralPrefix;
            return prefix != null && path.regionMatches(position, prefix, 0, prefix.length());
        }
    }

    private record LookupState<T>(Map<String, PathTemplateMatch<T>> exactPaths,
                                  PrefixRadix<T> prefixTrie,
                                  int singleStems,
                                  int linearStems,
                                  int decisionStems) {}

    private static final class PrefixRadix<T> {
        private final String[] edgeSource;
        private final int[] edgeStart;
        private final int[] edgeLength;
        private final int[] childOffset;
        private final int[] childCount;
        private final char[] childChars;
        private final int[] childNodes;
        private final PathTemplateHolder<T>[] singles;
        private final StemMatcher<T>[] matchers;
        private final int[] fallback;

        private PrefixRadix(String[] edgeSource,
                            int[] edgeStart,
                            int[] edgeLength,
                            int[] childOffset,
                            int[] childCount,
                            char[] childChars,
                            int[] childNodes,
                            PathTemplateHolder<T>[] singles,
                            StemMatcher<T>[] matchers,
                            int[] fallback) {
            this.edgeSource = edgeSource;
            this.edgeStart = edgeStart;
            this.edgeLength = edgeLength;
            this.childOffset = childOffset;
            this.childCount = childCount;
            this.childChars = childChars;
            this.childNodes = childNodes;
            this.singles = singles;
            this.matchers = matchers;
            this.fallback = fallback;
        }

        private int child(int node, char value) {
            int offset = this.childOffset[node];
            int count = this.childCount[node];
            if (count <= 4) {
                int limit = offset + count;
                for (int i = offset; i < limit; i++) {
                    if (this.childChars[i] == value) {
                        return this.childNodes[i];
                    }
                }
                return -1;
            }
            int low = offset;
            int high = offset + count - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                char current = this.childChars[middle];
                if (current < value) {
                    low = middle + 1;
                } else if (current > value) {
                    high = middle - 1;
                } else {
                    return this.childNodes[middle];
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
            var node = this.root;
            int prefixIndex = 0;
            while (prefixIndex < prefix.length()) {
                char first = prefix.charAt(prefixIndex);
                var child = node.children.get(first);
                if (child == null) {
                    var leaf = new RadixBuilderNode<T>(prefix, prefixIndex, prefix.length(), node);
                    leaf.single = single;
                    leaf.matcher = matcher;
                    node.children.put(first, leaf);
                    return;
                }
                int common = commonPrefixLength(prefix, prefixIndex, child.edgeSource, child.edgeStart, child.edgeEnd);
                int childLength = child.edgeEnd - child.edgeStart;
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
            int limit = Math.min(first.length() - firstStart, secondEnd - secondStart);
            int common = 0;
            while (common < limit && first.charAt(firstStart + common) == second.charAt(secondStart + common)) {
                common++;
            }
            return common;
        }

        private PrefixRadix<T> build() {
            var nodes = new ArrayList<RadixBuilderNode<T>>();
            this.root.id = 0;
            nodes.add(this.root);
            for (int i = 0; i < nodes.size(); i++) {
                for (var child : nodes.get(i).children.values()) {
                    child.id = nodes.size();
                    nodes.add(child);
                }
            }

            int size = nodes.size();
            var edgeSource = new String[size];
            var edgeStart = new int[size];
            var edgeLength = new int[size];
            var childOffset = new int[size];
            var childCount = new int[size];
            var childChars = new char[Math.max(0, size - 1)];
            var childNodes = new int[Math.max(0, size - 1)];
            @SuppressWarnings("unchecked")
            PathTemplateHolder<T>[] singles = (PathTemplateHolder<T>[]) new PathTemplateHolder<?>[size];
            @SuppressWarnings("unchecked")
            StemMatcher<T>[] matchers = (StemMatcher<T>[]) new StemMatcher<?>[size];
            var fallback = new int[size];
            int childIndex = 0;
            for (int i = 0; i < size; i++) {
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

    private static final class DecisionTrie<T> {
        private final int[] staticOffset;
        private final int[] staticCount;
        private final String[] staticSegments;
        private final int[] staticHashes;
        private final int[] staticChildren;
        private final int[] parameterChild;
        private final DecisionRoute<T>[] terminalWithoutSlash;
        private final DecisionRoute<T>[] terminalWithSlash;
        private final List<DecisionRoute<T>>[] wildcards;
        private final int[] minimumRank;

        private DecisionTrie(int[] staticOffset,
                             int[] staticCount,
                             String[] staticSegments,
                             int[] staticHashes,
                             int[] staticChildren,
                             int[] parameterChild,
                             DecisionRoute<T>[] terminalWithoutSlash,
                             DecisionRoute<T>[] terminalWithSlash,
                             List<DecisionRoute<T>>[] wildcards,
                             int[] minimumRank) {
            this.staticOffset = staticOffset;
            this.staticCount = staticCount;
            this.staticSegments = staticSegments;
            this.staticHashes = staticHashes;
            this.staticChildren = staticChildren;
            this.parameterChild = parameterChild;
            this.terminalWithoutSlash = terminalWithoutSlash;
            this.terminalWithSlash = terminalWithSlash;
            this.wildcards = wildcards;
            this.minimumRank = minimumRank;
        }

        @Nullable
        private PathTemplateMatch<T> match(String path, int startPosition) {
            return this.match(0, path, startPosition, startPosition == path.length(), 0, 0, 0);
        }

        @Nullable
        private PathTemplateMatch<T> match(int node,
                                           String path,
                                           int position,
                                           boolean endedWithSlash,
                                           long firstCapture,
                                           long secondCapture,
                                           int captureCount) {
            int length = path.length();
            if (position == length) {
                var terminal = endedWithSlash ? this.terminalWithSlash[node] : this.terminalWithoutSlash[node];
                var wildcard = node == 0 || endedWithSlash ? this.wildcard(node, path, position) : null;
                if (terminal == null || wildcard != null && wildcard.rank < terminal.rank) {
                    return wildcard == null
                        ? null
                        : materialize(wildcard, path, firstCapture, secondCapture, captureCount, position);
                }
                return materialize(terminal, path, firstCapture, secondCapture, captureCount, -1);
            }

            int end = position;
            int hash = 0;
            while (end < length && path.charAt(end) != '/') {
                hash = 31 * hash + path.charAt(end++);
            }
            int nextPosition = end == length ? end : end + 1;
            boolean nextEndedWithSlash = end < length && nextPosition == length;
            int staticChild = this.staticChild(node, hash, path, position, end);
            int parameter = end > position || end < length ? this.parameterChild[node] : -1;
            var wildcard = this.wildcard(node, path, position);

            boolean staticTried = staticChild < 0;
            boolean parameterTried = parameter < 0;
            boolean wildcardTried = wildcard == null;
            for (int attempt = 0; attempt < 3; attempt++) {
                int staticRank = staticTried ? Integer.MAX_VALUE : this.minimumRank[staticChild];
                int parameterRank = parameterTried ? Integer.MAX_VALUE : this.minimumRank[parameter];
                int wildcardRank = wildcardTried ? Integer.MAX_VALUE : wildcard.rank;
                int bestRank = Math.min(staticRank, Math.min(parameterRank, wildcardRank));
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
                    long range = range(position, end);
                    long nextFirst = captureCount == 0 ? range : firstCapture;
                    long nextSecond = captureCount == 1 ? range : secondCapture;
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
            int offset = this.staticOffset[node];
            int count = this.staticCount[node];
            if (count <= 4) {
                int limit = offset + count;
                for (int i = offset; i < limit; i++) {
                    if (this.staticHashes[i] == hash && segmentEquals(path, start, end, this.staticSegments[i])) {
                        return this.staticChildren[i];
                    }
                }
                return -1;
            }
            int low = offset;
            int high = offset + count - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                int current = this.staticHashes[middle];
                if (current < hash) {
                    low = middle + 1;
                } else if (current > hash) {
                    high = middle - 1;
                } else {
                    int first = middle;
                    while (first > offset && this.staticHashes[first - 1] == hash) {
                        first--;
                    }
                    int limit = offset + count;
                    for (int i = first; i < limit && this.staticHashes[i] == hash; i++) {
                        if (segmentEquals(path, start, end, this.staticSegments[i])) {
                            return this.staticChildren[i];
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
            var routes = this.wildcards[node];
            if (routes == null) {
                return null;
            }
            for (var route : routes) {
                if (route.wildcardMatches(path, position)) {
                    return route;
                }
            }
            return null;
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
                long wildcardRange = range(
                    wildcardPrefix == null
                        ? wildcardStart + route.wildcardLiteralPrefixLength()
                        : wildcardPrefix.length(),
                    path.length()
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

    private static final class DecisionTrieBuilder<T> {
        private final int segmentOffset;
        private final DecisionBuilderNode<T> root = new DecisionBuilderNode<>();

        private DecisionTrieBuilder(int segmentOffset) {
            this.segmentOffset = segmentOffset;
        }

        private void put(DecisionRoute<T> route) {
            var node = this.root;
            var segments = route.holder.template.decisionSegments();
            for (int i = this.segmentOffset; i < segments.length; i++) {
                var segment = segments[i];
                switch (segment.kind()) {
                    case LITERAL ->
                        node = node.staticChildren.computeIfAbsent(segment.value(), ignored -> new DecisionBuilderNode<>());
                    case PARAMETER -> {
                        if (node.parameterChild == null) {
                            node.parameterChild = new DecisionBuilderNode<>();
                        }
                        node = node.parameterChild;
                    }
                    case WILDCARD -> {
                        node.wildcards.add(route);
                        return;
                    }
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
            this.root.id = 0;
            nodes.add(this.root);
            for (int i = 0; i < nodes.size(); i++) {
                var node = nodes.get(i);
                node.orderedStaticChildren = new ArrayList<>(node.staticChildren.entrySet());
                node.orderedStaticChildren.sort(
                    Comparator.<Map.Entry<String, DecisionBuilderNode<T>>>comparingInt(e -> e.getKey().hashCode())
                        .thenComparing(Map.Entry::getKey)
                );
                for (var entry : node.orderedStaticChildren) {
                    entry.getValue().id = nodes.size();
                    nodes.add(entry.getValue());
                }
                if (node.parameterChild != null) {
                    node.parameterChild.id = nodes.size();
                    nodes.add(node.parameterChild);
                }
            }

            int nodeCount = nodes.size();
            int edgeCount = nodes.stream().mapToInt(node -> node.orderedStaticChildren.size()).sum();
            var staticOffset = new int[nodeCount];
            var staticCount = new int[nodeCount];
            var staticSegments = new String[edgeCount];
            var staticHashes = new int[edgeCount];
            var staticChildren = new int[edgeCount];
            var parameterChild = new int[nodeCount];
            Arrays.fill(parameterChild, -1);
            @SuppressWarnings("unchecked")
            DecisionRoute<T>[] terminalWithoutSlash = (DecisionRoute<T>[]) new DecisionRoute<?>[nodeCount];
            @SuppressWarnings("unchecked")
            DecisionRoute<T>[] terminalWithSlash = (DecisionRoute<T>[]) new DecisionRoute<?>[nodeCount];
            @SuppressWarnings("unchecked")
            List<DecisionRoute<T>>[] wildcards = (List<DecisionRoute<T>>[]) new List<?>[nodeCount];
            var minimumRank = new int[nodeCount];
            Arrays.fill(minimumRank, Integer.MAX_VALUE);

            int edge = 0;
            for (int i = 0; i < nodeCount; i++) {
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
                    node.wildcards.sort(Comparator.comparingInt(route -> route.rank));
                    wildcards[i] = List.copyOf(node.wildcards);
                }
            }

            for (int i = nodeCount - 1; i >= 0; i--) {
                if (terminalWithoutSlash[i] != null) {
                    minimumRank[i] = Math.min(minimumRank[i], terminalWithoutSlash[i].rank);
                }
                if (terminalWithSlash[i] != null) {
                    minimumRank[i] = Math.min(minimumRank[i], terminalWithSlash[i].rank);
                }
                if (wildcards[i] != null) {
                    minimumRank[i] = Math.min(minimumRank[i], wildcards[i].get(0).rank);
                }
                int limit = staticOffset[i] + staticCount[i];
                for (int j = staticOffset[i]; j < limit; j++) {
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
