package ru.tinkoff.kora.database.annotation.processor;

import com.palantir.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraRepositoryGenerator;
import ru.tinkoff.kora.database.annotation.processor.jdbc.JdbcRepositoryGenerator;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.util.List;

import static ru.tinkoff.kora.annotation.processor.common.TagUtils.makeAnnotationSpec;
import static ru.tinkoff.kora.annotation.processor.common.TagUtils.parseTagValue;

public class RepositoryBuilder {

    private final List<RepositoryGenerator> queryMethodGenerators;

    public RepositoryBuilder(ProcessingEnvironment processingEnv) {
        this.queryMethodGenerators = List.of(
            new JdbcRepositoryGenerator(processingEnv),
            new CassandraRepositoryGenerator(processingEnv)
        );
    }

    @Nullable
    public TypeSpec build(TypeElement repositoryElement) throws ProcessingErrorException {
        var name = NameUtils.generatedType(repositoryElement, "Impl");
        var builder = CommonUtils.extendsKeepAop(repositoryElement, name)
            .addAnnotation(AnnotationUtils.generated(RepositoryAnnotationProcessor.class));

        if (AnnotationUtils.findAnnotation(repositoryElement, CommonClassNames.root) != null) {
            builder.addAnnotation(CommonClassNames.root);
        }
        if (AnnotationUtils.findAnnotation(repositoryElement, CommonClassNames.component) != null) {
            builder.addAnnotation(CommonClassNames.component);
        }

        var tags = parseTagValue(repositoryElement);
        if (!tags.isEmpty()) {
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
        throw new ProcessingErrorException("Element doesn't extend any of known repository interfaces", repositoryElement);
    }

    private void enrichConstructorFromParentClass(MethodSpec.Builder constructorBuilder, TypeElement repositoryElement) {
        constructorBuilder.addCode("super(");
        var constructors = CommonUtils.findConstructors(repositoryElement, m -> !m.contains(Modifier.PRIVATE));
        if (constructors.isEmpty()) {
            constructorBuilder.addCode(");\n");
            return;
        }
        if (constructors.size() > 1) {
            throw new ProcessingErrorException("Abstract repository class has more than one public constructor", repositoryElement);
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
