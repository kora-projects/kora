package ru.tinkoff.kora.openapi.generator;

import io.swagger.v3.oas.models.OpenAPI;

import java.util.*;

public class SecurityData {
    // securitySchema = defined in /components/securitySchemes
    // securityRequirement = Set<Map<String, Set<String>>> in operation/security


    public Map<Set<Map<String, Set<String>>>, String> interceptorTagBySecurityRequirement = new HashMap<>();
    public Map<String, Set<Map<String, Set<String>>>> securityRequirementByOperation = new HashMap<>();

    public Map<Set<String>, String> principalExtractorTagBySecurityRequirementNames = new HashMap<>();

    public void fromOpenapi(OpenAPI openAPI) {
        if (openAPI.getPaths() != null) {
            for (var pathname : openAPI.getPaths().keySet()) {
                var path = openAPI.getPaths().get(pathname);
                if (path.readOperations() == null) {
                    continue;
                }
                for (var operation : path.readOperations()) {
                    if (operation.getSecurity() != null) {
                        var normalizedOperationSchema = new LinkedHashSet<Map<String, Set<String>>>();
                        for (var securityRequirement : operation.getSecurity()) {
                            var normalized = normalizeSecurityRequirement(securityRequirement);
                            normalizedOperationSchema.add(normalized);
                        }
                        securityRequirementByOperation.put(operation.getOperationId(), normalizedOperationSchema);
                    } else if (openAPI.getSecurity() != null) {
                        var normalizedOperationSchema = new LinkedHashSet<Map<String, Set<String>>>();
                        for (var securityRequirement : openAPI.getSecurity()) {
                            var normalized = normalizeSecurityRequirement(securityRequirement);
                            normalizedOperationSchema.add(normalized);
                        }
                        securityRequirementByOperation.put(operation.getOperationId(), normalizedOperationSchema);
                    }
                }
            }
        }
        for (var requirement : securityRequirementByOperation.values()) {
            if (!requirement.isEmpty()) {
                interceptorTagBySecurityRequirement.putIfAbsent(requirement, "OperationSecuritySchemaTag" + interceptorTagBySecurityRequirement.size());
            }
        }
        for (var securitySchema : securityRequirementByOperation.values()) {
            for (var requirement : securitySchema) {
                principalExtractorTagBySecurityRequirementNames.putIfAbsent(new LinkedHashSet<>(requirement.keySet()), "SecurityRequirementTag" + principalExtractorTagBySecurityRequirementNames.size());
            }
        }
    }

    private static Map<String, Set<String>> normalizeSecurityRequirement(Map<String, List<String>> schema) {
        var setSchema = new HashMap<String, Set<String>>();
        for (var entry : schema.entrySet()) {
            setSchema.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return setSchema;
    }

}
