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
import java.util.TreeSet;

/**
 * Matcher that resolves every dynamic route through one full-path segment decision trie.
 *
 * <h2>Build algorithm</h2>
 * <ol>
 *   <li>Place exact routes in a hash map containing cached match results.</li>
 *   <li>Sort dynamic stem groups from longest to shortest, then traverse candidates in their
 *       semantic order. Assign a monotonically increasing rank to each route; lower rank means
 *       higher priority.</li>
 *   <li>Insert every route segment into a tree. Each node owns hashed literal children, at most one
 *       parameter child, optional wildcard terminals, and terminals with or without trailing
 *       slash.</li>
 *   <li>Flatten nodes and edges into arrays. Literal edges are sorted by segment hash, and every
 *       node receives the minimum route rank reachable below it.</li>
 * </ol>
 *
 * <h2>Lookup algorithm</h2>
 * <ol>
 *   <li>Check the exact map, then scan the request one segment at a time.</li>
 *   <li>Hash each segment directly from the request string and locate a literal child without
 *       allocating a segment substring. A region comparison resolves hash collisions.</li>
 *   <li>Literal, parameter, and wildcard alternatives are attempted in order of their precomputed
 *       minimum rank. If a more specific branch reaches a dead end, matching backtracks to the
 *       next-ranked branch. Wildcards carrying a final-segment literal prefix are considered only
 *       when that prefix matches at the current request position.</li>
 *   <li>Carry capture offsets as primitive ranges during traversal and materialize parameters only
 *       after the winning terminal is known.</li>
 * </ol>
 *
 * <h2>Performance characteristics</h2>
 * <p>Lookup depends primarily on path segment count and literal fanout rather than the number of
 * routes. Collision-heavy route sets therefore scale well and misses allocate nothing. For ordinary
 * route tables where each static stem already identifies one candidate, full-path segment hashing,
 * recursion, and rank-directed branching add overhead compared with the radix matcher.</p>
 *
 * @param <T> matched value type
 */
class DecisionPathTemplateMatcher<T> {

    private final Map<String, Set<PathTemplateHolder<T>>> pathTemplateMap = new HashMap<>();
    private volatile LookupState<T> lookupState = LookupState.empty();

    public record PathTemplateMatch<T>(String matchedTemplate, Map<String, String> parameters, T value) {}

    @Nullable
    public PathTemplateMatch<T> match(final String path) {
        final String normalizedPath = path.isEmpty() ? "/" : path;
        var state = this.lookupState;
        var exact = state.exactPaths.get(normalizedPath);
        if (exact != null) {
            return exact;
        }
        return state.decisionTrie.match(normalizedPath);
    }

    private static <T> PathTemplateMatch<T> result(PathTemplateHolder<T> holder, Map<String, String> parameters) {
        return new PathTemplateMatch<>(holder.template.templateString(), parameters, holder.value);
    }

    private static int mapCapacity(int size) {
        return size < 3 ? size + 1 : (int) (size / 0.75f) + 1;
    }

    public synchronized Map.@Nullable Entry<DecisionPathTemplate, T> add(final DecisionPathTemplate template,
                                                                          final T value) {
        return this.add(template, value, true);
    }

    private Map.@Nullable Entry<DecisionPathTemplate, T> add(final DecisionPathTemplate template,
                                                              final T value,
                                                              boolean rebuildLookupState) {
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
        if (rebuildLookupState) {
            this.buildLookupState();
        }
        return null;
    }

    private static String trimBase(DecisionPathTemplate template) {
        var wildcardPrefix = template.wildcardPrefix();
        return wildcardPrefix == null ? template.base() : wildcardPrefix;
    }

    private void buildLookupState() {
        var builder = new DecisionTrieBuilder<T>();
        var exactPaths = new HashMap<String, PathTemplateMatch<T>>(this.pathTemplateMap.size());
        var dynamicGroups = new ArrayList<Map.Entry<String, Set<PathTemplateHolder<T>>>>();

        for (var entry : this.pathTemplateMap.entrySet()) {
            boolean hasDynamic = false;
            for (var holder : entry.getValue()) {
                if (holder.template.exact()) {
                    exactPaths.put(
                        holder.template.templateString(),
                        new PathTemplateMatch<>(holder.template.templateString(), Map.of(), holder.value)
                    );
                } else {
                    hasDynamic = true;
                }
            }
            if (hasDynamic) {
                dynamicGroups.add(entry);
            }
        }

        dynamicGroups.sort(
            Comparator.<Map.Entry<String, Set<PathTemplateHolder<T>>>>comparingInt(entry -> entry.getKey().length())
                .reversed()
                .thenComparing(Map.Entry::getKey)
        );

        int rank = 0;
        for (var entry : dynamicGroups) {
            for (var holder : entry.getValue()) {
                if (!holder.template.exact()) {
                    builder.put(new DecisionRoute<>(holder, rank++));
                }
            }
        }
        this.lookupState = new LookupState<>(Map.copyOf(exactPaths), builder.build());
    }

