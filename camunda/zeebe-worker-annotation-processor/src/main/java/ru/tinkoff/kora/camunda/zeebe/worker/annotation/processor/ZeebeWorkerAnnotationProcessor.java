package ru.tinkoff.kora.camunda.zeebe.worker.annotation.processor;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

public final class ZeebeWorkerAnnotationProcessor extends AbstractKoraProcessor {

    private static final ClassName ANNOTATION_WORKER = ClassName.get("ru.tinkoff.kora.camunda.zeebe.worker.annotation", "JobWorker");

    private static final ClassName ANNOTATION_VARIABLE = ClassName.get("ru.tinkoff.kora.camunda.zeebe.worker.annotation", "JobVariable");
    private static final ClassName ANNOTATION_VARIABLES = ClassName.get("ru.tinkoff.kora.camunda.zeebe.worker.annotation", "JobVariables");

    private static final ClassName CLASS_KORA_WORKER = ClassName.get("ru.tinkoff.kora.camunda.zeebe.worker", "KoraJobWorker");
    private static final ClassName CLASS_JOB_CONTEXT = ClassName.get("ru.tinkoff.kora.camunda.zeebe.worker", "JobContext");
    private static final ClassName CLASS_ACTIVE_CONTEXT = ClassName.get("ru.tinkoff.kora.camunda.zeebe.worker", "ActiveJobContext");
    private static final ClassName CLASS_CLIENT = ClassName.get("io.camunda.zeebe.client.api.worker", "JobClient");
    private static final ClassName CLASS_FINAL_COMMAND = ClassName.get("io.camunda.zeebe.client.api.command", "FinalCommandStep");
    private static final ClassName CLASS_ACTIVE_JOB = ClassName.get("io.camunda.zeebe.client.api.response", "ActivatedJob");
    private static final ClassName CLASS_WORKER_EXCEPTION = ClassName.get("ru.tinkoff.kora.camunda.zeebe.worker", "JobWorkerException");
    private static final ClassName CLASS_JSON_READER = ClassName.get("ru.tinkoff.kora.json.common", "JsonReader");
    private static final ClassName CLASS_JSON_WRITER = ClassName.get("ru.tinkoff.kora.json.common", "JsonWriter");
    private static final ClassName CLASS_VARIABLE_READER = ClassName.get("ru.tinkoff.kora.camunda.zeebe.worker", "ZeebeVariableJsonReader");
    private static final ClassName CLASS_WORKER_CONFIG = ClassName.get("ru.tinkoff.kora.camunda.zeebe.worker", "ZeebeWorkerConfig");

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ANNOTATION_WORKER.canonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var annotationWorker = processingEnv.getElementUtils().getTypeElement(ANNOTATION_WORKER.canonicalName());
        if (annotationWorker == null) {
            return false;
        }

