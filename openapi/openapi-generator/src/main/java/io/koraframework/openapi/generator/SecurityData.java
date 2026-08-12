package io.koraframework.openapi.generator;

import io.swagger.v3.oas.models.OpenAPI;

import java.util.*;
import java.util.function.UnaryOperator;

public class SecurityData {
    // securitySchema = defined in /components/securitySchemes
    // securityRequirement = Set<Map<String, Set<String>>> in operation/security


    public Map<Set<Map<String, Set<String>>>, String> interceptorTagBySecurityRequirement = new LinkedHashMap<>();
    public Map<String, Set<Map<String, Set<String>>>> securityRequirementByOperation = new LinkedHashMap<>();

    public Map<Set<String>, String> principalExtractorTagBySecurityRequirementNames = new LinkedHashMap<>();
    public Map<String, String> tagBySecuritySchemeName = new LinkedHashMap<>();

    public void fromOpenapi(OpenAPI openAPI, boolean useSecurityDeclarationOrder, UnaryOperator<String> securityTagNameMapper) {
        if (openAPI.getComponents() != null && openAPI.getComponents().getSecuritySchemes() != null) {
            for (var securitySchemeName : openAPI.getComponents().getSecuritySchemes().keySet()) {
                tagBySecuritySchemeName.put(securitySchemeName, securityTagNameMapper.apply(securitySchemeName));
            }
        }
        if (openAPI.getPaths() != null) {
            for (var pathname : openAPI.getPaths().keySet()) {
                var path = openAPI.getPaths().get(pathname);
                if (path.readOperations() == null) {
                    continue;
                }
                for (var operation : path.readOperations()) {
                    if (operation.getSecurity() != null) {
                        var normalizedOperationSchema = newSecurityRequirementsSet(useSecurityDeclarationOrder);
                        for (var securityRequirement : operation.getSecurity()) {
                            var normalized = normalizeSecurityRequirement(securityRequirement, useSecurityDeclarationOrder);
                            normalizedOperationSchema.add(normalized);
                        }
                        securityRequirementByOperation.put(operation.getOperationId(), normalizedOperationSchema);
                    } else if (openAPI.getSecurity() != null) {
                        var normalizedOperationSchema = newSecurityRequirementsSet(useSecurityDeclarationOrder);
                        for (var securityRequirement : openAPI.getSecurity()) {
                            var normalized = normalizeSecurityRequirement(securityRequirement, useSecurityDeclarationOrder);
                            normalizedOperationSchema.add(normalized);
                        }
                        securityRequirementByOperation.put(operation.getOperationId(), normalizedOperationSchema);
                    }
                }
            }
        }
        var requirementsByBaseTag = new LinkedHashMap<String, List<Set<Map<String, Set<String>>>>>();
        for (var requirement : securityRequirementByOperation.values()) {
            if (hasNonAnonymousRequirements(requirement)
                && requirementsByBaseTag.values().stream().flatMap(Collection::stream).noneMatch(requirement::equals)) {
                requirementsByBaseTag.computeIfAbsent(operationSecurityTag(requirement), _ -> new ArrayList<>()).add(requirement);
            }
        }
        for (var entry : requirementsByBaseTag.entrySet()) {
            var baseTag = entry.getKey();
            var requirements = entry.getValue();
            if (requirements.size() == 1) {
                interceptorTagBySecurityRequirement.put(requirements.getFirst(), baseTag);
                continue;
            }
            var tags = new LinkedHashMap<Set<Map<String, Set<String>>>, String>();
            for (var requirement : requirements) {
                tags.put(requirement, baseTag + "_" + operationSecurityScopesTag(requirement, securityTagNameMapper, false));
            }
            var duplicateTags = tags.values().stream()
                .filter(tag -> Collections.frequency(tags.values(), tag) > 1)
                .collect(java.util.stream.Collectors.toSet());
            for (var requirement : requirements) {
                var tag = tags.get(requirement);
                if (duplicateTags.contains(tag)) {
                    tag = baseTag + "_" + operationSecurityScopesTag(requirement, securityTagNameMapper, true);
                }
                interceptorTagBySecurityRequirement.put(requirement, tag);
            }
        }
        for (var securitySchema : securityRequirementByOperation.values()) {
            for (var requirement : securitySchema) {
                if (requirement.isEmpty()) {
                    continue;
                }
                var securityNames = newSecurityNamesSet(requirement.keySet(), useSecurityDeclarationOrder);
                principalExtractorTagBySecurityRequirementNames.putIfAbsent(securityNames, securityNames.stream()
                    .map(this::tagForSecurityScheme)
                    .collect(java.util.stream.Collectors.joining("With")));
            }
        }
    }

