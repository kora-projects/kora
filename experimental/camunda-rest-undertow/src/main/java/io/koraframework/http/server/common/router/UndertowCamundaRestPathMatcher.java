package io.koraframework.http.server.common.router;

import org.jspecify.annotations.Nullable;

import java.util.*;

public final class UndertowCamundaRestPathMatcher {

    private final Map<String, HybridPathTemplateMatcher<String>> pathTemplateMatcher;

    public record HttpMethodPath(String method, String routeTemplate) {}

    public UndertowCamundaRestPathMatcher(List<HttpMethodPath> methods) {
        var matcherBuilders = new HashMap<String, HybridPathTemplateMatcher.Builder<String>>();
        for (var h : methods) {
            var route = h.routeTemplate();
            var methodMatcherBuilder = matcherBuilders.computeIfAbsent(
                h.method().toUpperCase(Locale.ROOT),
                _ -> HybridPathTemplateMatcher.builder()
            );
            var oldValue = methodMatcherBuilder.add(route, route);
            if (oldValue != null) {
                throw new IllegalStateException("Can't add path template %s, matcher already contains an equivalent pattern %s".formatted(route, oldValue.getKey().templateString()));
            }
        }

        var pathTemplateMatchers = new HashMap<String, HybridPathTemplateMatcher<String>>(matcherBuilders.size());
        for (var entry : matcherBuilders.entrySet()) {
            pathTemplateMatchers.put(entry.getKey(), entry.getValue().build());
        }
        this.pathTemplateMatcher = Map.copyOf(pathTemplateMatchers);
    }

    public record Match(String method, String pathTemplate, Map<String, String> pathParameters) {}

    @Nullable
    public Match getMatch(String method, String path) {
        var methodMatchers = pathTemplateMatcher.get(method);
        var pathTemplateMatch = methodMatchers == null ? null : methodMatchers.match(path);
        if (pathTemplateMatch == null) {
            return null;
        }
        return new Match(method, pathTemplateMatch.matchedTemplate(), pathTemplateMatch.parameters());
    }
}
