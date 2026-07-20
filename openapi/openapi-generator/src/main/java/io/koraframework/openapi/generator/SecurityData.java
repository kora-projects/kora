package io.koraframework.openapi.generator;

import io.swagger.v3.oas.models.OpenAPI;

import java.util.*;

public class SecurityData {
    // securitySchema = defined in /components/securitySchemes
    // securityRequirement = Set<Map<String, Set<String>>> in operation/security


    public Map<Set<Map<String, Set<String>>>, String> interceptorTagBySecurityRequirement = new LinkedHashMap<>();
    public Map<String, Set<Map<String, Set<String>>>> securityRequirementByOperation = new LinkedHashMap<>();

    public Map<Set<String>, String> principalExtractorTagBySecurityRequirementNames = new LinkedHashMap<>();

    public void fromOpenapi(OpenAPI openAPI, boolean useSecurityDeclarationOrder, CodegenParams.SecurityRequirementMode securityRequirementMode) {
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
                            addSecurityRequirement(normalizedOperationSchema, normalized, useSecurityDeclarationOrder, securityRequirementMode);
                        }
                        securityRequirementByOperation.put(operation.getOperationId(), normalizedOperationSchema);
                    } else if (openAPI.getSecurity() != null) {
                        var normalizedOperationSchema = newSecurityRequirementsSet(useSecurityDeclarationOrder);
                        for (var securityRequirement : openAPI.getSecurity()) {
                            var normalized = normalizeSecurityRequirement(securityRequirement, useSecurityDeclarationOrder);
                            addSecurityRequirement(normalizedOperationSchema, normalized, useSecurityDeclarationOrder, securityRequirementMode);
                        }
                        securityRequirementByOperation.put(operation.getOperationId(), normalizedOperationSchema);
                    }
                }
            }
        }
        for (var requirement : securityRequirementByOperation.values()) {
            if (hasNonAnonymousRequirements(requirement)) {
                interceptorTagBySecurityRequirement.putIfAbsent(requirement, "OperationSecuritySchemaTag" + interceptorTagBySecurityRequirement.size());
            }
        }
        for (var securitySchema : securityRequirementByOperation.values()) {
            for (var requirement : securitySchema) {
                if (requirement.isEmpty()) {
                    continue;
                }
                principalExtractorTagBySecurityRequirementNames.putIfAbsent(newSecurityNamesSet(requirement.keySet(), useSecurityDeclarationOrder), "SecurityRequirementTag" + principalExtractorTagBySecurityRequirementNames.size());
            }
        }
    }

    private static void addSecurityRequirement(
        Set<Map<String, Set<String>>> requirements,
        Map<String, Set<String>> requirement,
        boolean useSecurityDeclarationOrder,
        CodegenParams.SecurityRequirementMode securityRequirementMode
    ) {
        if (securityRequirementMode == CodegenParams.SecurityRequirementMode.STANDARD || requirement.size() <= 1) {
            requirements.add(requirement);
            return;
        }
        for (var entry : requirement.entrySet()) {
            var singleRequirement = useSecurityDeclarationOrder
                ? new OrderedMap<String, Set<String>>()
                : new TreeMap<String, Set<String>>();
            singleRequirement.put(entry.getKey(), entry.getValue());
            requirements.add(singleRequirement);
        }
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
