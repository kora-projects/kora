package io.koraframework.database.annotation.processor.mongo;

import com.palantir.javapoet.CodeBlock;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import javax.lang.model.element.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A BSON document or pipeline written in an operation annotation, with {@code :name} placeholders for method
 * parameters. The template is parsed at compile time and emitted as literal document construction, so nothing is
 * parsed at runtime and a malformed template fails the build.
 */
public final class BsonTemplate {

    private static final String MARKER_PREFIX = "__kora_placeholder_";
    private static final String MARKER_SUFFIX = "__";

    private final BsonValue root;
    private final List<String> parameters;

    private BsonTemplate(BsonValue root, List<String> parameters) {
        this.root = root;
        this.parameters = parameters;
    }

    /**
     * @return parameter names referenced by the template, in order of appearance
     */
    public List<String> parameters() {
        return this.parameters;
    }

    /**
     * @return the parsed template, for compile-time inspection of a document the generator also emits
     * @throws org.bson.BsonInvalidOperationException if this template was built by {@link #parseArray}, whose root is not a document
     */
    public BsonDocument document() {
        return this.root.asDocument();
    }

    public static BsonTemplate parseDocument(String template, Element element, String attribute) {
        var replaced = replacePlaceholders(template);
        try {
            return new BsonTemplate(BsonDocument.parse(replaced.json()), replaced.parameters());
        } catch (RuntimeException e) {
            throw new ProcessingErrorException(parseError(template, attribute, "document", e), element);
        }
    }

    public static BsonTemplate parseArray(String template, Element element, String attribute) {
        var replaced = replacePlaceholders(template);
        try {
            return new BsonTemplate(BsonArray.parse(replaced.json()), replaced.parameters());
        } catch (RuntimeException e) {
            throw new ProcessingErrorException(parseError(template, attribute, "array", e), element);
        }
    }

    /**
     * @param parameterResolver produces the expression a placeholder is replaced with, by parameter name
     */
    public CodeBlock toCodeBlock(Function<String, CodeBlock> parameterResolver) {
        return this.build(this.root, parameterResolver);
    }

    private CodeBlock build(BsonValue value, Function<String, CodeBlock> resolver) {
        return switch (value.getBsonType()) {
            case DOCUMENT -> this.buildDocument(value.asDocument(), resolver);
            case ARRAY -> this.buildArray(value.asArray(), resolver);
            case STRING -> {
                var string = value.asString().getValue();
                var index = markerIndex(string);
                yield index < 0
                    ? CodeBlock.of("new $T($S)", MongoTypes.BSON_STRING, string)
                    : resolver.apply(this.parameters.get(index));
            }
            case INT32 -> CodeBlock.of("new $T($L)", MongoTypes.BSON_INT32, value.asInt32().getValue());
            case INT64 -> CodeBlock.of("new $T($LL)", MongoTypes.BSON_INT64, value.asInt64().getValue());
            case DOUBLE -> CodeBlock.of("new $T($LD)", MongoTypes.BSON_DOUBLE, value.asDouble().getValue());
            case BOOLEAN -> CodeBlock.of("$T.valueOf($L)", MongoTypes.BSON_BOOLEAN, value.asBoolean().getValue());
            case NULL -> CodeBlock.of("$T.VALUE", MongoTypes.BSON_NULL);
            case DATE_TIME -> CodeBlock.of("new $T($LL)", MongoTypes.BSON_DATE_TIME, value.asDateTime().getValue());
            case OBJECT_ID -> CodeBlock.of("new $T(new $T($S))", MongoTypes.BSON_OBJECT_ID, MongoTypes.OBJECT_ID, value.asObjectId().getValue().toHexString());
            case DECIMAL128 -> CodeBlock.of("new $T($T.parse($S))", MongoTypes.BSON_DECIMAL128, MongoTypes.DECIMAL_128, value.asDecimal128().getValue().toString());
            case REGULAR_EXPRESSION -> CodeBlock.of("new $T($S, $S)", MongoTypes.BSON_REGULAR_EXPRESSION,
                value.asRegularExpression().getPattern(), value.asRegularExpression().getOptions());
            default -> throw new IllegalStateException("Kora internal error: unsupported BSON type in a template: " + value.getBsonType());
        };
    }

