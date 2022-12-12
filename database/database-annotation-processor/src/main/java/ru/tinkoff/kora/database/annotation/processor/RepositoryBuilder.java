package ru.tinkoff.kora.database.annotation.processor;

import com.squareup.javapoet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.common.annotation.Generated;
import ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraRepositoryGenerator;
import ru.tinkoff.kora.database.annotation.processor.jdbc.JdbcRepositoryGenerator;
import ru.tinkoff.kora.database.annotation.processor.r2dbc.R2DbcRepositoryGenerator;
import ru.tinkoff.kora.database.annotation.processor.vertx.VertxRepositoryGenerator;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.List;

public class RepositoryBuilder {
    private static final Logger log = LoggerFactory.getLogger(RepositoryBuilder.class);

    private final Types types;
    private final ProcessingEnvironment env;
    private final List<RepositoryGenerator> queryMethodGenerators;

    public RepositoryBuilder(ProcessingEnvironment processingEnv) {
        this.env = processingEnv;
        this.types = processingEnv.getTypeUtils();
        this.queryMethodGenerators = List.of(
            new JdbcRepositoryGenerator(this.env),
            new VertxRepositoryGenerator(this.env),
            new CassandraRepositoryGenerator(this.env),
            new R2DbcRepositoryGenerator(this.env)
        );
    }

    @Nullable
    public TypeSpec build(TypeElement repositoryElement) throws ProcessingErrorException, IOException {
        log.info("Generating Repository for {}", repositoryElement);
        var name = CommonUtils.getOuterClassesAsPrefix(repositoryElement) + repositoryElement.getSimpleName().toString() + "_Impl";
        var builder = CommonUtils.extendsKeepAop(repositoryElement, name)
            .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", CodeBlock.of("$S", RepositoryBuilder.class.getCanonicalName())).build())
            .addOriginatingElement(repositoryElement);
        var constructorBuilder = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        if (repositoryElement.getKind().isClass()) {
            this.enrichConstructorFromParentClass(constructorBuilder, repositoryElement);
        }
        for (var availableGenerator : this.queryMethodGenerators) {
            var repositoryInterface = availableGenerator.repositoryInterface();
            if (repositoryInterface != null && this.types.isAssignable(repositoryElement.asType(), repositoryInterface)) {
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
