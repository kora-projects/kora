package ru.tinkoff.kora.database.annotation.processor.cassandra.extension;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraTypes;
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
import java.util.Map;
import java.util.Set;

public class CassandraTypesExtension implements KoraExtension {

    private final Types types;
    private final Elements elements;
    //    private final CassandraEntityGenerator generator;

    public CassandraTypesExtension(ProcessingEnvironment env) {
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, Set<String> tag) {
        if (!tag.isEmpty()) {
            return null;
        }
        var typeName = TypeName.get(typeMirror).withoutAnnotations();
        if (!(typeName instanceof ParameterizedTypeName ptn) || !(typeMirror instanceof DeclaredType dt)) {
            return null;
        }
        if (ptn.rawType.equals(CassandraTypes.RESULT_SET_MAPPER)) {
            return this.generateResultSetMapper(roundEnvironment, dt);
        }
        if (ptn.rawType.equals(CassandraTypes.ASYNC_RESULT_SET_MAPPER)) {
            return this.generateAsyncResultSetMapper(roundEnvironment, dt);
        }
        if (ptn.rawType.equals(CassandraTypes.REACTIVE_RESULT_SET_MAPPER)) {
            return this.generateReactiveResultSetMapper(roundEnvironment, ptn, dt);
        }
        if (ptn.rawType.equals(CassandraTypes.ROW_MAPPER)) {
            return this.generateResultRowMapper(roundEnvironment, dt);
        }
        if (ptn.rawType.equals(CassandraTypes.PARAMETER_COLUMN_MAPPER)) {
            return this.generateParameterColumnMapper(roundEnvironment, dt);
        }
        if (ptn.rawType.equals(CassandraTypes.RESULT_COLUMN_MAPPER)) {
            return this.generateRowColumnMapper(roundEnvironment, dt);
        }
        return null;
    }

    private KoraExtensionDependencyGenerator generateRowColumnMapper(RoundEnvironment roundEnvironment, DeclaredType dt) {
        var entityType = dt.getTypeArguments().get(0);
        var element = (TypeElement) this.types.asElement(entityType);
        if (AnnotationUtils.findAnnotation(element, CassandraTypes.UDT_ANNOTATION) != null) {
            return KoraExtensionDependencyGenerator.generatedFrom(elements, element, CassandraTypes.RESULT_COLUMN_MAPPER);
        }
        if (element.getQualifiedName().contentEquals("java.util.List")) {
            entityType = ((DeclaredType) entityType).getTypeArguments().get(0);
            element = (TypeElement) this.types.asElement(entityType);
            if (AnnotationUtils.findAnnotation(element, CassandraTypes.UDT_ANNOTATION) != null) {
                return KoraExtensionDependencyGenerator.generatedFrom(elements, element, "List_CassandraRowColumnMapper");
            }
        }
        return null;
    }

    private KoraExtensionDependencyGenerator generateParameterColumnMapper(RoundEnvironment roundEnvironment, DeclaredType dt) {
        var entityType = dt.getTypeArguments().get(0);
        var element = (TypeElement) this.types.asElement(entityType);
        if (AnnotationUtils.findAnnotation(element, CassandraTypes.UDT_ANNOTATION) != null) {
            return KoraExtensionDependencyGenerator.generatedFrom(elements, element, CassandraTypes.PARAMETER_COLUMN_MAPPER);
        }
        if (element.getQualifiedName().contentEquals("java.util.List")) {
            entityType = ((DeclaredType) entityType).getTypeArguments().get(0);
            element = (TypeElement) this.types.asElement(entityType);
            if (AnnotationUtils.findAnnotation(element, CassandraTypes.UDT_ANNOTATION) != null) {
                return KoraExtensionDependencyGenerator.generatedFrom(elements, element, "List_CassandraParameterColumnMapper");
            }
        }
        return null;
    }

    @Nullable
    private KoraExtensionDependencyGenerator generateResultRowMapper(RoundEnvironment roundEnvironment, DeclaredType typeMirror) {
        var rowType = typeMirror.getTypeArguments().get(0);
        var rowTypeElement = this.types.asElement(rowType);
        if (AnnotationUtils.isAnnotationPresent(rowTypeElement, CassandraTypes.CASSANDRA_ENTITY)) {
            return KoraExtensionDependencyGenerator.generatedFrom(elements, rowTypeElement, CassandraTypes.ROW_MAPPER);
        }
        return null;
    }

