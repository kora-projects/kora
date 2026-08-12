package io.koraframework.openapi.generator;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityDataTest {
    @Test
    void omitsScopesFromUniqueOperationSecurityTag() {
        var security = securityData(operation("read", requirement("oAuth", "read_pets")));

        assertEquals(List.of("OAuth"), security.interceptorTagBySecurityRequirement.values().stream().toList());
    }

    @Test
    void appendsScopesWhenOperationSecurityTagsCollide() {
        var security = securityData(
            operation("read", requirement("oAuth", "read_pets")),
            operation("write", requirement("oAuth", "write_pets", "read_pets")),
            operation("unscoped", requirement("oAuth"))
        );

        assertEquals(
            List.of("OAuth_ReadPets", "OAuth_ReadPetsAndWritePets", "OAuth_NoScopes"),
            security.interceptorTagBySecurityRequirement.values().stream().toList()
        );
    }

    @Test
    void distinguishesAnonymousFallbackFromRequiredSecurity() {
        var required = requirement("bearerAuth");
        var optional = new Operation().operationId("optional").security(List.of(required, new SecurityRequirement()));
        var security = securityData(operation("required", required), optional);

        assertEquals(
            List.of("BearerAuth", "BearerAuth_Anonymous"),
            security.interceptorTagBySecurityRequirement.values().stream().toList()
        );
    }

    private static SecurityData securityData(Operation... operations) {
        var paths = new Paths();
        for (var operation : operations) {
            paths.addPathItem("/" + operation.getOperationId(), new PathItem().get(operation));
        }
        var openApi = new OpenAPI()
            .components(new Components()
                .addSecuritySchemes("oAuth", new SecurityScheme().type(SecurityScheme.Type.OAUTH2))
                .addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")))
            .paths(paths);
        var security = new SecurityData();
        security.fromOpenapi(openApi, false, SecurityDataTest::tagName);
        return security;
    }

    private static Operation operation(String operationId, SecurityRequirement requirement) {
        return new Operation().operationId(operationId).security(List.of(requirement));
    }

    private static SecurityRequirement requirement(String name, String... scopes) {
        return new SecurityRequirement().addList(name, List.of(scopes));
    }

    private static String tagName(String value) {
        var result = new StringBuilder();
        var upperCaseNext = true;
        for (var character : value.toCharArray()) {
            if (!Character.isLetterOrDigit(character)) {
                upperCaseNext = true;
            } else if (upperCaseNext) {
                result.append(Character.toUpperCase(character));
                upperCaseNext = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }
}
