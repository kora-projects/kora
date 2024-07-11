package ru.tinkoff.kora.database.annotation.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.common.annotation.Generated;
import ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraRepositoryGenerator;
import ru.tinkoff.kora.database.annotation.processor.jdbc.JdbcRepositoryGenerator;
import ru.tinkoff.kora.database.annotation.processor.r2dbc.R2dbcRepositoryGenerator;
import ru.tinkoff.kora.database.annotation.processor.vertx.VertxRepositoryGenerator;

import jakarta.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.List;

import static ru.tinkoff.kora.annotation.processor.common.TagUtils.makeAnnotationSpec;
import static ru.tinkoff.kora.annotation.processor.common.TagUtils.parseTagValue;

public class RepositoryBuilder {

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
            new R2dbcRepositoryGenerator(this.env)
        );
    }

    @Nullable
    public TypeSpec build(TypeElement repositoryElement) throws ProcessingErrorException, IOException {
        var name = NameUtils.generatedType(repositoryElement, "Impl");
        var builder = CommonUtils.extendsKeepAop(repositoryElement, name)
            .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", CodeBlock.of("$S", RepositoryAnnotationProcessor.class.getCanonicalName())).build())
            .addOriginatingElement(repositoryElement);

        var tags = parseTagValue(repositoryElement);
        if (!tags.isEmpty()) {
            builder.addAnnotation(makeAnnotationSpec(tags));
        }

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
