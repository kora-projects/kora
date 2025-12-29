package ru.tinkoff.kora.logging.aspect;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import java.util.*;

import static ru.tinkoff.kora.logging.aspect.LogAspectClassNames.*;
import static ru.tinkoff.kora.logging.aspect.LogAspectUtils.*;

public class LogAspect implements KoraAspect {

    private static final String RESULT_VAR_NAME = "__result";
    private static final String ELEMENT_VAR_NAME = "__element";
    private static final String ERROR_VAR_NAME = "__error";
    private static final String DATA_IN_VAR_NAME = "__dataIn";
    private static final String DATA_OUT_VAR_NAME = "__dataOut";
    private static final String DATA_ERROR_VAR_NAME = "__dataError";
    private static final String MESSAGE_IN = ">";
    private static final String MESSAGE_OUT = "<";
    private static final String MESSAGE_OUT_ELEMENT = "<<<";

    private final ProcessingEnvironment env;

    public LogAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(log.canonicalName(), logIn.canonicalName(), logOut.canonicalName());
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(log, logIn, logOut);
    }

    @Override
    public ApplyResult apply(ExecutableElement executableElement, String superCall, AspectContext aspectContext) {
        var loggerName = executableElement.getEnclosingElement() + "." + executableElement.getSimpleName();
        var loggerFactoryFieldName = aspectContext.fieldFactory().constructorParam(
            loggerFactory,
            List.of()
        );
        var loggerFieldName = aspectContext.fieldFactory().constructorInitialized(
            logger,
            CodeBlock.builder().add("$N.getLogger($S)", loggerFactoryFieldName, loggerName).build()
        );

        if (MethodUtils.isPublisher(executableElement)) {
            throw new ProcessingErrorException("Publisher methods are not supported", executableElement);
        }
        if (MethodUtils.isFuture(executableElement)) {
            return this.futureBody(aspectContext, executableElement, superCall, loggerFieldName);
        } else {
            return this.blockingBody(aspectContext, executableElement, superCall, loggerFieldName);
        }
    }

    private ApplyResult blockingBody(AspectContext aspectContext, ExecutableElement executableElement, String superCall, String loggerFieldName) {
        var logInLevel = logInLevel(executableElement, env);
        var logOutLevel = logOutLevel(executableElement, env);

        var b = CodeBlock.builder();
        if (logInLevel != null) {
            b.add(this.buildLogIn(aspectContext, executableElement, logInLevel, loggerFieldName));
        }
        b.beginControlFlow("try");
        var isVoid = executableElement.getReturnType().getKind() == TypeKind.VOID;
        if (isVoid) {
            b.addStatement(KoraAspect.callSuper(executableElement, superCall));
        } else {
            b.addStatement("var $N = $L", RESULT_VAR_NAME, KoraAspect.callSuper(executableElement, superCall));
        }
        if (logOutLevel != null) {
            var logResultLevel = logResultLevel(executableElement, logOutLevel, env);
            final CodeBlock resultWriter;
            if (!isVoid && logResultLevel != null) {
                var mapping = CommonUtils.parseMapping(executableElement).getMapping(structuredArgumentMapper);
                var mapperType = mapping != null && mapping.mapperClass() != null
                    ? mapping.isGeneric() ? mapping.parameterized(TypeName.get(executableElement.getReturnType())) : TypeName.get(mapping.mapperClass())
                    : ParameterizedTypeName.get(structuredArgumentMapper, TypeName.get(executableElement.getReturnType()).box());
                var mapper = aspectContext.fieldFactory().constructorParam(
                    mapperType.annotated(CommonClassNames.nullableAnnotation),
                    List.of()
                );
                var resultWriterBuilder = CodeBlock.builder().beginControlFlow("gen ->")
                    .addStatement("gen.writeStartObject()")
                    .beginControlFlow("if (this.$N != null)", mapper)
                    .addStatement("gen.writeName($S)", "out")
                    .addStatement("this.$N.write(gen, $N)", mapper, RESULT_VAR_NAME)
                    .nextControlFlow("else")
                    .addStatement("gen.writeStringProperty($S, String.valueOf($N))", "out", RESULT_VAR_NAME)
                    .endControlFlow()
                    .addStatement("gen.writeEndObject()")
                    .endControlFlow();
                resultWriter = resultWriterBuilder.build();
            } else {
                resultWriter = null;
            }

            if (isVoid || logResultLevel == null) {
                b.addStatement("$N.$L($S)", loggerFieldName, logOutLevel.toLowerCase(), MESSAGE_OUT);
            } else if (logOutLevel.equals(logResultLevel)) {
                ifLogLevelEnabled(b, loggerFieldName, logOutLevel, () -> {
                    b.add("var $N = $T.marker($S, $L);\n", DATA_OUT_VAR_NAME, structuredArgument, "data", resultWriter);
                    b.add("$N.$L($N, $S);", loggerFieldName, logOutLevel.toLowerCase(), DATA_OUT_VAR_NAME, MESSAGE_OUT);
                }).add("\n");
            } else {
                ifLogLevelEnabled(b, loggerFieldName, logResultLevel, () -> {
                    b.add("var $N = $T.marker($S, $L);\n", DATA_OUT_VAR_NAME, structuredArgument, "data", resultWriter);
                    b.add("$N.$L($N, $S);", loggerFieldName, logOutLevel.toLowerCase(), DATA_OUT_VAR_NAME, MESSAGE_OUT);
                    b.add("$<\n} else {$>\n");
                    b.add("$N.$L($S);", loggerFieldName, logOutLevel.toLowerCase(), MESSAGE_OUT);
                });
            }
        }
        if (!isVoid) {
            b.addStatement("return $N", RESULT_VAR_NAME);
        }
        b.nextControlFlow("catch(Throwable $L)", ERROR_VAR_NAME);
        b.beginControlFlow("if ($N.isWarnEnabled())", loggerFieldName);
        var errorWriterBuilder = CodeBlock.builder()
            .add("var $N = $T.marker($S, ", DATA_ERROR_VAR_NAME, structuredArgument, "data")
            .beginControlFlow("gen ->")
            .addStatement("gen.writeStartObject()")
            .addStatement("gen.writeStringProperty($S, $L.getClass().getCanonicalName())", "errorType", ERROR_VAR_NAME)
            .addStatement("gen.writeStringProperty($S, $L.getMessage())", "errorMessage", ERROR_VAR_NAME)
            .addStatement("gen.writeEndObject()")
            .endControlFlow(")");

        b.add(errorWriterBuilder.build());
        b.add("\n");
        b.beginControlFlow("if($N.isDebugEnabled())", loggerFieldName);
        b.addStatement("$N.$L($N, $S, $L)", loggerFieldName, "warn", DATA_ERROR_VAR_NAME, MESSAGE_OUT, ERROR_VAR_NAME);
        b.nextControlFlow("else");
        b.addStatement("$N.$L($N, $S)", loggerFieldName, "warn", DATA_ERROR_VAR_NAME, MESSAGE_OUT);
        b.endControlFlow();
        b.endControlFlow();
        b.addStatement("throw $L", ERROR_VAR_NAME);
        b.endControlFlow();
        return new ApplyResult.MethodBody(b.build());
    }

    private ApplyResult futureBody(AspectContext aspectContext, ExecutableElement executableElement, String superCall, String loggerFieldName) {
        var logInLevel = logInLevel(executableElement, env);
        var logOutLevel = logOutLevel(executableElement, env);

        var b = CodeBlock.builder();
        if (logInLevel != null) {
            b.add(this.buildLogIn(aspectContext, executableElement, logInLevel, loggerFieldName));
        }

        var methodGeneric = MethodUtils.getGenericType(executableElement.getReturnType()).orElseThrow();

        var isVoid = MethodUtils.getGenericType(executableElement.getReturnType())
            .map(CommonUtils::isVoid)
            .orElse(false);

        b.add("return $L", KoraAspect.callSuper(executableElement, superCall));

        if (logOutLevel != null) {
            var logResultLevel = logResultLevel(executableElement, logOutLevel, env);
            final CodeBlock resultWriter;
            b.beginControlFlow(".whenComplete(($L, $L) -> ", RESULT_VAR_NAME, ERROR_VAR_NAME);
            if (!isVoid && logResultLevel != null) {
                var mapping = CommonUtils.parseMapping(executableElement).getMapping(structuredArgumentMapper);
                var mapperType = mapping != null && mapping.mapperClass() != null
                    ? mapping.isGeneric() ? mapping.parameterized(TypeName.get(methodGeneric)) : TypeName.get(mapping.mapperClass())
                    : ParameterizedTypeName.get(structuredArgumentMapper, TypeName.get(methodGeneric));
                var mapper = aspectContext.fieldFactory().constructorParam(
                    mapperType.annotated(CommonClassNames.nullableAnnotation),
                    List.of()
                );
                var resultWriterBuilder = CodeBlock.builder().add("gen -> {$>\n")
                    .add("gen.writeStartObject();\n")
                    .beginControlFlow("if (this.$N != null)", mapper)
                    .addStatement("gen.writeName($S)", "out")
                    .addStatement("this.$N.write(gen, $N)", mapper, RESULT_VAR_NAME)
                    .nextControlFlow("else")
                    .addStatement("gen.writeStringProperty($S, String.valueOf($N))", "out", RESULT_VAR_NAME)
                    .endControlFlow()
                    .add("gen.writeEndObject();")
                    .add("$<\n}");
                resultWriter = resultWriterBuilder.build();

            } else {
                resultWriter = null;
            }

            b.beginControlFlow("if($L == null)", ERROR_VAR_NAME);
            if (isVoid || logResultLevel == null) {
                b.addStatement("$N.$L($S)", loggerFieldName, logOutLevel.toLowerCase(), MESSAGE_OUT);
            } else if (logOutLevel.equals(logResultLevel)) {
                ifLogLevelEnabled(b, loggerFieldName, logOutLevel, () -> {
                    b.add("var $N = $T.marker($S, $L);\n", DATA_OUT_VAR_NAME, structuredArgument, "data", resultWriter);
                    b.add("$N.$L($N, $S);", loggerFieldName, logOutLevel.toLowerCase(), DATA_OUT_VAR_NAME, MESSAGE_OUT);
                }).add("\n");
            } else {
                ifLogLevelEnabled(b, loggerFieldName, logResultLevel, () -> {
                    b.add("var $N = $T.marker($S, $L);\n", DATA_OUT_VAR_NAME, structuredArgument, "data", resultWriter);
                    b.add("$N.$L($N, $S);", loggerFieldName, logOutLevel.toLowerCase(), DATA_OUT_VAR_NAME, MESSAGE_OUT);
                    b.add("$<\n} else {$>\n");
                    b.add("$N.$L($S);", loggerFieldName, logOutLevel.toLowerCase(), MESSAGE_OUT);
                });
            }
            b.nextControlFlow("else");
            var errorWriterBuilder = CodeBlock.builder()
                .add("var $N = $T.marker($S, ", DATA_ERROR_VAR_NAME, structuredArgument, "data")
                .beginControlFlow("gen ->")
                .addStatement("gen.writeStartObject()")
                .addStatement("gen.writeStringProperty($S, $L.getClass().getCanonicalName())", "errorType", ERROR_VAR_NAME)
                .addStatement("gen.writeStringProperty($S, $L.getMessage())", "errorMessage", ERROR_VAR_NAME)
                .addStatement("gen.writeEndObject()")
                .endControlFlow(")");

            b.add(errorWriterBuilder.build());
            b.add("\n");
            b.beginControlFlow("if($N.isDebugEnabled())", loggerFieldName);
            b.addStatement("$N.$L($N, $S, $L)", loggerFieldName, "warn", DATA_ERROR_VAR_NAME, MESSAGE_OUT, ERROR_VAR_NAME);
            b.nextControlFlow("else");
            b.addStatement("$N.$L($N, $S)", loggerFieldName, "warn", DATA_ERROR_VAR_NAME, MESSAGE_OUT);
            b.endControlFlow();
            b.endControlFlow();

            b.endControlFlow(")");
        } else {
            b.add(";");
        }

        return new ApplyResult.MethodBody(b.build());
    }

    private CodeBlock buildLogIn(AspectContext aspectContext, ExecutableElement executableElement, String logInLevel, String loggerFieldName) {
        var b = CodeBlock.builder();
        var methodLevelIdx = LEVELS.indexOf(logInLevel);
        var logMarkerCode = this.logInMarker(aspectContext, loggerFieldName, executableElement, logInLevel);
        if (logMarkerCode != null) {
            if (LEVELS.indexOf(logMarkerCode.minLogLevel()) <= methodLevelIdx) {
                ifLogLevelEnabled(b, loggerFieldName, logInLevel, () -> {
                    b.add(logMarkerCode.codeBlock());
                    b.add("$N.$L($N, $S);", loggerFieldName, logInLevel.toLowerCase(), DATA_IN_VAR_NAME, MESSAGE_IN);
                }).add("\n");
            } else {
                ifLogLevelEnabled(b, loggerFieldName, logMarkerCode.minLogLevel(), () -> {
                    b.add(logMarkerCode.codeBlock());
                    b.add("$N.$L($N, $S);", loggerFieldName, logInLevel.toLowerCase(), DATA_IN_VAR_NAME, MESSAGE_IN);
                    b.add("$<\n} else {$>\n");
                    b.add("$N.$L($S);", loggerFieldName, logInLevel.toLowerCase(), MESSAGE_IN);
                }).add("\n");
            }
        } else {
            b.add("$N.$L($S);\n", loggerFieldName, logInLevel.toLowerCase(), MESSAGE_IN);
        }
        return b.build();
    }

    record LogInMarker(CodeBlock codeBlock, String minLogLevel) {
    }

    /**
     * <pre> {@code var __dataIn = StructuredArgument.marker("data", gen -> {
     *      gen.writeStartObject();
     *      gen.writeStringProperty("param1", String.valueOf(param1));
     *      gen.writeStringProperty("param2", String.valueOf(param2));
     *      gen.writeEndObject();
     *  });
     * } </pre>
     */
    @Nullable
    private LogInMarker logInMarker(AspectContext aspectContext, String loggerField, ExecutableElement executableElement, String logInLevel) {
        var parametersToLog = new ArrayList<VariableElement>(executableElement.getParameters().size());
        for (var parameter : executableElement.getParameters()) {
            if (AnnotationUtils.findAnnotation(parameter, logOff) == null) {
                parametersToLog.add(parameter);
            }
        }
        if (parametersToLog.isEmpty()) {
            return null;
        }

        var b = CodeBlock.builder();
        b.beginControlFlow("var $N = $T.marker($S, gen -> ", DATA_IN_VAR_NAME, structuredArgument, "data");
        b.addStatement("gen.writeStartObject()");

        var params = new HashMap<String, List<VariableElement>>();
        var minLevelIdx = Integer.MAX_VALUE;

        for (var parameter : parametersToLog) {
            var level = Objects.requireNonNull(logParameterLevel(parameter, logInLevel, env));
            minLevelIdx = Math.min(minLevelIdx, LEVELS.indexOf(level));
            params.computeIfAbsent(level, l -> new ArrayList<>()).add(parameter);
        }
        for (int i = 0; i < LEVELS.size(); i++) {
            var level = LEVELS.get(i);
            var paramsForLevel = params.getOrDefault(level, List.of());
            if (paramsForLevel.isEmpty()) {
                continue;
            }
            if (i > minLevelIdx) {
                b.beginControlFlow("if ($N.$N())", loggerField, "is" + CommonUtils.capitalize(level.toLowerCase()) + "Enabled");
            }
            for (var param : paramsForLevel) {
                var mapping = CommonUtils.parseMapping(param).getMapping(structuredArgumentMapper);
                var mapperType = mapping != null && mapping.mapperClass() != null
                    ? mapping.isGeneric() ? mapping.parameterized(TypeName.get(param.asType())) : TypeName.get(mapping.mapperClass())
                    : ParameterizedTypeName.get(structuredArgumentMapper, TypeName.get(param.asType()).box());
                var mapper = aspectContext.fieldFactory().constructorParam(
                    mapperType.annotated(CommonClassNames.nullableAnnotation),
                    List.of()
                );
                b.beginControlFlow("if (this.$N != null)", mapper);
                b.addStatement("gen.writeName($S)", param.getSimpleName());
                b.addStatement("this.$N.write(gen, $N)", mapper, param.getSimpleName());
                b.nextControlFlow("else");
                b.addStatement("gen.writeStringProperty($S, String.valueOf($N))", param.getSimpleName(), param.getSimpleName());
                b.endControlFlow();
            }
            if (i > minLevelIdx) {
                b.endControlFlow();
            }
        }
        b.addStatement("gen.writeEndObject()");

        return new LogInMarker(b.endControlFlow(")").build(), LEVELS.get(minLevelIdx));
    }

    private CodeBlock.Builder ifLogLevelEnabled(CodeBlock.Builder cb, String loggerFieldName, String logLevel, Runnable r) {
        cb.add("if ($N.$N()) {$>\n", loggerFieldName, "is" + CommonUtils.capitalize(logLevel.toLowerCase()) + "Enabled");
        r.run();
        cb.add("$<\n}\n");
        return cb;
    }
}