    @Nullable
    private KoraExtensionDependencyGenerator generateResultSetMapper(RoundEnvironment roundEnvironment, DeclaredType typeMirror) {
        //CassandraResultSetMapper<List<T>>
        var resultType = typeMirror.getTypeArguments().get(0);
        if (!(resultType instanceof DeclaredType dt)) {
            return null;
        }
        if (CommonUtils.isList(resultType)) {
            var tn = (ParameterizedTypeName) TypeName.get(resultType);
            var rowType = dt.getTypeArguments().get(0);
            return this.listResultSetMapper(typeMirror, tn, (DeclaredType) rowType);
        }
        var rowTypeElement = this.types.asElement(resultType);
        if (AnnotationUtils.isAnnotationPresent(rowTypeElement, CassandraTypes.CASSANDRA_ENTITY)) {
            return KoraExtensionDependencyGenerator.generatedFrom(elements, rowTypeElement, CassandraTypes.RESULT_SET_MAPPER);
        }
        return () -> {
            var singleResultSetMapper = findStaticMethod(CassandraTypes.RESULT_SET_MAPPER, "singleResultSetMapper");
            var tp = (TypeVariable) singleResultSetMapper.getTypeParameters().get(0).asType();
            var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, resultType), singleResultSetMapper.asType());
            return ExtensionResult.fromExecutable(singleResultSetMapper, executableType);
        };
    }

    @Nullable
    private KoraExtensionDependencyGenerator generateAsyncResultSetMapper(RoundEnvironment roundEnvironment, DeclaredType typeMirror) {
        //CassandraResultSetMapper<List<T>>
        var listType = typeMirror.getTypeArguments().get(0);
        if (!(listType instanceof DeclaredType dt)) {
            return null;
        }
        if (CommonUtils.isList(listType)) {
            return () -> {
                var tn = (ParameterizedTypeName) TypeName.get(listType);
                var rowType = dt.getTypeArguments().get(0);
                var singleResultSetMapper = findStaticMethod(CassandraTypes.ASYNC_RESULT_SET_MAPPER, "list");
                var tp = (TypeVariable) singleResultSetMapper.getTypeParameters().get(0).asType();
                var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, rowType), singleResultSetMapper.asType());
                return ExtensionResult.fromExecutable(singleResultSetMapper, executableType);
            };
        }
        return () -> {
            var singleResultSetMapper = findStaticMethod(CassandraTypes.ASYNC_RESULT_SET_MAPPER, "one");
            var tp = (TypeVariable) singleResultSetMapper.getTypeParameters().get(0).asType();
            var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, listType), singleResultSetMapper.asType());
            return ExtensionResult.fromExecutable(singleResultSetMapper, executableType);
        };
    }

    @Nullable
    private KoraExtensionDependencyGenerator generateReactiveResultSetMapper(RoundEnvironment roundEnvironment, ParameterizedTypeName ptn, DeclaredType typeMirror) {
        if (ptn.typeArguments.size() < 2 || !(ptn.typeArguments.get(1) instanceof ParameterizedTypeName publisherTypeName)) {
            return null;
        }
        if (publisherTypeName.rawType.equals(CommonClassNames.flux)) {
            var fluxMapper = findStaticMethod(CassandraTypes.REACTIVE_RESULT_SET_MAPPER, "flux");
            var rowType = typeMirror.getTypeArguments().get(0);
            var tp = (TypeVariable) fluxMapper.getTypeParameters().get(0).asType();
            var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, rowType), fluxMapper.asType());
            return () -> ExtensionResult.fromExecutable(fluxMapper, executableType);
        }
        if (publisherTypeName.rawType.equals(CommonClassNames.mono)) {
            var monoParam = typeMirror.getTypeArguments().get(0);
            var monoParamTypeName = TypeName.get(monoParam);
            if (monoParam instanceof DeclaredType monoDt && monoParamTypeName instanceof ParameterizedTypeName monoPtn && monoPtn.rawType.equals(CommonClassNames.list)) {
                var rowType = monoDt.getTypeArguments().get(0);
                var monoList = findStaticMethod(CassandraTypes.REACTIVE_RESULT_SET_MAPPER, "monoList");
                var tp = (TypeVariable) monoList.getTypeParameters().get(0).asType();
                var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, rowType), monoList.asType());
                return () -> ExtensionResult.fromExecutable(monoList, executableType);
            }
            if (monoParamTypeName.equals(TypeName.VOID.box())) {
                var monoVoid = findStaticMethod(CassandraTypes.REACTIVE_RESULT_SET_MAPPER, "monoVoid");
                return () -> ExtensionResult.fromExecutable(monoVoid, (ExecutableType) monoVoid.asType());
            }
            var rowType = monoParam;
            var mono = findStaticMethod(CassandraTypes.REACTIVE_RESULT_SET_MAPPER, "mono");
            var tp = (TypeVariable) mono.getTypeParameters().get(0).asType();
            var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, rowType), mono.asType());
            return () -> ExtensionResult.fromExecutable(mono, executableType);
        }
        return null;
    }

    private KoraExtensionDependencyGenerator listResultSetMapper(DeclaredType typeMirror, ParameterizedTypeName listType, DeclaredType rowTypeMirror) {
        var rowTypeElement = this.types.asElement(rowTypeMirror);
        if (AnnotationUtils.isAnnotationPresent(rowTypeElement, CassandraTypes.CASSANDRA_ENTITY)) {
            return KoraExtensionDependencyGenerator.generatedFromWithName(elements, rowTypeElement, NameUtils.generatedType(rowTypeElement, "ListCassandraResultSetMapper"));
        }
        return () -> {
            var listResultSetMapper = findStaticMethod(CassandraTypes.RESULT_SET_MAPPER, "listResultSetMapper");
            var tp = (TypeVariable) listResultSetMapper.getTypeParameters().get(0).asType();
            var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, rowTypeMirror), listResultSetMapper.asType());
            return ExtensionResult.fromExecutable(listResultSetMapper, executableType);
        };
    }

    private ExecutableElement findStaticMethod(ClassName className, String methodName) {
        return this.elements.getTypeElement(className.canonicalName()).getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.METHOD && e.getModifiers().contains(Modifier.STATIC))
            .map(ExecutableElement.class::cast)
            .filter(m -> m.getSimpleName().contentEquals(methodName))
            .findFirst()
            .orElseThrow();
    }
}