    public synchronized Map.@Nullable Entry<DecisionPathTemplate, T> add(final String pathTemplate, final T value) {
        return this.add(DecisionPathTemplate.create(pathTemplate), value, true);
    }

    public synchronized DecisionPathTemplateMatcher<T> addAll(DecisionPathTemplateMatcher<T> pathTemplateMatcher) {
        for (var entry : pathTemplateMatcher.getPathTemplateMap().entrySet()) {
            for (var holder : entry.getValue()) {
                this.add(holder.template, holder.value, false);
            }
        }
        this.buildLookupState();
        return this;
    }

    Map<String, Set<PathTemplateHolder<T>>> getPathTemplateMap() {
        return this.pathTemplateMap;
    }

    public Set<DecisionPathTemplate> getPathTemplates() {
        var templates = new HashSet<DecisionPathTemplate>();
        for (var holders : this.pathTemplateMap.values()) {
            for (var holder : holders) {
                templates.add(holder.template);
            }
        }
        return templates;
    }

    public synchronized DecisionPathTemplateMatcher<T> remove(final String pathTemplate) {
        return this.remove(DecisionPathTemplate.create(pathTemplate));
    }

    private DecisionPathTemplateMatcher<T> remove(DecisionPathTemplate template) {
        var base = trimBase(template);
        var values = this.pathTemplateMap.get(base);
        if (values == null) {
            return this;
        }

        var newValues = new TreeSet<>(values);
        boolean removed = newValues.removeIf(holder -> holder.template.templateString().equals(template.templateString()));
        if (!removed) {
            return this;
        }
        if (newValues.isEmpty()) {
            this.pathTemplateMap.remove(base);
        } else {
            this.pathTemplateMap.put(base, newValues);
        }
        this.buildLookupState();
        return this;
    }