        for (var element : roundEnv.getElementsAnnotatedWith(annotationWorker)) {
            var method = (ExecutableElement) element;
            if (method.getModifiers().stream().anyMatch(m -> m.equals(Modifier.PRIVATE))) {
                throw new ProcessingErrorException("@JobWorker method can't be private", method);
            }

            var packageName = getPackage(method);
            var ownerType = getOwner(method);

            final List<Variable> variables = getVariables(method);
            var implSpecBuilder = TypeSpec.classBuilder(NameUtils.generatedType(ownerType, "%s_KoraJobWorker".formatted(method.getSimpleName())))
                .addModifiers(Modifier.FINAL, Modifier.PUBLIC)
                .addAnnotation(AnnotationUtils.generated(ZeebeWorkerAnnotationProcessor.class))
                .addAnnotation(CommonClassNames.component)
                .addSuperinterface(CLASS_KORA_WORKER);

            final MethodSpec methodConstructor = getMethodConstructor(ownerType, method, implSpecBuilder, variables);
            final MethodSpec methodFetchVariables = getMethodFetchVariables(method, variables);
            if (methodFetchVariables != null) {
                implSpecBuilder.addMethod(methodFetchVariables);
            }

            final TypeSpec spec = implSpecBuilder
                .addMethod(methodConstructor)
                .addMethod(getMethodType(method))
                .addMethod(getMethodHandler(ownerType, method, variables))
                .build();
            try {
                var implFile = JavaFile.builder(packageName, spec).build();
                implFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        return false;
    }

    private static String getJobType(ExecutableElement method) {
        var ann = Objects.requireNonNull(AnnotationUtils.findAnnotation(method, ANNOTATION_WORKER));
        return Objects.requireNonNull(AnnotationUtils.<String>parseAnnotationValueWithoutDefault(ann, "value"));
    }

    private MethodSpec getMethodHandler(TypeElement ownerType, ExecutableElement method, List<Variable> variables) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("handle")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(CLASS_CLIENT, "client")
            .addParameter(CLASS_ACTIVE_JOB, "job")
            .returns(ParameterizedTypeName.get(CLASS_FINAL_COMMAND, WildcardTypeName.subtypeOf(Object.class)));

        CodeBlock.Builder codeBuilder = CodeBlock.builder();

        codeBuilder.beginControlFlow("try");
        int varCounter = 1;
        final List<String> vars = new ArrayList<>();
        for (Variable variable : variables) {
            var varName = "var" + (vars.size() + 1);
            vars.add(varName);
            if (variable.isVars) {
                codeBuilder.addStatement("var $L = varsReader.read(job.getVariables())", varName);
            } else if (variable.isContext) {
                codeBuilder.addStatement("var $L = new $T(jobName, job)", varName, CLASS_ACTIVE_CONTEXT);
            } else if (variable.isVar) {
                String varReaderName = "var" + varCounter++ + "Reader";
                codeBuilder.addStatement("var $L = $L.read(job.getVariables())", varName, varReaderName);
            }
        }

        String methodName = method.getSimpleName().toString();
        String varsArg = String.join(", ", vars);
        if (MethodUtils.isVoid(method)) {
            codeBuilder.addStatement("this.handler.$L($L)", methodName, varsArg);
            codeBuilder.addStatement("return client.newCompleteCommand(job)");
        } else {
            if (MethodUtils.isOptional(method)) {
                codeBuilder.addStatement("var result = this.handler.$L($L).orElse(null)", methodName, varsArg);
            } else {
                codeBuilder.addStatement("var result = this.handler.$L($L)", methodName, varsArg);
            }

            codeBuilder.beginControlFlow("if(result != null)");
            codeBuilder.addStatement("return client.newCompleteCommand(job).variables(varsWriter.toStringUnchecked(result))");
            codeBuilder.nextControlFlow("else");
            codeBuilder.addStatement("return client.newCompleteCommand(job)");
            codeBuilder.endControlFlow();
        }

        codeBuilder.nextControlFlow("catch ($T e)", CLASS_WORKER_EXCEPTION);
        codeBuilder.addStatement("throw e");

        if (variables.stream().anyMatch(v -> v.isVars || v.isVar)) {
            codeBuilder.nextControlFlow("catch ($T e)", IOException.class);
            codeBuilder.addStatement("throw new $T($S, e)", CLASS_WORKER_EXCEPTION, "DESERIALIZATION");
        }

        if (!MethodUtils.isVoid(method)) {
            codeBuilder.nextControlFlow("catch ($T e)", UncheckedIOException.class);
            codeBuilder.addStatement("throw new $T($S, e)", CLASS_WORKER_EXCEPTION, "SERIALIZATION");
        }

        codeBuilder.nextControlFlow("catch (Exception e)", CLASS_WORKER_EXCEPTION);
        codeBuilder.addStatement("throw new $T($S, e)", CLASS_WORKER_EXCEPTION, "UNEXPECTED");
        codeBuilder.endControlFlow();

        return methodBuilder
            .addCode(codeBuilder.build())
            .build();
    }

