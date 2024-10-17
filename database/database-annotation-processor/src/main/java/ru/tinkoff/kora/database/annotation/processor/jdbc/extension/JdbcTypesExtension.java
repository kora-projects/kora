package ru.tinkoff.kora.database.annotation.processor.jdbc.extension;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.GenericTypeResolver;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;
import ru.tinkoff.kora.database.annotation.processor.jdbc.JdbcEntityGenerator;
import ru.tinkoff.kora.database.annotation.processor.jdbc.JdbcTypes;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.Messager;
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
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// JdbcRowMapper<T>
// JdbcResultSetMapper<T>
// JdbcResultSetMapper<List<T>>
public class JdbcTypesExtension implements KoraExtension {
    private static final ClassName LIST_CLASS_NAME = ClassName.get(List.class);

    private final Types types;
    private final Elements elements;
    private final JdbcEntityGenerator generator;
    private final Messager messager;

    public JdbcTypesExtension(ProcessingEnvironment env) {
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
        this.messager = env.getMessager();
        this.generator = new JdbcEntityGenerator(types, elements, env.getFiler());
    }

    private KoraExtensionDependencyGenerator fromAnnotationProcessor(ClassName mapperName) {
        var maybeGenerated = this.elements.getTypeElement(mapperName.canonicalName());
        if (maybeGenerated != null) {
            var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
            if (constructors.size() != 1) throw new IllegalStateException();
            return () -> ExtensionResult.fromExecutable(constructors.get(0));
        } else {
            return ExtensionResult::nextRound;
        }
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, Set<String> tags) {
        if (!tags.isEmpty()) {
            return null;
        }
        if (!(typeMirror instanceof DeclaredType declaredType)) {
            return null;
        }
        var typeName = TypeName.get(typeMirror);
        if (!(typeName instanceof ParameterizedTypeName ptn)) {
            return null;
        }
        if (Objects.equals(ptn.rawType, JdbcTypes.ROW_MAPPER)) {
            var rowTypeMirror = declaredType.getTypeArguments().get(0);
            var rowTypeElement = (TypeElement) ((DeclaredType) rowTypeMirror).asElement();
            if (AnnotationUtils.isAnnotationPresent(rowTypeElement, JdbcTypes.JDBC_ENTITY)) {
                return fromAnnotationProcessor(generator.rowMapperName(rowTypeElement));
            }

            var entity = DbEntity.parseEntity(this.types, rowTypeMirror);
            if (entity != null) {
                return this.entityRowMapper(entity);
            }
            return null;
        }

        if (Objects.equals(ptn.rawType, JdbcTypes.RESULT_SET_MAPPER)) {
            var resultTypeName = ptn.typeArguments.get(0);
            var resultTypeMirror = declaredType.getTypeArguments().get(0);
            if (resultTypeName instanceof ParameterizedTypeName rptn && rptn.rawType.equals(LIST_CLASS_NAME) && resultTypeMirror instanceof DeclaredType resultDeclaredType) {
                var rowTypeMirror = resultDeclaredType.getTypeArguments().get(0);
                var rowTypeElement = (TypeElement) types.asElement(rowTypeMirror);
                if (AnnotationUtils.isAnnotationPresent(rowTypeElement, JdbcTypes.JDBC_ENTITY)) {
                    return fromAnnotationProcessor(generator.listJdbcResultSetMapperName(rowTypeElement));
                }
                var entity = DbEntity.parseEntity(this.types, rowTypeMirror);
                if (entity != null) {
                    return this.entityResultListSetMapper(entity);
                } else {
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
                }
            } else {
                var resultTypeElement = (TypeElement) types.asElement(resultTypeMirror);
                if (AnnotationUtils.isAnnotationPresent(resultTypeElement, JdbcTypes.JDBC_ENTITY)) {
                    return fromAnnotationProcessor(generator.resultSetMapperName(resultTypeElement));
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

    private KoraExtension.KoraExtensionDependencyGenerator entityRowMapper(DbEntity entity) {
        return () -> {
            var mapperName = NameUtils.generatedType(entity.typeElement(), JdbcTypes.ROW_MAPPER);
            var packageElement = this.elements.getPackageOf(entity.typeElement());
            var maybeGenerated = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + mapperName);
            if (maybeGenerated != null) {
                var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            }
            this.messager.printMessage(
                Diagnostic.Kind.WARNING,
                "Type is not annotated with @JdbcEntity, but mapper %s is requested by graph. Generating one in graph building process will lead to another round of compiling which will slow down you build",
                entity.typeElement()
            );
            this.generator.generateRowMapper(entity);
            return ExtensionResult.nextRound();
        };
    }

    private KoraExtension.KoraExtensionDependencyGenerator entityResultListSetMapper(DbEntity entity) {
        return () -> {
            var mapperName = NameUtils.generatedType(entity.typeElement(), "ListJdbcResultSetMapper");
            var packageElement = this.elements.getPackageOf(entity.typeElement());
            var maybeGenerated = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + mapperName);
            if (maybeGenerated != null) {
                var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            }
            this.messager.printMessage(
                Diagnostic.Kind.WARNING,
                "Type is not annotated with @JdbcEntity, but mapper %s is requested by graph. Generating one in graph building process will lead to another round of compiling which will slow down you build",
                entity.typeElement()
            );
            this.generator.generateListResultSetMapper(entity);
            return ExtensionResult.nextRound();
        };
    }
}