    public String tagForSecurityScheme(String securitySchemeName) {
        return tagBySecuritySchemeName.getOrDefault(securitySchemeName, securitySchemeName);
    }

    private String operationSecurityTag(Set<Map<String, Set<String>>> requirements) {
        return requirements.stream()
            .map(requirement -> requirement.isEmpty()
                ? "Anonymous"
                : requirement.keySet().stream()
                    .map(this::tagForSecurityScheme)
                    .collect(java.util.stream.Collectors.joining("And")))
            .distinct()
            .collect(java.util.stream.Collectors.joining("_"));
    }

    private String operationSecurityScopesTag(Set<Map<String, Set<String>>> requirements, UnaryOperator<String> securityTagNameMapper, boolean includeSchemeNames) {
        var scopedEntries = requirements.stream()
            .flatMap(requirement -> requirement.entrySet().stream())
            .filter(entry -> !entry.getValue().isEmpty())
            .toList();
        if (scopedEntries.isEmpty()) {
            return "NoScopes";
        }
        return scopedEntries.stream()
            .map(entry -> (includeSchemeNames ? tagForSecurityScheme(entry.getKey()) : "") + entry.getValue().stream()
                .map(securityTagNameMapper)
                .collect(java.util.stream.Collectors.joining("And")))
            .collect(java.util.stream.Collectors.joining("_"));
    }

    public static boolean hasNonAnonymousRequirements(Set<? extends Map<String, ? extends Set<String>>> requirements) {
        if (requirements == null) {
            return false;
        }
        return requirements.stream().anyMatch(requirement -> !requirement.isEmpty());
    }

    public static boolean hasAnonymousRequirement(Collection<? extends Map<String, ? extends Set<String>>> requirements) {
        if (requirements == null) {
            return false;
        }
        return requirements.stream().anyMatch(Map::isEmpty);
    }

    private static Set<Map<String, Set<String>>> newSecurityRequirementsSet(boolean useSecurityDeclarationOrder) {
        return useSecurityDeclarationOrder
            ? new OrderedSet<>()
            : new LinkedHashSet<>();
    }

    private static Set<String> newSecurityNamesSet(Collection<String> names, boolean useSecurityDeclarationOrder) {
        return useSecurityDeclarationOrder
            ? new OrderedSet<>(names)
            : new LinkedHashSet<>(names);
    }

    private static Map<String, Set<String>> normalizeSecurityRequirement(Map<String, List<String>> schema, boolean useSecurityDeclarationOrder) {
        var setSchema = useSecurityDeclarationOrder
            ? new OrderedMap<String, Set<String>>()
            : new TreeMap<String, Set<String>>();
        for (var entry : schema.entrySet()) {
            var scopes = useSecurityDeclarationOrder
                ? new LinkedHashSet<>(entry.getValue())
                : new TreeSet<>(entry.getValue());
            setSchema.put(entry.getKey(), scopes);
        }
        return setSchema;
    }

    private static final class OrderedMap<K, V> extends LinkedHashMap<K, V> {
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Map<?, ?> other) || size() != other.size()) {
                return false;
            }
            var thisIterator = entrySet().iterator();
            var otherIterator = other.entrySet().iterator();
            while (thisIterator.hasNext() && otherIterator.hasNext()) {
                if (!Objects.equals(thisIterator.next(), otherIterator.next())) {
                    return false;
                }
            }
            return !thisIterator.hasNext() && !otherIterator.hasNext();
        }

        @Override
        public int hashCode() {
            return orderedHash(entrySet());
        }
    }

    private static final class OrderedSet<E> extends LinkedHashSet<E> {
        private OrderedSet() {}

        private OrderedSet(Collection<? extends E> values) {
            super(values);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Set<?> other) || size() != other.size()) {
                return false;
            }
            var thisIterator = iterator();
            var otherIterator = other.iterator();
            while (thisIterator.hasNext() && otherIterator.hasNext()) {
                if (!Objects.equals(thisIterator.next(), otherIterator.next())) {
                    return false;
                }
            }
            return !thisIterator.hasNext() && !otherIterator.hasNext();
        }

        @Override
        public int hashCode() {
            return orderedHash(this);
        }
    }

    private static int orderedHash(Collection<?> values) {
        var result = 1;
        for (var value : values) {
            result = 31 * result + Objects.hashCode(value);
        }
        return result;
    }

}
