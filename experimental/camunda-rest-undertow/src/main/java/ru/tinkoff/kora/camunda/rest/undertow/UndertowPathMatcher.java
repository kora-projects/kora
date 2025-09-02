package ru.tinkoff.kora.camunda.rest.undertow;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.server.common.router.PathTemplateMatcher;

import java.util.*;

final class UndertowPathMatcher {

    private final Map<String, PathTemplateMatcher<String>> pathTemplateMatcher = new HashMap<>();

    record HttpMethodPath(String method, String routeTemplate) {}

    UndertowPathMatcher(List<HttpMethodPath> methods) {
        final PathTemplateMatcher<List<String>> allMethodMatchers = new PathTemplateMatcher<>();
        for (var h : methods) {
            var route = h.routeTemplate();
            var methodMatchers = pathTemplateMatcher.computeIfAbsent(h.method().toUpperCase(Locale.ROOT), k -> new PathTemplateMatcher<>());
            var oldValue = methodMatchers.add(route, route);
            if (oldValue != null) {
                throw new IllegalStateException("Can't add path template %s, matcher already contains an equivalent pattern %s".formatted(route, oldValue.getKey().templateString()));
            }

            var otherMethods = new ArrayList<>(List.of(h.method()));
            var oldAllMethodValue = allMethodMatchers.add(route, otherMethods);
            if (oldAllMethodValue != null) {
                otherMethods.addAll(oldAllMethodValue.getValue());
            }
        }
    }

    record Match(String method, String pathTemplate, Map<String, String> pathParameters) {}

    @Nullable
    Match getMatch(String method, String path) {
        final Map<String, String> templateParameters;
        final @Nullable String routeTemplate;

        var methodMatchers = pathTemplateMatcher.get(method);
        var pathTemplateMatch = methodMatchers == null ? null : methodMatchers.match(path);
        if (pathTemplateMatch == null) {
            return null;
        } else {
            templateParameters = pathTemplateMatch.parameters();
            routeTemplate = pathTemplateMatch.matchedTemplate();
            return new Match(method, routeTemplate, templateParameters);
        }
    }
}
