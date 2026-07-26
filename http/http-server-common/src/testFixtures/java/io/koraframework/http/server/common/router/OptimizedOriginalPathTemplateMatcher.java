package io.koraframework.http.server.common.router;

import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Matcher using an exact-path map and an uncompressed character prefix trie.
 *
 * <h2>Build algorithm</h2>
 * <ol>
 *   <li>Group registrations by static stem and preserve semantic priority inside each group with
 *       a {@link TreeSet}.</li>
 *   <li>Move exact routes into a hash map for constant-time lookup.</li>
 *   <li>Insert every dynamic stem into a character trie: one trie node is created for each stem
 *       character. Terminal nodes store the ordered candidate list.</li>
 *   <li>For every terminal, precompute a fallback link to the nearest terminal ancestor. This
 *       represents the next shorter matching stem.</li>
 *   <li>Flatten builder nodes into arrays and publish them with the exact map as one volatile
 *       lookup snapshot.</li>
 * </ol>
 *
 * <h2>Lookup algorithm</h2>
 * <ol>
 *   <li>Check the exact-path map.</li>
 *   <li>Walk the request path one character at a time through the trie without creating prefix
 *       substrings, remembering the deepest terminal reached.</li>
 *   <li>Test candidates at that terminal in priority order. The trie already matched their base,
 *       so template matching uses the {@code baseMatched} fast path.</li>
 *   <li>If the group fails, follow the precomputed fallback link and repeat until a route matches
 *       or no terminal remains.</li>
 * </ol>
 *
 * <h2>Performance characteristics</h2>
 * <p>Exact lookup is expected constant time. Dynamic stem lookup is linear in matched stem
 * characters, while collisions inside one stem still require a linear candidate scan. Misses
 * create no prefix substrings. The single-parameter template path delays capture allocation until
 * a complete match succeeds.</p>
 *
 * @param <T> matched value type
 */
class OptimizedOriginalPathTemplateMatcher<T> {

    private static final Map<String, String> EMPTY_PARAMETERS = Map.of();

    /**
     * Mutable registration state. Request matching only reads {@link #lookupState}.
     */
    private final Map<String, Set<PathTemplateHolder<T>>> pathTemplateMap = new HashMap<>();

    private volatile LookupState<T> lookupState = LookupState.empty();

    /**
     * The result of a path template match.
     *
     * @author Stuart Douglas
     */
    public record PathTemplateMatch<T>(String matchedTemplate, Map<String, String> parameters, T value) {}

