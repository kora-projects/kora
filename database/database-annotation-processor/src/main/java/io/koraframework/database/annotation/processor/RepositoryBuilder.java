package io.koraframework.database.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.database.annotation.processor.cassandra.CassandraRepositoryGenerator;
import io.koraframework.database.annotation.processor.jdbc.JdbcRepositoryGenerator;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import java.util.List;

import static io.koraframework.annotation.processor.common.TagUtils.makeAnnotationSpec;
import static io.koraframework.annotation.processor.common.TagUtils.parseTagValue;

public class RepositoryBuilder {

    private final Elements elements;
    private final List<RepositoryGenerator> queryMethodGenerators;

    public RepositoryBuilder(Elements elements, ProcessingEnvironment processingEnv) {
        this.elements = elements;
        this.queryMethodGenerators = List.of(
            new JdbcRepositoryGenerator(processingEnv),
            new CassandraRepositoryGenerator(processingEnv)
        );
    }

    @Nullable
    public TypeSpec build(TypeElement repositoryElement) throws ProcessingErrorException {
        var name = NameUtils.generatedType(repositoryElement, "Impl");
        var builder = CommonUtils.extendsKeepAop(elements, repositoryElement, name)
            .addAnnotation(AnnotationUtils.generated(RepositoryAnnotationProcessor.class));

        if (AnnotationUtils.findAnnotation(repositoryElement, CommonClassNames.root) != null) {
            builder.addAnnotation(CommonClassNames.root);
        }
        if (AnnotationUtils.findAnnotation(repositoryElement, CommonClassNames.component) != null) {
            builder.addAnnotation(CommonClassNames.component);
        }

        var tags = parseTagValue(repositoryElement);
        if (tags != null) {
            builder.addAnnotation(makeAnnotationSpec(tags));
        }

        var constructorBuilder = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        if (repositoryElement.getKind().isClass()) {
            this.enrichConstructorFromParentClass(constructorBuilder, repositoryElement);
        }
        var repositoryType = (DeclaredType) repositoryElement.asType();
        for (var availableGenerator : this.queryMethodGenerators) {
            var repositoryInterface = availableGenerator.repositoryInterface();
            var repositoryInterfaceType = TypeUtils.findSupertype(repositoryType, repositoryInterface);

            if (repositoryInterfaceType != null) {
                return availableGenerator.generate(repositoryElement, builder, constructorBuilder);
            }
        }
        throw new ProcessingErrorException("""
            Repository type is invalid:
              %s

            Problem:
              @Repository type doesn't extend any supported repository interface.

            Hint:
              Kora chooses the repository generator by a known base interface, for example JDBC or Cassandra repository contract.

            Fix:
              Extend one of the supported repository interfaces, or remove @Repository from this type.
            """.formatted(repositoryElement.getQualifiedName()), repositoryElement);
    }

    private void enrichConstructorFromParentClass(MethodSpec.Builder constructorBuilder, TypeElement repositoryElement) {
        constructorBuilder.addCode("super(");
        var constructors = CommonUtils.findConstructors(repositoryElement, m -> !m.contains(Modifier.PRIVATE));
        if (constructors.isEmpty()) {
            constructorBuilder.addCode(");\n");
            return;
        }
        if (constructors.size() > 1) {
            throw new ProcessingErrorException("""
                Repository class has ambiguous constructors:
                  %s

                Problem:
                  Abstract repository class has more than one non-private constructor.

                Hint:
                  Generated repository implementation must call exactly one parent constructor.

                Fix:
                  Keep a single non-private constructor, or make extra constructors private.
                """.formatted(repositoryElement.getQualifiedName()), repositoryElement);
        }
        var constructor = constructors.get(0);
        var parameters = constructor.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            var parameter = parameters.get(i);
            var constructorParameter = ParameterSpec.builder(TypeName.get(parameter.asType()), parameter.getSimpleName().toString());
            for (var annotation : parameter.getAnnotationMirrors()) {
                constructorParameter.addAnnotation(AnnotationSpec.get(annotation));
            }
            constructorBuilder.addParameter(constructorParameter.build());
            constructorBuilder.addCode("$L", parameter);
            if (i < parameters.size() - 1) {
                constructorBuilder.addCode(", ");
            }
        }
        constructorBuilder.addCode(");\n");
    }

}
