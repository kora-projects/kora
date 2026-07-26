package io.koraframework.http.server.common.router;


import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Original matcher based on a map of static stems and a descending list of stem lengths.
 *
 * <h2>Build algorithm</h2>
 * <ol>
 *   <li>Group registrations by their static stem. Templates in each group are kept in a
 *       {@link TreeSet}, whose order is the semantic route priority defined by
 *       {@link OriginalPathTemplate#compareTo(OriginalPathTemplate)}.</li>
 *   <li>On every mutation, publish an immutable-style lookup snapshot containing copied candidate
 *       lists and the distinct stem lengths sorted from longest to shortest.</li>
 * </ol>
 *
 * <h2>Lookup algorithm</h2>
 * <ol>
 *   <li>Normalize an empty request path to {@code /}.</li>
 *   <li>For every registered stem length, longest first, create the corresponding path prefix
 *       with {@link String#substring(int, int)} and look it up in the stem map.</li>
 *   <li>When a stem is found, test its candidates in priority order. The first successful
 *       {@link OriginalPathTemplate#matches(String, Map)} result wins.</li>
 *   <li>If all candidates fail, continue with the next shorter stem, which implements wildcard
 *       and less-specific-route fallback.</li>
 * </ol>
 *
 * <h2>Performance characteristics</h2>
 * <p>Lookup cost is proportional to the number of distinct stem lengths plus the number of
 * candidates in matching stem groups. Prefix substrings are allocated during lookup, and parameter
 * substrings may be allocated by candidates that eventually fail. Registration rebuilds the
 * snapshot but is not part of the request hot path.</p>
 *
 * @param <T> matched value type
 */
class OriginalPathTemplateMatcher<T> {
    private static final Map<String, String> EMPTY_PARAMETERS = Map.of();

    /**
     * Map of path template stem to the path templates that share the same base.
     */
    private final Map<String, Set<PathTemplateHolder>> pathTemplateMap = new HashMap<>();

    private volatile LookupState lookupState = new LookupState(Map.of(), new int[0]);

    /**
     * The result of a path template match.
     *
     * @author Stuart Douglas
     */
    public record PathTemplateMatch<T>(String matchedTemplate, Map<String, String> parameters, T value) {}

    @Nullable
    public PathTemplateMatch<T> match(final String path) {
        String normalizedPath = "".equals(path) ? "/" : path;
        int length = normalizedPath.length();
        var lookupState = this.lookupState;
        var pathTemplateMap = lookupState.pathTemplateMap;
        var lengths = lookupState.lengths;
        for (int pathLength : lengths) {
            if (pathLength == length) {
                var entry = pathTemplateMap.get(normalizedPath);
                if (entry != null) {
                    var res = handleStemMatch(entry, normalizedPath);
                    if (res != null) {
                        return res;
                    }
                }
            } else if (pathLength < length) {
                var part = normalizedPath.substring(0, pathLength);
                var entry = pathTemplateMap.get(part);
                if (entry != null) {
                    var res = handleStemMatch(entry, normalizedPath);
                    if (res != null) {
                        return res;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private PathTemplateMatch<T> handleStemMatch(final List<PathTemplateHolder> entry, final String path) {
        Map<String, String> params = null;
        for (var val : entry) {
            var templateParams = val.hasParameters
                ? (params == null ? params = new LinkedHashMap<>() : params)
                : EMPTY_PARAMETERS;
            if (val.template.matches(path, templateParams)) {
                return new PathTemplateMatch<>(val.template.templateString(), templateParams, val.value);
            } else {
                if (params != null) {
                    params.clear();
                }
            }
        }
        return null;
    }

    /**
     * @return the previous value associated with path template, or null if there was none
     */
    public Map.@Nullable Entry<OriginalPathTemplate, T> add(final OriginalPathTemplate template, final T value) {
        return add(template, value, true);
    }

    private Map.@Nullable Entry<OriginalPathTemplate, T> add(final OriginalPathTemplate template, final T value, boolean rebuildLookupState) {
        var base = trimBase(template);
        var values = pathTemplateMap.get(base);
        Set<PathTemplateHolder> newValues;
        if (values == null) {
            newValues = new TreeSet<>();
        } else {
            newValues = new TreeSet<>(values);
        }
        var holder = new PathTemplateHolder(value, template);
        if (newValues.contains(holder)) {
            for (var item : newValues) {
                if (item.compareTo(holder) == 0) {
                    return Map.entry(item.template, item.value);
                }
            }
            throw new IllegalStateException();
        }
        newValues.add(holder);
        pathTemplateMap.put(base, newValues);
        if (rebuildLookupState) {
            buildLookupState();
        }
        return null;
    }

    private String trimBase(OriginalPathTemplate template) {
        String retval = template.base();

        if (retval.charAt(retval.length() - 1) == '*') {
            return retval.substring(0, retval.length() - 1);
        }

        return retval;
    }

    private void buildLookupState() {
        final var lengths = new TreeSet<Integer>((o1, o2) -> -o1.compareTo(o2));
        final var lookup = new HashMap<String, List<PathTemplateHolder>>(pathTemplateMap.size());
        for (var entry : pathTemplateMap.entrySet()) {
            lengths.add(entry.getKey().length());
            lookup.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        int[] lengthArray = new int[lengths.size()];
        int pos = 0;
        for (int i : lengths) {
            lengthArray[pos++] = i; //-1 because the base paths end with a /
        }
        this.lookupState = new LookupState(lookup, lengthArray);
    }

    public synchronized Map.Entry<OriginalPathTemplate, T> add(final String pathTemplate, final T value) {
        final OriginalPathTemplate template = OriginalPathTemplate.create(pathTemplate);
        return add(template, value);
    }

    public synchronized OriginalPathTemplateMatcher<T> addAll(OriginalPathTemplateMatcher<T> pathTemplateMatcher) {
        for (var entry : pathTemplateMatcher.getPathTemplateMap().entrySet()) {
            for (var pathTemplateHolder : entry.getValue()) {
                add(pathTemplateHolder.template, pathTemplateHolder.value, false);
            }
        }
        buildLookupState();
        return this;
    }

    Map<String, Set<PathTemplateHolder>> getPathTemplateMap() {
        return pathTemplateMap;
    }

    public Set<OriginalPathTemplate> getPathTemplates() {
        var templates = new HashSet<OriginalPathTemplate>();
        for (var holders : pathTemplateMap.values()) {
            for (var holder : holders) {
                templates.add(holder.template);
            }
        }
        return templates;
    }

    public synchronized OriginalPathTemplateMatcher<T> remove(final String pathTemplate) {
        final OriginalPathTemplate template = OriginalPathTemplate.create(pathTemplate);
        return remove(template);
    }

    private synchronized OriginalPathTemplateMatcher<T> remove(OriginalPathTemplate template) {
        var base = trimBase(template);
        var values = pathTemplateMap.get(base);
        Set<PathTemplateHolder> newValues;
        if (values == null) {
            return this;
        } else {
            newValues = new TreeSet<>(values);
        }
        var it = newValues.iterator();
        while (it.hasNext()) {
            PathTemplateHolder next = it.next();
            if (next.template.templateString().equals(template.templateString())) {
                it.remove();
                break;
            }
        }
        if (newValues.size() == 0) {
            pathTemplateMap.remove(base);
        } else {
            pathTemplateMap.put(base, newValues);
        }
        buildLookupState();
        return this;
    }

    public synchronized T get(String template) {
        var pathTemplate = OriginalPathTemplate.create(template);
        var values = pathTemplateMap.get(trimBase(pathTemplate));
        if (values == null) {
            return null;
        }
        for (var next : values) {
            if (next.template.equals(pathTemplate)) {
                return next.value;
            }
        }
        return null;
    }

    private final class PathTemplateHolder implements Comparable<PathTemplateHolder> {
        final T value;
        final OriginalPathTemplate template;
        final boolean hasParameters;

        private PathTemplateHolder(T value, OriginalPathTemplate template) {
            this.value = value;
            this.template = template;
            this.hasParameters = !template.parameterNames().isEmpty() || template.wildcardPrefix() != null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof OriginalPathTemplateMatcher<?>.PathTemplateHolder that) {
                return template.equals(that.template);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return template.hashCode();
        }

        @Override
        public int compareTo(PathTemplateHolder o) {
            return template.compareTo(o.template);
        }
    }

    private final class LookupState {
        final Map<String, List<PathTemplateHolder>> pathTemplateMap;
        final int[] lengths;

        private LookupState(Map<String, List<PathTemplateHolder>> pathTemplateMap, int[] lengths) {
            this.pathTemplateMap = pathTemplateMap;
            this.lengths = lengths;
        }
    }
}