    /**
     * Emits an aggregation pipeline as {@code List<Bson>}, which is what the driver's {@code aggregate} expects.
     */
    public CodeBlock toPipelineCodeBlock(Function<String, CodeBlock> parameterResolver, Element element) {
        var stages = this.root.asArray();
        if (stages.isEmpty()) {
            return CodeBlock.of("$T.<$T>of()", List.class, MongoTypes.BSON);
        }
        var elements = CodeBlock.builder();
        for (int i = 0; i < stages.size(); i++) {
            var stage = stages.get(i);
            if (!stage.isDocument()) {
                throw new ProcessingErrorException("""
                    Mongo aggregation pipeline is invalid:
                      stage %d is a %s

                    Problem:
                      Every aggregation stage must be a document.

                    Hint:
                      A pipeline looks like [{"$match": {...}}, {"$group": {...}}].

                    Fix:
                      Wrap the stage into a document, or remove it from the pipeline.
                    """.formatted(i, stage.getBsonType()), element);
            }
            if (i > 0) {
                elements.add(",\n");
            }
            elements.add(this.build(stage, parameterResolver));
        }
        return CodeBlock.of("$T.<$T>of($>\n$L$<)", List.class, MongoTypes.BSON, elements.build());
    }

    private CodeBlock buildDocument(BsonDocument document, Function<String, CodeBlock> resolver) {
        if (document.isEmpty()) {
            return CodeBlock.of("new $T()", MongoTypes.BSON_DOCUMENT);
        }
        var b = CodeBlock.builder().add("new $T()", MongoTypes.BSON_DOCUMENT).indent().indent();
        for (var entry : document.entrySet()) {
            b.add("\n.append($S, $L)", entry.getKey(), this.build(entry.getValue(), resolver));
        }
        return b.unindent().unindent().build();
    }

    private CodeBlock buildArray(BsonArray array, Function<String, CodeBlock> resolver) {
        if (array.isEmpty()) {
            return CodeBlock.of("new $T()", MongoTypes.BSON_ARRAY);
        }
        var elements = CodeBlock.builder();
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) {
                elements.add(", ");
            }
            elements.add(this.build(array.get(i), resolver));
        }
        return CodeBlock.of("new $T($T.<$T>of($L))", MongoTypes.BSON_ARRAY, List.class, MongoTypes.BSON_VALUE, elements.build());
    }

    private static int markerIndex(String value) {
        if (!value.startsWith(MARKER_PREFIX) || !value.endsWith(MARKER_SUFFIX)) {
            return -1;
        }
        var digits = value.substring(MARKER_PREFIX.length(), value.length() - MARKER_SUFFIX.length());
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private record Replaced(String json, List<String> parameters) {}

    /**
     * Swaps every {@code :name} placeholder for a marker string, so that what is left is valid JSON the BSON parser
     * can read. Placeholders are only recognised in a value position, which keeps the {@code key:value} separator and
     * anything inside a string literal untouched.
     */
    private static Replaced replacePlaceholders(String template) {
        var json = new StringBuilder(template.length());
        var parameters = new ArrayList<String>();
        var inString = false;

        for (int i = 0; i < template.length(); i++) {
            var c = template.charAt(i);
            if (inString) {
                json.append(c);
                if (c == '\\' && i + 1 < template.length()) {
                    json.append(template.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                json.append(c);
                continue;
            }
            if (c == ':' && i + 1 < template.length() && isIdentifierStart(template.charAt(i + 1)) && isValuePosition(json)) {
                var end = i + 1;
                while (end < template.length() && isPathPart(template, end)) {
                    end++;
                }
                json.append('"').append(MARKER_PREFIX).append(parameters.size()).append(MARKER_SUFFIX).append('"');
                parameters.add(template.substring(i + 1, end));
                i = end - 1;
                continue;
            }
            json.append(c);
        }
        return new Replaced(json.toString(), parameters);
    }

    /**
     * A value can only start right after a {@code key:} separator, a {@code ,} separator or an opening {@code [}.
     * Anything else before a colon means the colon is itself the separator, as in an unquoted {@code {key:true}}.
     */
    private static boolean isValuePosition(StringBuilder json) {
        for (int i = json.length() - 1; i >= 0; i--) {
            var c = json.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == ':' || c == ',' || c == '[';
        }
        return true;
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * A placeholder may walk into an entity parameter, as in {@code :user.address.city}. A dot only continues the path
     * when a field name follows it, so a placeholder that ends a value keeps the surrounding JSON intact.
     */
    private static boolean isPathPart(String template, int index) {
        var c = template.charAt(index);
        if (isIdentifierPart(c)) {
            return true;
        }
        return c == '.' && index + 1 < template.length() && isIdentifierStart(template.charAt(index + 1));
    }

    private static String parseError(String template, String attribute, String expected, RuntimeException cause) {
        return """
            Mongo query template is invalid:
              %s

            Problem:
              The '%s' attribute is not a valid BSON %s: %s

            Hint:
              A template is JSON where a method parameter is referenced as ':name' in place of a whole value,
              for example {"login": :login}. A placeholder can not be used inside a string or as a field name.

            Fix:
              Correct the JSON syntax of the template.
            """.formatted(template, attribute, expected, cause.getMessage());
    }
}
