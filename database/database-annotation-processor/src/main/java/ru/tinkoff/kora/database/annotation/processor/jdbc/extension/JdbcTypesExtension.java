package ru.tinkoff.kora.database.annotation.processor.jdbc.extension;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.GenericTypeResolver;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.database.annotation.processor.jdbc.JdbcTypes;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// JdbcRowMapper<T>
// JdbcResultSetMapper<T>
// JdbcResultSetMapper<List<T>>
public class JdbcTypesExtension implements KoraExtension {
    private static final ClassName LIST_CLASS_NAME = ClassName.get(List.class);

    private final Types types;
    private final Elements elements;

    public JdbcTypesExtension(ProcessingEnvironment env) {
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, @Nullable String tag) {
        if (tag != null) {
            return null;
        }
        if (!(typeMirror instanceof DeclaredType declaredType)) {
            return null;
        }
        var typeName = TypeName.get(typeMirror);
        if (!(typeName instanceof ParameterizedTypeName ptn)) {
            return null;
        }
        if (Objects.equals(ptn.rawType(), JdbcTypes.ROW_MAPPER)) {
            var rowTypeMirror = declaredType.getTypeArguments().get(0);
            var rowTypeElement = (TypeElement) ((DeclaredType) rowTypeMirror).asElement();
            if (AnnotationUtils.isAnnotationPresent(rowTypeElement, JdbcTypes.JDBC_ENTITY)) {
                return KoraExtensionDependencyGenerator.generatedFrom(elements, rowTypeElement, JdbcTypes.ROW_MAPPER);
            }
            return null;
        }

        if (Objects.equals(ptn.rawType(), JdbcTypes.RESULT_SET_MAPPER)) {
            var resultTypeName = ptn.typeArguments().get(0);
            var resultTypeMirror = declaredType.getTypeArguments().get(0);
            if (resultTypeName instanceof ParameterizedTypeName rptn && rptn.rawType().equals(LIST_CLASS_NAME) && resultTypeMirror instanceof DeclaredType resultDeclaredType) {
                var rowTypeMirror = resultDeclaredType.getTypeArguments().get(0);
                var rowTypeElement = (TypeElement) types.asElement(rowTypeMirror);
                if (AnnotationUtils.isAnnotationPresent(rowTypeElement, JdbcTypes.JDBC_ENTITY)) {
                    return KoraExtensionDependencyGenerator.generatedFromWithName(elements, rowTypeElement, NameUtils.generatedType(rowTypeElement, "ListJdbcResultSetMapper"));
                }
                return () -> {
                    var listResultSetMapper = this.elements.getTypeElement(JdbcTypes.RESULT_SET_MAPPER.canonicalName()).getEnclosedElements()
                        .stream()
                        .filter(e -> e.getKind() == ElementKind.METHOD && e.getModifiers().contains(Modifier.STATIC))
                        .map(ExecutableElement.class::cast)
                        .filter(m -> m.getSimpleName().contentEquals("listResultSetMapper"))
                        .findFirst()
                        .orElseThrow();
                    var tp = (TypeVariable) listResultSetMapper.getTypeParameters().get(0).asType();
                    var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, rowTypeMirror), listResultSetMapper.asType());
                    return ExtensionResult.fromExecutable(listResultSetMapper, executableType);
                };
            } else {
                var resultTypeElement = (TypeElement) types.asElement(resultTypeMirror);
                if (AnnotationUtils.isAnnotationPresent(resultTypeElement, JdbcTypes.JDBC_ENTITY)) {
                    return KoraExtensionDependencyGenerator.generatedFrom(elements, resultTypeElement, JdbcTypes.RESULT_SET_MAPPER);
                }

                return () -> {
                    var singleResultSetMapper = this.elements.getTypeElement(JdbcTypes.RESULT_SET_MAPPER.canonicalName()).getEnclosedElements()
                        .stream()
                        .filter(e -> e.getKind() == ElementKind.METHOD && e.getModifiers().contains(Modifier.STATIC))
                        .map(ExecutableElement.class::cast)
                        .filter(m -> m.getSimpleName().contentEquals("singleResultSetMapper"))
                        .findFirst()
                        .orElseThrow();
                    var tp = (TypeVariable) singleResultSetMapper.getTypeParameters().get(0).asType();
                    var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, resultTypeMirror), singleResultSetMapper.asType());
                    return ExtensionResult.fromExecutable(singleResultSetMapper, executableType);
                };
            }
        }
        return null;
    }
}
