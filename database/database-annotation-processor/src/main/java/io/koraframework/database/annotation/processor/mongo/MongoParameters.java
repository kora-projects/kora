package io.koraframework.database.annotation.processor.mongo;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.FieldFactory;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Method parameters of a repository operation, and the expressions that turn them into BSON values inside a template.
 */
public final class MongoParameters {

    public record Parameter(VariableElement element, TypeMirror type) {

        public String name() {
            return this.element.getSimpleName().toString();
        }
    }

    private final Types types;
    private final ExecutableElement method;
    private final FieldFactory codecs;
    private final List<Parameter> parameters;
    private final Set<String> used = new LinkedHashSet<>();

    public MongoParameters(Types types, ExecutableElement method, ExecutableType methodType, FieldFactory codecs) {
        this.types = types;
        this.method = method;
        this.codecs = codecs;

        var declared = method.getParameters();
        var resolved = methodType.getParameterTypes();
        var list = new ArrayList<Parameter>(declared.size());
        for (int i = 0; i < declared.size(); i++) {
            list.add(new Parameter(declared.get(i), resolved.get(i)));
        }
        this.parameters = List.copyOf(list);
    }

    public List<Parameter> all() {
        return this.parameters;
    }

    /**
     * @return a resolver that maps a {@code :name} placeholder to the expression producing its BSON value
     */
    public Function<String, CodeBlock> resolver() {
        return name -> {
            var parameter = this.find(name);
            if (parameter == null) {
                throw new ProcessingErrorException("""
                    Mongo query template is invalid:
                      %s#%s

                    Problem:
                      Template references ':%s', but the method has no such parameter.

                    Hint:
                      A placeholder is matched against method parameter names, so ':login' needs a parameter named 'login'.
                      Compile with '-parameters' so parameter names survive compilation.

                    Fix:
                      Rename the placeholder or the parameter so that they match.
                    """.formatted(this.method.getEnclosingElement().getSimpleName(), this.method.getSimpleName(), name), this.method);
            }
            this.used.add(name);
            return this.bsonValue(parameter);
        };
    }

    public void markUsed(Parameter parameter) {
        this.used.add(parameter.name());
    }

    public void validateAllUsed() {
        var unused = this.parameters.stream()
            .filter(p -> !this.used.contains(p.name()))
            .map(Parameter::name)
            .toList();
        if (!unused.isEmpty()) {
            throw new ProcessingErrorException("""
                Mongo repository method has unused parameters:
                  %s#%s

                Problem:
                  Parameters are never referenced by the operation: %s

                Hint:
                  Every parameter must appear in a template as ':name', or be the entity a write operation stores.

                Fix:
                  Reference the parameter in the template, or remove it from the method.
                """.formatted(this.method.getEnclosingElement().getSimpleName(), this.method.getSimpleName(),
                String.join(", ", unused)), this.method);
        }
    }

    /**
     * @return the only parameter that no template referenced, which a write operation stores as a document
     */
    public Parameter entityParameter(String operation) {
        var candidates = this.parameters.stream()
            .filter(p -> !this.used.contains(p.name()))
            .toList();
        if (candidates.size() != 1) {
            throw new ProcessingErrorException("""
                Mongo repository method is invalid:
                  %s#%s

                Problem:
                  %s needs exactly one parameter holding the document to write, but found %d: %s

                Hint:
                  Parameters referenced by a filter template are query arguments, the remaining one is the document.

                Fix:
                  Keep a single entity parameter, and reference every other parameter from the template.
                """.formatted(this.method.getEnclosingElement().getSimpleName(), this.method.getSimpleName(),
                operation, candidates.size(),
                candidates.isEmpty() ? "none" : candidates.stream().map(Parameter::name).collect(Collectors.joining(", "))), this.method);
        }
        var parameter = candidates.get(0);
        this.used.add(parameter.name());
        return parameter;
    }

    @Nullable
    private Parameter find(String name) {
        for (var parameter : this.parameters) {
            if (parameter.name().equals(name)) {
                return parameter;
            }
        }
        return null;
    }

    public CodeBlock bsonValue(Parameter parameter) {
        var value = this.bsonValueExpression(parameter.type(), CodeBlock.of("$N", parameter.name()), parameter.element(), 0);
        if (CommonUtils.isNullable(parameter.element())) {
            return CodeBlock.of("$N == null ? $T.VALUE : $L", parameter.name(), MongoTypes.BSON_NULL, value);
        }
        return value;
    }

    private CodeBlock bsonValueExpression(TypeMirror type, CodeBlock valueExpr, VariableElement origin, int depth) {
        var mapping = CommonUtils.parseMapping(origin).getMapping(MongoTypes.CODEC);
        if (mapping == null) {
            var nativeType = MongoNativeTypes.find(TypeName.get(type));
            if (nativeType != null) {
                return nativeType.bsonValue().apply(valueExpr);
            }
            if (type instanceof DeclaredType declaredType && declaredType.asElement().getKind() == ElementKind.ENUM) {
                return CodeBlock.of("new $T($L.name())", MongoTypes.BSON_STRING, valueExpr);
            }
            var elementType = this.collectionElementType(type);
            if (elementType != null) {
                var element = "_e" + depth;
                return CodeBlock.of("new $T($L.stream().<$T>map($N -> $L).toList())",
                    MongoTypes.BSON_ARRAY, valueExpr, MongoTypes.BSON_VALUE, element,
                    this.bsonValueExpression(elementType, CodeBlock.of("$N", element), origin, depth + 1));
            }
        }

        var codecField = this.codecs.add(mapping, ParameterizedTypeName.get(MongoTypes.CODEC, TypeName.get(type).box()));
        return CodeBlock.of("$T.encode(this.$N, $L)", MongoTypes.VALUES, codecField, valueExpr);
    }

    @Nullable
    private TypeMirror collectionElementType(TypeMirror type) {
        if (!(type instanceof DeclaredType declaredType)) {
            return null;
        }
        var erasure = this.types.erasure(type).toString();
        if (!erasure.equals(List.class.getCanonicalName())
            && !erasure.equals(Set.class.getCanonicalName())
            && !erasure.equals(java.util.Collection.class.getCanonicalName())) {
            return null;
        }
        var arguments = declaredType.getTypeArguments();
        return arguments.size() == 1
            ? arguments.get(0)
            : null;
    }
}
