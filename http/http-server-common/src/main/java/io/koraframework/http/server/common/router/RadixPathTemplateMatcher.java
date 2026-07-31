package io.koraframework.http.server.common.router;

import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Matcher using an exact-path map and a flat compressed radix trie for dynamic stems.
 *
 * <h2>Build algorithm</h2>
 * <ol>
 *   <li>A mutable {@link Builder} groups routes by static stem and retains candidates in semantic
 *       priority order without compiling lookup state after each registration.</li>
 *   <li>Store exact routes as prebuilt match results in a hash map.</li>
 *   <li>Insert dynamic stems into a radix tree. Unlike a character trie, each edge stores the
 *       longest shared fragment, for example {@code /api/users/}, rather than one node per
 *       character. An insertion splits an edge only at the first differing character.</li>
 *   <li>Flatten edges, child ranges, terminal candidate lists, and terminal-ancestor fallback links
 *       into parallel arrays. Edges reference regions of existing stem strings instead of copying
 *       edge substrings.</li>
 *   <li>{@link Builder#build()} publishes those arrays and exact routes through one final
 *       {@code LookupState}; the resulting matcher is immutable.</li>
 * </ol>
 *
 * <h2>Lookup algorithm</h2>
 * <ol>
 *   <li>Return the cached exact result when present.</li>
 *   <li>Select a child by the first edge character, then verify the remaining compressed edge with
 *       {@link String#regionMatches(int, String, int, int)}.</li>
 *   <li>Remember the deepest terminal reached. At that terminal, test candidates linearly in
 *       semantic priority order using base-already-matched template methods.</li>
 *   <li>If all candidates fail, follow the fallback link to the next shorter terminal stem.</li>
 * </ol>
 *
 * <h2>Performance characteristics</h2>
 * <p>Compression reduces node count and dependent child transitions relative to the character
 * trie. Exact hits and stem misses allocate nothing. Successful captures allocate only their
 * result record, final substrings, and compact parameter map. A stem containing many competing
 * templates still has linear candidate cost; this limitation motivates
 * {@link HybridPathTemplateMatcher}. Building compiles the complete route table once, while lookup
 * needs neither synchronization nor a volatile snapshot read.</p>
 *
 * @param <T> matched value type
 */
class RadixPathTemplateMatcher<T> {

    private final LookupState<T> lookupState;

    private RadixPathTemplateMatcher(LookupState<T> lookupState) {
        this.lookupState = lookupState;
    }

    static <T> Builder<T> builder() {
        return new Builder<>();
    }

    record PathTemplateMatch<T>(String matchedTemplate, Map<String, String> parameters, T value) {}

    @Nullable
    public PathTemplateMatch<T> match(final String path) {
        final String normalizedPath = path.isEmpty() ? "/" : path;
        final var state = this.lookupState;

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
                || (edgeLength > 1 && !normalizedPath.regionMatches(
                pathIndex + 1, trie.edgeSource[child], trie.edgeStart[child] + 1, edgeLength - 1
            ))) {
                break;
            }

            pathIndex += edgeLength;
            node = child;
            if (trie.entries[node] != null) {
                candidate = node;
            }
        }

        while (candidate >= 0) {
            var match = this.handleStemMatch(trie.entries[candidate], normalizedPath);
            if (match != null) {
                return match;
            }
            candidate = trie.fallback[candidate];
        }
        return null;
    }

    @Nullable
    private PathTemplateMatch<T> handleStemMatch(final List<PathTemplateHolder<T>> entries, final String path) {
        LinkedHashMap<String, String> parameters = null;
        for (var holder : entries) {
            int parameterCount = holder.template.parameterCount();
            if (parameterCount == 1) {
                var match = holder.template.matchSingleParameter(path, true);
                if (match != null) {
                    return result(holder, match);
                }
                continue;
            }
            if (parameterCount == 2) {
                var match = holder.template.matchTwoParameters(path, true);
                if (match != null) {
                    return result(holder, match);
                }
                continue;
            }
            if (parameters == null) {
                parameters = new LinkedHashMap<>(mapCapacity(parameterCount));
            }
            if (holder.template.matches(path, parameters, true)) {
                return result(holder, parameters);
            }
            parameters.clear();
        }
        return null;
    }

    private static <T> PathTemplateMatch<T> result(PathTemplateHolder<T> holder, Map<String, String> parameters) {
        return new PathTemplateMatch<>(holder.template.templateString(), parameters, holder.value);
    }

    private static int mapCapacity(int size) {
        return size < 3 ? size + 1 : (int) (size / 0.75f) + 1;
    }

    private static String trimBase(RadixPathTemplate template) {
        var wildcardPrefix = template.wildcardPrefix();
        return wildcardPrefix == null ? template.base() : wildcardPrefix;
    }

    private static <T> LookupState<T> buildLookupState(
        Map<String, Set<PathTemplateHolder<T>>> pathTemplateMap
    ) {
        var exactPaths = new HashMap<String, PathTemplateMatch<T>>(pathTemplateMap.size());
        var prefixTrie = new PrefixRadixBuilder<T>();
        for (var entry : pathTemplateMap.entrySet()) {
            ArrayList<PathTemplateHolder<T>> dynamic = null;
            for (var holder : entry.getValue()) {
                if (holder.template.exact()) {
                    exactPaths.put(holder.template.templateString(),
                        new PathTemplateMatch<>(holder.template.templateString(), Map.of(), holder.value));
                } else {
                    if (dynamic == null) {
                        dynamic = new ArrayList<>(entry.getValue().size());
                    }
                    dynamic.add(holder);
                }
            }
            if (dynamic != null) {
                prefixTrie.put(entry.getKey(), List.copyOf(dynamic));
            }
        }
        return new LookupState<>(Map.copyOf(exactPaths), prefixTrie.build());
    }

    /**
     * Single-threaded mutable registration phase for producing immutable radix matchers.
     */
    static final class Builder<T> {
        private final Map<String, Set<PathTemplateHolder<T>>> pathTemplateMap = new HashMap<>();

        Map.@Nullable Entry<RadixPathTemplate, T> add(RadixPathTemplate template, T value) {
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

        Map.@Nullable Entry<RadixPathTemplate, T> add(String template, T value) {
            return this.add(RadixPathTemplate.create(template), value);
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
            var pathTemplate = RadixPathTemplate.create(template);
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
            var pathTemplate = RadixPathTemplate.create(template);
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

        Set<RadixPathTemplate> getPathTemplates() {
            var templates = new HashSet<RadixPathTemplate>();
            for (var holders : this.pathTemplateMap.values()) {
                for (var holder : holders) {
                    templates.add(holder.template);
                }
            }
            return templates;
        }

        RadixPathTemplateMatcher<T> build() {
            return new RadixPathTemplateMatcher<>(buildLookupState(this.pathTemplateMap));
        }
    }

    int radixNodeCount() {
        return this.lookupState.prefixTrie.entries.length;
    }

    private record PathTemplateHolder<T>(T value, RadixPathTemplate template) implements Comparable<PathTemplateHolder<T>> {

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

    private record LookupState<T>(Map<String, PathTemplateMatch<T>> exactPaths, PrefixRadix<T> prefixTrie) {}

    private record PrefixRadix<T>(String[] edgeSource, int[] edgeStart, int[] edgeLength, int[] childOffset, int[] childCount, char[] childChars, int[] childNodes,
                                  List<PathTemplateHolder<T>>[] entries, int[] fallback) {

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
        private final BuilderNode<T> root = new BuilderNode<>(null, 0, 0, null);

        private void put(String prefix, List<PathTemplateHolder<T>> entries) {
            var node = this.root;
            int prefixIndex = 0;
            while (prefixIndex < prefix.length()) {
                char first = prefix.charAt(prefixIndex);
                var child = node.children.get(first);
                if (child == null) {
                    var leaf = new BuilderNode<>(prefix, prefixIndex, prefix.length(), node);
                    leaf.entries = entries;
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

                var split = new BuilderNode<>(child.edgeSource, child.edgeStart, child.edgeStart + common, node);
                node.children.put(first, split);

                child.edgeStart += common;
                child.parent = split;
                split.children.put(child.edgeSource.charAt(child.edgeStart), child);

                prefixIndex += common;
                if (prefixIndex == prefix.length()) {
                    split.entries = entries;
                } else {
                    var leaf = new BuilderNode<>(prefix, prefixIndex, prefix.length(), split);
                    leaf.entries = entries;
                    split.children.put(prefix.charAt(prefixIndex), leaf);
                }
                return;
            }
            node.entries = entries;
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
            var nodes = new ArrayList<BuilderNode<T>>();
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
            List<PathTemplateHolder<T>>[] entries = (List<PathTemplateHolder<T>>[]) new List<?>[size];
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
                    childNodes[childIndex] = child.id;
                    childIndex++;
                }
                entries[i] = source.entries;
                var parent = source.parent;
                while (parent != null && parent.entries == null) {
                    parent = parent.parent;
                }
                fallback[i] = parent == null ? -1 : parent.id;
            }
            return new PrefixRadix<>(edgeSource, edgeStart, edgeLength, childOffset, childCount,
                childChars, childNodes, entries, fallback);
        }
    }

    private static final class BuilderNode<T> {
        private final String edgeSource;
        private int edgeStart;
        private final int edgeEnd;
        private BuilderNode<T> parent;
        private final TreeMap<Character, BuilderNode<T>> children = new TreeMap<>();
        private @Nullable List<PathTemplateHolder<T>> entries;
        private int id;

        private BuilderNode(String edgeSource, int edgeStart, int edgeEnd, BuilderNode<T> parent) {
            this.edgeSource = edgeSource;
            this.edgeStart = edgeStart;
            this.edgeEnd = edgeEnd;
            this.parent = parent;
        }
    }
}