    @Nullable
    private MethodSpec getMethodFetchVariables(ExecutableElement method, List<Variable> variables) {
        if (variables.stream().noneMatch(v -> v.isVar)) {
            return null;
        }

        String varArg = variables.stream()
            .filter(Variable::isVar)
            .map(v -> "\"" + v.name + "\"")
            .collect(Collectors.joining(", "));

        return MethodSpec.methodBuilder("fetchVariables")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(List.class, String.class))
            .addStatement("return $T.of($L)", List.class, varArg)
            .build();
    }

    private MethodSpec getMethodType(ExecutableElement method) {
        var jobType = getJobType(method);

        return MethodSpec.methodBuilder("type")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addStatement("return $S", jobType)
            .build();
    }

    private MethodSpec getMethodConstructor(TypeElement ownerType,
                                            ExecutableElement method,
                                            TypeSpec.Builder implBuilder,
                                            List<Variable> variables) {
        MethodSpec.Builder methodBuilder = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);
        CodeBlock.Builder constructorBuilder = CodeBlock.builder();

        implBuilder.addField(TypeName.get(ownerType.asType()), "handler", Modifier.PRIVATE, Modifier.FINAL);
        methodBuilder.addParameter(TypeName.get(ownerType.asType()), "handler");
        constructorBuilder.addStatement("this.handler = handler");

        if (variables.stream().anyMatch(v -> v.isContext)) {
            implBuilder.addField(String.class, "jobName", Modifier.PRIVATE, Modifier.FINAL);
            methodBuilder.addParameter(CLASS_WORKER_CONFIG, "config");
            constructorBuilder.addStatement("this.jobName = config.getJobConfig($S).name()", getJobType(method));
        }

        if (MethodUtils.isMono(method) || MethodUtils.isFlux(method) || MethodUtils.isFuture(method)) {
            throw new ProcessingErrorException("@JobWorker return type can't be Mono/Flux/CompletionStage", method);
        } else if (!MethodUtils.isVoid(method)) {
            TypeMirror returnType = MethodUtils.isOptional(method)
                ? MethodUtils.getGenericType(method.getReturnType()).orElseThrow(() -> new ProcessingErrorException("Method return Optional<T> type must have type signature", method))
                : method.getReturnType();

            var writerType = ParameterizedTypeName.get(CLASS_JSON_WRITER, TypeName.get(returnType));
            implBuilder.addField(writerType, "varsWriter", Modifier.PRIVATE, Modifier.FINAL);
            methodBuilder.addParameter(writerType, "varsWriter");
            constructorBuilder.addStatement("this.varsWriter = varsWriter");
        }

        variables.stream()
            .filter(v -> v.isVars)
            .findFirst()
            .ifPresent(vars -> {
                var readerType = ParameterizedTypeName.get(CLASS_JSON_READER, TypeName.get(vars.parameter.asType()));
                implBuilder.addField(readerType, "varsReader", Modifier.PRIVATE, Modifier.FINAL);
                methodBuilder.addParameter(readerType, "varsReader");
                constructorBuilder.addStatement("this.varsReader = varsReader");
            });

        int varCounter = 1;
        for (Variable variable : variables) {
            if (variable.isVar) {
                var readerType = ParameterizedTypeName.get(CLASS_JSON_READER, TypeName.get(variable.parameter.asType()));
                var readerName = "var" + varCounter + "Reader";
                implBuilder.addField(readerType, readerName, Modifier.PRIVATE, Modifier.FINAL);
                methodBuilder.addParameter(readerType, readerName);
                boolean isNullable = CommonUtils.isNullable(variable.parameter);
                constructorBuilder.addStatement("this.$L = new $T<>($S, $L, $L)", readerName, CLASS_VARIABLE_READER, variable.name, isNullable, readerName);
                varCounter++;
            }
        }

        return methodBuilder
            .addCode(constructorBuilder.build())
            .build();
    }

    private String getPackage(Element element) {
        return processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
    }

    private static TypeElement getOwner(ExecutableElement method) {
        Element enclosingElement = method.getEnclosingElement();

        while (enclosingElement != null) {
            if (enclosingElement instanceof TypeElement te) {
                return te;
            }

            enclosingElement = enclosingElement.getEnclosingElement();
        }

        throw new ProcessingErrorException("Can't find TypeElement for " + method.getSimpleName(), method);
    }

    private List<Variable> getVariables(ExecutableElement method) {
        final List<Variable> variables = new ArrayList<>();
        boolean haveAlreadyVars = false;
        boolean haveAlreadyContext = false;
        for (VariableElement parameter : method.getParameters()) {
            boolean isVars = AnnotationUtils.findAnnotation(parameter, ANNOTATION_VARIABLES) != null;
            if (isVars) {
                if (haveAlreadyVars) {
                    throw new ProcessingErrorException("One @%s variable only can be specified".formatted(
                        ANNOTATION_VARIABLES.simpleName()), parameter);
                } else {
                    haveAlreadyVars = true;
                }
            }

            boolean isContext = TypeName.get(parameter.asType()).equals(CLASS_JOB_CONTEXT);
            if (isContext) {
                if (haveAlreadyContext) {
                    throw new ProcessingErrorException("One @%s variable only can be specified".formatted(
                        ANNOTATION_VARIABLES.simpleName()), parameter);
                } else {
                    haveAlreadyContext = true;
                }
            }

            var varAnnotation = AnnotationUtils.findAnnotation(parameter, ANNOTATION_VARIABLE);
            boolean isVar = varAnnotation != null;
            if (isVars || isContext || isVar) {
                final String varName = (isVar)
                    ? Optional.ofNullable(((String) AnnotationUtils.parseAnnotationValue(elements, varAnnotation, "value")))
                    .filter(s -> !s.isBlank())
                    .orElse(parameter.getSimpleName().toString())
                    : parameter.getSimpleName().toString();

                variables.add(new Variable(parameter, varName, isVar, isVars, isContext));
            } else {
                throw new ProcessingErrorException("Only @%s and @%s and %s variables are supported as JobWorker arguments".formatted(
                    ANNOTATION_VARIABLES.simpleName(), ANNOTATION_VARIABLE.simpleName(), CLASS_JOB_CONTEXT.simpleName()), method);
            }
        }

        return variables;
    }

    record Variable(VariableElement parameter, String name, boolean isVar, boolean isVars, boolean isContext) {

    }
}
