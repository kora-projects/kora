package io.koraframework.database.annotation.processor.mongo;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.TypeSpec;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.FieldFactory;
import io.koraframework.database.annotation.processor.DbUtils;
import io.koraframework.database.annotation.processor.RepositoryGenerator;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Comparator;
import java.util.List;

public class MongoRepositoryGenerator implements RepositoryGenerator {

    static final String EXECUTOR_FIELD = "_mongoExecutor";

    private final Types types;
    private final Elements elements;
    private final MongoOperationGenerator operations;

    public MongoRepositoryGenerator(ProcessingEnvironment processingEnv) {
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
        this.operations = new MongoOperationGenerator(this.types, this.elements);
    }

    @Override
    public ClassName repositoryInterface() {
        return MongoTypes.REPOSITORY;
    }

    @Override
    public TypeSpec generate(TypeElement repositoryElement, TypeSpec.Builder type, MethodSpec.Builder constructor) {
        var repositoryType = (DeclaredType) repositoryElement.asType();
        this.enrichWithExecutor(repositoryElement, type, constructor);
        var codecs = new FieldFactory(this.types, this.elements, type, constructor, "_codec_");
        var registries = new MongoCodecRegistries(type, constructor);

        int number = 1;
        for (var method : this.findOperationMethods(repositoryElement)) {
            var methodType = (ExecutableType) this.types.asMemberOf(repositoryType, method);
            type.addMethod(this.generateMethod(repositoryElement, type, method, methodType, number++, codecs, registries));
        }
        return type.addMethod(constructor.build()).build();
    }

    private MethodSpec generateMethod(TypeElement repositoryElement,
                                      TypeSpec.Builder type,
                                      ExecutableElement method,
                                      ExecutableType methodType,
                                      int number,
                                      FieldFactory codecs,
                                      MongoCodecRegistries registries) {
        var operation = MongoOperation.parse(method);
        var parameters = new MongoParameters(this.types, method, methodType, codecs);
        var context = new MongoOperationGenerator.Context(repositoryElement, method, methodType, operation, parameters, codecs, registries);

        var body = CodeBlock.builder();
        body.addStatement("_observation.observeConnection()");
        var description = this.operations.generate(body, context);

        var queryContextField = "QUERY_CONTEXT_" + number;
        type.addField(FieldSpec.builder(DbUtils.QUERY_CONTEXT, queryContextField, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("""
                new $T(
                      $S,
                      $S,
                      $S
                    )""", DbUtils.QUERY_CONTEXT, description, description, DbUtils.operationName(method))
            .build());

        var mb = DbUtils.queryMethodBuilder(method, methodType);
        mb.addStatement("var _query = $L", queryContextField);
        mb.addStatement("var _observation = this.$N.telemetry().observe(_query)", EXECUTOR_FIELD);

        var isVoid = methodType.getReturnType().getKind() == TypeKind.VOID;
        if (!isVoid) {
            mb.addCode("return ");
        }
        CommonUtils.observe(mb, "_observation", isVoid ? "run" : "call", b -> b.add(body.build()));
        mb.addCode(";\n");
        return mb.build();
    }

    private void enrichWithExecutor(TypeElement repositoryElement, TypeSpec.Builder builder, MethodSpec.Builder constructorBuilder) {
        builder.addField(MongoTypes.EXECUTOR, EXECUTOR_FIELD, Modifier.PRIVATE, Modifier.FINAL);
        builder.addSuperinterface(MongoTypes.REPOSITORY);
        builder.addMethod(MethodSpec.methodBuilder("executor")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Override.class)
            .returns(MongoTypes.EXECUTOR)
            .addCode("return this.$N;", EXECUTOR_FIELD)
            .build());

        var executorTag = DbUtils.getTag(repositoryElement);
        if (executorTag != null) {
            constructorBuilder.addParameter(ParameterSpec.builder(MongoTypes.EXECUTOR, EXECUTOR_FIELD).addAnnotation(executorTag).build());
        } else {
            constructorBuilder.addParameter(MongoTypes.EXECUTOR, EXECUTOR_FIELD);
        }
        constructorBuilder.addStatement("this.$N = $N", EXECUTOR_FIELD, EXECUTOR_FIELD);
    }

    /**
     * Methods are sorted so that generated field numbering stays stable between compilations.
     */
    private List<ExecutableElement> findOperationMethods(TypeElement repositoryElement) {
        return DbUtils.collectInterfaces(this.types, repositoryElement).stream()
            .filter(t -> !t.getQualifiedName().contentEquals(MongoTypes.REPOSITORY.canonicalName()))
            .flatMap(t -> t.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
                .filter(e -> !e.getModifiers().contains(Modifier.PRIVATE))
                .filter(e -> e.getModifiers().contains(Modifier.ABSTRACT)))
            .map(ExecutableElement.class::cast)
            .sorted(Comparator.comparing(e -> e.getSimpleName().toString() + e))
            .toList();
    }
}