    @Nullable
    public PathTemplateMatch<T> match(final String path) {
        final String normalizedPath = path.isEmpty() ? "/" : path;
        final var state = this.lookupState;

        var exact = state.exactPaths.get(normalizedPath);
        if (exact != null) {
            return result(exact, EMPTY_PARAMETERS);
        }

        var trie = state.prefixTrie;
        int node = 0;
        int candidate = -1;
        for (int i = 0; i < normalizedPath.length(); i++) {
            node = trie.child(node, normalizedPath.charAt(i));
            if (node < 0) {
                break;
            }
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

    private PathTemplateMatch<T> result(PathTemplateHolder<T> holder, Map<String, String> parameters) {
        return new PathTemplateMatch<>(holder.template.templateString(), parameters, holder.value);
    }

    @Nullable
    private PathTemplateMatch<T> handleStemMatch(final List<PathTemplateHolder<T>> entries, final String path) {
        LinkedHashMap<String, String> parameters = null;
        for (var holder : entries) {
            if (holder.template.parameterCount() == 1) {
                var singleParameter = holder.template.matchSingleParameter(path, true);
                if (singleParameter != null) {
                    return result(holder, singleParameter);
                }
                continue;
            }
            if (parameters == null) {
                parameters = new LinkedHashMap<>(mapCapacity(holder.template.parameterCount()));
            }
            if (holder.template.matches(path, parameters, true)) {
                return result(holder, parameters);
            }
            parameters.clear();
        }
        return null;
    }

    private static int mapCapacity(int size) {
        return size < 3 ? size + 1 : (int) (size / 0.75f) + 1;
    }

    /**
     * @return previous value associated with path template, or null if there was none
     */
    public synchronized Map.@Nullable Entry<OptimizedOriginalPathTemplate, T> add(final OptimizedOriginalPathTemplate template, final T value) {
        return this.add(template, value, true);
    }

    private Map.@Nullable Entry<OptimizedOriginalPathTemplate, T> add(final OptimizedOriginalPathTemplate template, final T value, boolean rebuildLookupState) {
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

    private static String trimBase(OptimizedOriginalPathTemplate template) {
        var wildcardPrefix = template.wildcardPrefix();
        return wildcardPrefix == null ? template.base() : wildcardPrefix;
    }

    private void buildLookupState() {
        var exactPaths = new HashMap<String, PathTemplateHolder<T>>(this.pathTemplateMap.size());
        var prefixTrie = new PrefixTrieBuilder<T>();
        for (var entry : this.pathTemplateMap.entrySet()) {
            ArrayList<PathTemplateHolder<T>> dynamic = null;
            for (var holder : entry.getValue()) {
                if (holder.template.exact()) {
                    exactPaths.put(holder.template.templateString(), holder);
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
        this.lookupState = new LookupState<>(Map.copyOf(exactPaths), prefixTrie.build());
    }

    public synchronized Map.@Nullable Entry<OptimizedOriginalPathTemplate, T> add(final String pathTemplate, final T value) {
        return this.add(OptimizedOriginalPathTemplate.create(pathTemplate), value, true);
    }

    public synchronized OptimizedOriginalPathTemplateMatcher<T> addAll(OptimizedOriginalPathTemplateMatcher<T> pathTemplateMatcher) {
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

    public Set<OptimizedOriginalPathTemplate> getPathTemplates() {
        var templates = new HashSet<OptimizedOriginalPathTemplate>();
        for (var holders : this.pathTemplateMap.values()) {
            for (var holder : holders) {
                templates.add(holder.template);
            }
        }
        return templates;
    }

    public synchronized OptimizedOriginalPathTemplateMatcher<T> remove(final String pathTemplate) {
        return this.remove(OptimizedOriginalPathTemplate.create(pathTemplate));
    }

    private OptimizedOriginalPathTemplateMatcher<T> remove(OptimizedOriginalPathTemplate template) {
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
        var pathTemplate = OptimizedOriginalPathTemplate.create(template);
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

    private static final class PathTemplateHolder<T> implements Comparable<PathTemplateHolder<T>> {
        private final T value;
        private final OptimizedOriginalPathTemplate template;

        private PathTemplateHolder(T value, OptimizedOriginalPathTemplate template) {
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

    private record LookupState<T>(Map<String, PathTemplateHolder<T>> exactPaths, PrefixTrie<T> prefixTrie) {
        private static <T> LookupState<T> empty() {
            return new LookupState<>(Map.of(), PrefixTrie.empty());
        }
    }

    /**
     * Immutable character trie. Flat arrays keep request traversal allocation-free.
     */
    private static final class PrefixTrie<T> {
        private final char[][] childChars;
        private final int[][] childNodes;
        private final List<PathTemplateHolder<T>>[] entries;
        private final int[] fallback;

        private PrefixTrie(char[][] childChars,
                           int[][] childNodes,
                           List<PathTemplateHolder<T>>[] entries,
                           int[] fallback) {
            this.childChars = childChars;
            this.childNodes = childNodes;
            this.entries = entries;
            this.fallback = fallback;
        }

        private int child(int node, char value) {
            var chars = this.childChars[node];
            if (chars.length <= 4) {
                for (int i = 0; i < chars.length; i++) {
                    if (chars[i] == value) {
                        return this.childNodes[node][i];
                    }
                }
                return -1;
            }

            int low = 0;
            int high = chars.length - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                char current = chars[middle];
                if (current < value) {
                    low = middle + 1;
                } else if (current > value) {
                    high = middle - 1;
                } else {
                    return this.childNodes[node][middle];
                }
            }
            return -1;
        }

        private static <T> PrefixTrie<T> empty() {
            @SuppressWarnings("unchecked")
            List<PathTemplateHolder<T>>[] entries = (List<PathTemplateHolder<T>>[]) new List<?>[]{null};
            return new PrefixTrie<>(new char[][]{new char[0]}, new int[][]{new int[0]}, entries, new int[]{-1});
        }
    }

    private static final class PrefixTrieBuilder<T> {
        private final ArrayList<BuilderNode<T>> nodes = new ArrayList<>();

        private PrefixTrieBuilder() {
            this.nodes.add(new BuilderNode<>(-1));
        }

        private void put(String prefix, List<PathTemplateHolder<T>> entries) {
            int node = 0;
            for (int i = 0; i < prefix.length(); i++) {
                var current = this.nodes.get(node);
                var child = current.children.get(prefix.charAt(i));
                if (child == null) {
                    child = this.nodes.size();
                    current.children.put(prefix.charAt(i), child);
                    this.nodes.add(new BuilderNode<>(node));
                }
                node = child;
            }
            this.nodes.get(node).entries = entries;
        }

        private PrefixTrie<T> build() {
            int size = this.nodes.size();
            var childChars = new char[size][];
            var childNodes = new int[size][];
            @SuppressWarnings("unchecked")
            List<PathTemplateHolder<T>>[] entries = (List<PathTemplateHolder<T>>[]) new List<?>[size];
            var fallback = new int[size];

            for (int i = 0; i < size; i++) {
                var source = this.nodes.get(i);
                int childCount = source.children.size();
                childChars[i] = new char[childCount];
                childNodes[i] = new int[childCount];
                int childIndex = 0;
                for (var child : source.children.entrySet()) {
                    childChars[i][childIndex] = child.getKey();
                    childNodes[i][childIndex] = child.getValue();
                    childIndex++;
                }
                entries[i] = source.entries;
                int parent = source.parent;
                while (parent >= 0 && this.nodes.get(parent).entries == null) {
                    parent = this.nodes.get(parent).parent;
                }
                fallback[i] = parent;
            }
            return new PrefixTrie<>(childChars, childNodes, entries, fallback);
        }
    }

    private static final class BuilderNode<T> {
        private final int parent;
        private final TreeMap<Character, Integer> children = new TreeMap<>();
        private @Nullable List<PathTemplateHolder<T>> entries;

        private BuilderNode(int parent) {
            this.parent = parent;
        }
    }
}
