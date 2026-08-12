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

    @Test
    void preservesSeparatorsWhenSecuritySchemeNamesCollide() {
        var paths = new Paths()
            .addPathItem("/dash", new PathItem().get(operation("dash", requirement("api-key"))))
            .addPathItem("/underscore", new PathItem().get(operation("underscore", requirement("api_key"))));
        var openApi = new OpenAPI()
            .components(new Components()
                .addSecuritySchemes("api-key", new SecurityScheme().type(SecurityScheme.Type.APIKEY))
                .addSecuritySchemes("api_key", new SecurityScheme().type(SecurityScheme.Type.APIKEY)))
            .paths(paths);
        var security = new SecurityData();
        security.fromOpenapi(openApi, false, SecurityDataTest::tagName);

        assertEquals("ApiKey", security.tagBySecuritySchemeName.get("api-key"));
        assertEquals("Api_Key", security.tagBySecuritySchemeName.get("api_key"));
        assertEquals(List.of("ApiKey", "Api_Key"), security.interceptorTagBySecurityRequirement.values().stream().toList());
    }

    @Test
    void preservesSeparatorsWhenScopeNamesCollide() {
        var security = securityData(
            operation("dash", requirement("oAuth", "read-pets")),
            operation("underscore", requirement("oAuth", "read_pets"))
        );

        assertEquals("ReadPets", security.tagBySecurityScopeName.get("read-pets"));
        assertEquals("Read_Pets", security.tagBySecurityScopeName.get("read_pets"));
        assertEquals(List.of("OAuth_ReadPets", "OAuth_Read_Pets"), security.interceptorTagBySecurityRequirement.values().stream().toList());
    }

    @Test
    void addsStableSuffixWhenComposedScopeTagsStillCollide() {
        var security = securityData(
            operation("combined", requirement("oAuth", "read", "pets")),
            operation("single", requirement("oAuth", "petsAndRead"))
        );

        assertEquals(List.of("OAuth_OAuthPetsAndRead", "OAuth_OAuthPetsAndRead2"), security.interceptorTagBySecurityRequirement.values().stream().toList());
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
