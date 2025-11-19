package ru.tinkoff.kora.camunda.zeebe.worker.annotation.processor;

import com.palantir.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.regex.Pattern;
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

    private static final Pattern VAR_PATTERN = Pattern.compile("[a-zA-Z_]+[a-zA-Z0-9_]+");
    private static final Set<String> VAR_RESERVED = Set.of("null", "true", "false", "function", "if", "then", "else", "for", "between", "instance", "of", "not");

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(ANNOTATION_WORKER);
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var element : annotatedElements.getOrDefault(ANNOTATION_WORKER, List.of())) {
            var method = (ExecutableElement) element.element();
            if (method.getModifiers().stream().anyMatch(m -> m.equals(Modifier.PRIVATE))) {
                throw new ProcessingErrorException("@JobWorker method can't be private", method);
            }

            var packageName = getPackage(method);
            var ownerType = getOwner(method);

            final List<Variable> variables = getVariables(method);
            var implSpecBuilder = TypeSpec.classBuilder(NameUtils.generatedType(ownerType, "%s_KoraJobWorker".formatted(method.getSimpleName())))
                .addOriginatingElement(ownerType)
                .addAnnotation(AnnotationUtils.generated(ZeebeWorkerAnnotationProcessor.class))
                .addModifiers(Modifier.FINAL, Modifier.PUBLIC)
                .addAnnotation(CommonClassNames.component)
                .addSuperinterface(CLASS_KORA_WORKER);

            final MethodSpec methodConstructor = getMethodConstructor(ownerType, method, implSpecBuilder, variables);
            final MethodSpec methodFetchVariables = getMethodFetchVariables(method, variables);
            if (methodFetchVariables != null) {
                implSpecBuilder.addMethod(methodFetchVariables);
            }

            var specBuilder = implSpecBuilder
                .addMethod(methodConstructor)
                .addMethod(getMethodType(method));
            if (MethodUtils.isFuture(method) || MethodUtils.isMono(method)) {
                throw new ProcessingErrorException("Async invocation is not supported", method);
            }

            specBuilder.addMethod(getMethodHandler(ownerType, method, variables));

            var spec = specBuilder.build();
            try {
                var implFile = JavaFile.builder(packageName, spec).build();
                implFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
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

            codeBuilder.beginControlFlow("if (result != null)");
            AnnotationMirror returnJobVariable = AnnotationUtils.findAnnotation(method, ANNOTATION_VARIABLE);
            if (returnJobVariable != null) {
                String varName = AnnotationUtils.parseAnnotationValueWithoutDefault(returnJobVariable, "value");
                if (varName == null) {
                    throw new ProcessingErrorException("Worker result job variable must specify name or @JobVariable annotation must be removed if result represent all variables", method);
                }

                if (isVariableInvalid(varName)) {
                    throw new ProcessingErrorException("Worker result job variable name must be alphanumeric ( _ symbol is allowed) and not start with number, but was: " + varName, method);
                }

                codeBuilder.addStatement("var _vars = $S + varsWriter.toStringUnchecked(result) + $S", "{\"" + varName + "\":", "}");
                codeBuilder.addStatement("return client.newCompleteCommand(job).variables(_vars)");
            } else {
                codeBuilder.addStatement("var _vars = varsWriter.toStringUnchecked(result)");
                codeBuilder.addStatement("return client.newCompleteCommand(job).variables(_vars)");
            }
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

        codeBuilder.nextControlFlow("catch (Exception e)");
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

        if (MethodUtils.isFlux(method) || MethodUtils.isPublisher(method)) {
            throw new ProcessingErrorException("@JobWorker return type can't be Flux/Publisher", method);
        } else if (!MethodUtils.isVoid(method)) {
            final TypeMirror returnType;
            if (MethodUtils.isOptional(method) || MethodUtils.isFuture(method) || MethodUtils.isMono(method)) {
                returnType = MethodUtils.getGenericType(method.getReturnType()).orElseThrow(() -> new ProcessingErrorException("Method return type must have type signature", method));
            } else {
                returnType = method.getReturnType();
            }

            var writerType = ParameterizedTypeName.get(CLASS_JSON_WRITER, TypeName.get(returnType).box());
            implBuilder.addField(writerType, "varsWriter", Modifier.PRIVATE, Modifier.FINAL);
            methodBuilder.addParameter(writerType, "varsWriter");
            constructorBuilder.addStatement("this.varsWriter = varsWriter");
        }

        variables.stream()
            .filter(v -> v.isVars)
            .findFirst()
            .ifPresent(vars -> {
                var readerType = ParameterizedTypeName.get(CLASS_JSON_READER, TypeName.get(vars.parameter.asType()).box());
                implBuilder.addField(readerType, "varsReader", Modifier.PRIVATE, Modifier.FINAL);
                methodBuilder.addParameter(readerType, "varsReader");
                constructorBuilder.addStatement("this.varsReader = varsReader");
            });

        int varCounter = 1;
        for (Variable variable : variables) {
            if (variable.isVar) {
                var readerType = ParameterizedTypeName.get(CLASS_JSON_READER, TypeName.get(variable.parameter.asType()).box());
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

                if (isVariableInvalid(varName)) {
                    throw new ProcessingErrorException("Worker argument job variable name must be alphanumeric with _ symbol and not start with number, but was: " + varName, method);
                }

                variables.add(new Variable(parameter, varName, isVar, isVars, isContext));
            } else {
                throw new ProcessingErrorException("Only @%s and @%s and %s variables are supported as JobWorker arguments".formatted(
                    ANNOTATION_VARIABLES.simpleName(), ANNOTATION_VARIABLE.simpleName(), CLASS_JOB_CONTEXT.simpleName()), method);
            }
        }

        return variables;
    }

    private static boolean isVariableInvalid(String name) {
        if (!VAR_PATTERN.matcher(name).matches()) {
            return true;
        }

        for (String s : VAR_RESERVED) {
            if (s.equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    record Variable(VariableElement parameter, String name, boolean isVar, boolean isVars, boolean isContext) {

    }
}
