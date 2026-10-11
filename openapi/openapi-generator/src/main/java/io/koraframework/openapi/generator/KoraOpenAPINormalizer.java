package io.koraframework.openapi.generator;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.openapitools.codegen.OpenAPINormalizer;
import org.openapitools.codegen.utils.ModelUtils;

import java.util.LinkedHashSet;
import java.util.Map;

/**
 * The default normalizer drops an allOf member that has no type, properties or $ref as a metadata only one and copies
 * its description and example to the schema, but not its `required`. So properties that such a member requires,
 * declared by another member, become optional. This normalizer keeps them required.
 * <p>
 * TODO this is a bug of OpenAPI Generator (OpenAPINormalizer#refactorAllOfWithMetadataOnlySchemas, seen in 7.25.0) that may be fixed
 *  in a later version: check it on a generator upgrade and remove this class with KoraCodegen#useKoraNormalizer when it is.
 */
public class KoraOpenAPINormalizer extends OpenAPINormalizer {

    public KoraOpenAPINormalizer(OpenAPI openAPI, Map<String, String> inputRules) {
        super(openAPI, inputRules);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void refactorAllOfWithMetadataOnlySchemas(Schema schema) {
        var required = new LinkedHashSet<String>();
        if (schema.getAllOf() != null) {
            for (var item : schema.getAllOf()) {
                if (item instanceof Schema member && member.getRequired() != null && ModelUtils.isMetadataOnlySchema(member)) {
                    required.addAll(member.getRequired());
                }
            }
        }
        super.refactorAllOfWithMetadataOnlySchemas(schema);
        for (var name : required) {
            if (schema.getRequired() == null || !schema.getRequired().contains(name)) {
                schema.addRequiredItem(name);
            }
        }
    }
}