    public synchronized @Nullable T get(String template) {
        var pathTemplate = DecisionPathTemplate.create(template);
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

    int decisionNodeCount() {
        return this.lookupState.decisionTrie.terminalWithoutSlash.length;
    }

    private static final class PathTemplateHolder<T> implements Comparable<PathTemplateHolder<T>> {
        private final T value;
        private final DecisionPathTemplate template;

        private PathTemplateHolder(T value, DecisionPathTemplate template) {
            this.value = value;
            this.template = template;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof PathTemplateHolder<?> that && this.template.equals(that.template);
        }

        @Override
        public int hashCode() {
            return this.template.hashCode();
        }

        @Override
        public int compareTo(PathTemplateHolder<T> o) {
            return this.template.compareTo(o.template);
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
                this.wildcardLiteralPrefix = segment.kind() == DecisionPathTemplate.SegmentKind.WILDCARD
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

    private record LookupState<T>(Map<String, PathTemplateMatch<T>> exactPaths, DecisionTrie<T> decisionTrie) {
        private static <T> LookupState<T> empty() {
            return new LookupState<>(Map.of(), DecisionTrie.empty());
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
        private PathTemplateMatch<T> match(String path) {
            return this.match(0, path, 1, path.length() == 1, 0, 0, 0);
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
                var terminal = endedWithSlash
                    ? this.terminalWithSlash[node]
                    : this.terminalWithoutSlash[node];
                var wildcard = node == 0 || endedWithSlash
                    ? this.wildcard(node, path, position)
                    : null;
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
                hash = 31 * hash + path.charAt(end);
                end++;
            }
            int nextPosition = end == length ? end : end + 1;
            boolean nextEndedWithSlash = end < length && nextPosition == length;

            int staticChild = this.staticChild(node, hash, path, position, end);
            int parameterChild = this.parameterChild[node];
            boolean parameterNotEmptyOrIntermediate = end > position || end < length;
            if (!parameterNotEmptyOrIntermediate) {
                parameterChild = -1;
            }
            var wildcard = this.wildcard(node, path, position);

            boolean staticTried = staticChild < 0;
            boolean parameterTried = parameterChild < 0;
            boolean wildcardTried = wildcard == null;
            for (int attempt = 0; attempt < 3; attempt++) {
                int staticRank = staticTried ? Integer.MAX_VALUE : this.minimumRank[staticChild];
                int parameterRank = parameterTried ? Integer.MAX_VALUE : this.minimumRank[parameterChild];
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
                        parameterChild, path, nextPosition, nextEndedWithSlash,
                        nextFirst, nextSecond, captureCount + 1
                    );
                } else {
                    wildcardTried = true;
                    match = materialize(
                        wildcard, path, firstCapture, secondCapture, captureCount, position
                    );
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
            int length = end - start;
            return expected.length() == length && path.regionMatches(start, expected, 0, length);
        }

        @Nullable
        private DecisionRoute<T> wildcard(int node, String path, int position) {
            var routes = this.wildcards[node];
            if (routes == null) {
                return null;
            }
            for (var route : routes) {
                var wildcardPrefix = route.holder.template.wildcardPrefix();
                if ((wildcardPrefix == null || path.startsWith(wildcardPrefix))
                    && route.wildcardMatches(path, position)) {
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
            var template = holder.template;
            if (wildcardStart >= 0) {
                var wildcardPrefix = template.wildcardPrefix();
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

            var parameters = template.materializeCaptured(path, firstCapture, secondCapture, captureCount);
            if (parameters != null) {
                return result(holder, parameters);
            }

            int parameterCount = template.parameterCount();
            if (parameterCount == 1) {
                parameters = template.matchSingleParameter(path);
                return parameters == null ? null : result(holder, parameters);
            }
            if (parameterCount == 2) {
                parameters = template.matchTwoParameters(path);
                return parameters == null ? null : result(holder, parameters);
            }

            var genericParameters = new LinkedHashMap<String, String>(mapCapacity(parameterCount));
            return template.matches(path, genericParameters)
                ? result(holder, genericParameters)
                : null;
        }

        private static <T> DecisionTrie<T> empty() {
            @SuppressWarnings("unchecked")
            DecisionRoute<T>[] terminals = (DecisionRoute<T>[]) new DecisionRoute<?>[1];
            @SuppressWarnings("unchecked")
            List<DecisionRoute<T>>[] wildcards = (List<DecisionRoute<T>>[]) new List<?>[1];
            return new DecisionTrie<>(
                new int[1], new int[1], new String[0], new int[0], new int[0],
                new int[]{-1}, terminals, terminals.clone(), wildcards, new int[]{Integer.MAX_VALUE}
            );
        }
    }

    private static final class DecisionTrieBuilder<T> {
        private final BuilderNode<T> root = new BuilderNode<>();

        private void put(DecisionRoute<T> route) {
            var node = this.root;
            for (var segment : route.holder.template.decisionSegments()) {
                switch (segment.kind()) {
                    case LITERAL -> node = node.staticChildren.computeIfAbsent(segment.value(), ignored -> new BuilderNode<>());
                    case PARAMETER -> {
                        if (node.parameterChild == null) {
                            node.parameterChild = new BuilderNode<>();
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
            var nodes = new ArrayList<BuilderNode<T>>();
            this.root.id = 0;
            nodes.add(this.root);
            for (int i = 0; i < nodes.size(); i++) {
                var node = nodes.get(i);
                node.orderedStaticChildren = new ArrayList<>(node.staticChildren.entrySet());
                node.orderedStaticChildren.sort(
                    Comparator.<Map.Entry<String, BuilderNode<T>>>comparingInt(entry -> entry.getKey().hashCode())
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
            int staticEdgeCount = 0;
            for (var node : nodes) {
                staticEdgeCount += node.orderedStaticChildren.size();
            }

            var staticOffset = new int[nodeCount];
            var staticCount = new int[nodeCount];
            var staticSegments = new String[staticEdgeCount];
            var staticHashes = new int[staticEdgeCount];
            var staticChildren = new int[staticEdgeCount];
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

            int staticIndex = 0;
            for (int i = 0; i < nodeCount; i++) {
                var node = nodes.get(i);
                staticOffset[i] = staticIndex;
                staticCount[i] = node.orderedStaticChildren.size();
                for (var entry : node.orderedStaticChildren) {
                    staticSegments[staticIndex] = entry.getKey();
                    staticHashes[staticIndex] = entry.getKey().hashCode();
                    staticChildren[staticIndex] = entry.getValue().id;
                    staticIndex++;
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
                var withoutSlash = terminalWithoutSlash[i];
                if (withoutSlash != null) {
                    minimumRank[i] = Math.min(minimumRank[i], withoutSlash.rank);
                }
                var withSlash = terminalWithSlash[i];
                if (withSlash != null) {
                    minimumRank[i] = Math.min(minimumRank[i], withSlash.rank);
                }
                var wildcardRoutes = wildcards[i];
                if (wildcardRoutes != null) {
                    minimumRank[i] = Math.min(minimumRank[i], wildcardRoutes.get(0).rank);
                }
                int offset = staticOffset[i];
                int limit = offset + staticCount[i];
                for (int edge = offset; edge < limit; edge++) {
                    minimumRank[i] = Math.min(minimumRank[i], minimumRank[staticChildren[edge]]);
                }
                int parameter = parameterChild[i];
                if (parameter >= 0) {
                    minimumRank[i] = Math.min(minimumRank[i], minimumRank[parameter]);
                }
            }

            return new DecisionTrie<>(
                staticOffset, staticCount, staticSegments, staticHashes, staticChildren, parameterChild,
                terminalWithoutSlash, terminalWithSlash, wildcards, minimumRank
            );
        }
    }

    private static final class BuilderNode<T> {
        private final Map<String, BuilderNode<T>> staticChildren = new HashMap<>();
        private @Nullable BuilderNode<T> parameterChild;
        private final List<DecisionRoute<T>> wildcards = new ArrayList<>();
        private @Nullable DecisionRoute<T> terminalWithoutSlash;
        private @Nullable DecisionRoute<T> terminalWithSlash;
        private List<Map.Entry<String, BuilderNode<T>>> orderedStaticChildren = List.of();
        private int id;
    }
}
