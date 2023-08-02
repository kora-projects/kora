package ru.tinkoff.kora.logging.aspect;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.aop.annotation.processor.KoraAspect;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import java.util.*;

import static ru.tinkoff.kora.logging.aspect.LogAspectClassNames.*;
import static ru.tinkoff.kora.logging.aspect.LogAspectUtils.*;

public class LogAspect implements KoraAspect {

    private static final String RESULT_VAR_NAME = "__result";
    private static final String DATA_IN_VAR_NAME = "__dataIn";
    private static final String DATA_OUT_VAR_NAME = "__dataOut";
    private static final String MESSAGE_IN = ">";
    private static final String MESSAGE_OUT = "<";

    private final ProcessingEnvironment env;

    public LogAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(log.canonicalName(), logIn.canonicalName(), logOut.canonicalName());
    }

    @Override
    public ApplyResult apply(ExecutableElement executableElement, String superCall, AspectContext aspectContext) {
        var loggerType = this.env.getElementUtils().getTypeElement(Logger.class.getCanonicalName()).asType();
        var loggerFactoryType = this.env.getElementUtils().getTypeElement(ILoggerFactory.class.getCanonicalName()).asType();
        var loggerName = executableElement.getEnclosingElement() + "." + executableElement.getSimpleName();
        var loggerFactoryFieldName = aspectContext.fieldFactory().constructorParam(
            loggerFactoryType,
            List.of()
        );
        var loggerFieldName = aspectContext.fieldFactory().constructorInitialized(
            loggerType,
            CodeBlock.builder().add("$N.getLogger($S)", loggerFactoryFieldName, loggerName).build()
        );

        var isMono = CommonUtils.isMono(executableElement.getReturnType());
        if (isMono) {
            return this.monoBody(executableElement, superCall, loggerFieldName);
        } else {
            return this.blockingBody(executableElement, superCall, loggerFieldName);
        }
    }

    private ApplyResult blockingBody(ExecutableElement executableElement, String superCall, String loggerFieldName) {
        var logInLevel = logInLevel(executableElement, env);
        var logOutLevel = logOutLevel(executableElement, env);

        var b = CodeBlock.builder();
        if (logInLevel != null) {
            b.add(this.buildLogIn(executableElement, logInLevel, loggerFieldName));
        }
        var isVoid = executableElement.getReturnType().getKind() == TypeKind.VOID;
        if (isVoid) {
            b.add(this.buildSuperCallWithParameters(executableElement, superCall)).add(";");
        } else {
            b.add("var $N = $L;\n", RESULT_VAR_NAME, this.buildSuperCallWithParameters(executableElement, superCall));
        }
        if (logOutLevel != null) {
            var logResultLevel = logResultLevel(executableElement, logOutLevel, env);
            if (isVoid || logResultLevel == null) {
                b.addStatement("$N.$L($S)", loggerFieldName, logOutLevel.toLowerCase(), MESSAGE_OUT);
            } else if (logOutLevel.equals(logResultLevel)) {
                ifLogLevelEnabled(b, loggerFieldName, logOutLevel, () -> {
                    b.addStatement("var $N = $T.marker($S, gen -> gen.writeStringField($S, String.valueOf($N)))", DATA_OUT_VAR_NAME, structuredArgument, "data", "out", RESULT_VAR_NAME);
                    b.add("$N.$L($N, $S);", loggerFieldName, logOutLevel.toLowerCase(), DATA_OUT_VAR_NAME, MESSAGE_OUT);
                }).add("\n");
            } else {
                ifLogLevelEnabled(b, loggerFieldName, logResultLevel, () -> {
                    b.addStatement("var $N = $T.marker($S, gen -> gen.writeStringField($S, String.valueOf($N)))", DATA_OUT_VAR_NAME, structuredArgument, "data", "out", RESULT_VAR_NAME);
                    b.add("$N.$L($N, $S);", loggerFieldName, logOutLevel.toLowerCase(), DATA_OUT_VAR_NAME, MESSAGE_OUT);
                    b.add("$<\n} else {$>\n");
                    b.add("$N.$L($S);", loggerFieldName, logOutLevel.toLowerCase(), MESSAGE_OUT);
                });
            }
        }
        if (!isVoid) {
            b.addStatement("return $N", RESULT_VAR_NAME);
        }
        return new ApplyResult.MethodBody(b.build());
    }

    private ApplyResult monoBody(ExecutableElement executableElement, String superCall, String loggerFieldName) {
        var logInLevel = logInLevel(executableElement, env);
        var logOutLevel = logOutLevel(executableElement, env);
        var b = CodeBlock.builder();

        b.add("var $N = $L;\n", RESULT_VAR_NAME, this.buildSuperCallWithParameters(executableElement, superCall));
        if (logInLevel != null) {
            var finalResultName = RESULT_VAR_NAME + "_final";
            b.add("var $N = $N;\n", finalResultName, RESULT_VAR_NAME);
            ifLogLevelEnabled(b, loggerFieldName, logInLevel, () -> {
                b.add("$N = $T.defer(() -> {$>\n", RESULT_VAR_NAME, CommonClassNames.mono);
                b.add(this.buildLogIn(executableElement, logInLevel, loggerFieldName));
                b.add("return $N;", finalResultName);
                b.add("$<\n});");
            }).add("\n");
        }
        if (logOutLevel != null) {
            var logResultLevel = logResultLevel(executableElement, logOutLevel, env);
            ifLogLevelEnabled(b, loggerFieldName, logOutLevel, () -> {
                var returnType = (ParameterizedTypeName) TypeName.get(executableElement.getReturnType());
                if (logResultLevel == null || returnType.typeArguments.get(0).equals(TypeName.VOID.box())) {
                    b.add("$N = $N.doOnSuccess(v -> $N.$L($S));", RESULT_VAR_NAME, RESULT_VAR_NAME, loggerFieldName, logOutLevel.toLowerCase(), MESSAGE_OUT);
                } else {
                    var resultValue = RESULT_VAR_NAME + "_value";
                    b.add("var $N = this.$N;\n", loggerFieldName, loggerFieldName);
                    b.add("$N = $N.doOnSuccess($N -> $>{\n", RESULT_VAR_NAME, RESULT_VAR_NAME, resultValue);
                    b.add("if ($N != null) {$>\n", resultValue);
                    if (logOutLevel.equals(logResultLevel)) {
                        ifLogLevelEnabled(b, loggerFieldName, logOutLevel, () -> {
                            b.addStatement("var $N = $T.marker($S, gen -> gen.writeStringField($S, String.valueOf($N)))", DATA_OUT_VAR_NAME, structuredArgument, "data", "out", resultValue);
                            b.add("$N.$L($N, $S);", loggerFieldName, logOutLevel.toLowerCase(), DATA_OUT_VAR_NAME, MESSAGE_OUT);
                        });
                    } else {
                        ifLogLevelEnabled(b, loggerFieldName, logResultLevel, () -> {
                            b.addStatement("var $N = $T.marker($S, gen -> gen.writeStringField($S, String.valueOf($N)))", DATA_OUT_VAR_NAME, structuredArgument, "data", "out", resultValue);
                            b.add("$N.$L($N, $S);", loggerFieldName, logOutLevel.toLowerCase(), DATA_OUT_VAR_NAME, MESSAGE_OUT);
                            b.add("$<\n} else {$>\n");
                            b.add("$N.$L($S);", loggerFieldName, logOutLevel.toLowerCase(), MESSAGE_OUT);
                        });
                    }
                    b.add("$<\n} else {$>\n");
                    b.add("$N.$L($S);", loggerFieldName, logOutLevel.toLowerCase(), MESSAGE_OUT);
                    b.add("$<\n}");
                    b.add("$<\n});");
                }
            }).add("\n");
        }

        return new ApplyResult.MethodBody(b.add("return $N;\n", RESULT_VAR_NAME).build());
    }

    /**
     * Generate calling of original method with params
     *
     * <p>Example result:</p>
     * <pre>{@code super.<originalMethodName>(param1, param2)}</pre>
     *
     * @return
     */
    private CodeBlock buildSuperCallWithParameters(ExecutableElement executableElement, String superCall) {
        var b = CodeBlock.builder()
            .add(superCall).add("(");
        for (int i = 0; i < executableElement.getParameters().size(); i++) {
            var parameter = executableElement.getParameters().get(i);
            if (i > 0) {
                b.add(", ");
            }
            b.add("$N", parameter.getSimpleName());
        }
        b.add(")");
        return b.build();
    }


    private CodeBlock buildLogIn(ExecutableElement executableElement, String logInLevel, String loggerFieldName) {
        var b = CodeBlock.builder();
        var methodLevelIdx = LEVELS.indexOf(logInLevel);
        var logMarkerCode = this.logInMarker(loggerFieldName, executableElement, logInLevel);
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

    record LogInMarker(CodeBlock codeBlock, String minLogLevel) {}

    /**
     * <pre> {@code var __dataIn = StructuredArgument.marker("data", gen -> {
     *      gen.writeStringField("param1", String.valueOf(param1));
     *      gen.writeStringField("param2", String.valueOf(param2));
     *  });
     * } </pre>
     */
    @Nullable
    private LogInMarker logInMarker(String loggerField, ExecutableElement executableElement, String logInLevel) {
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
        b.add("var $N = $T.marker($S, gen -> {$>", DATA_IN_VAR_NAME, structuredArgument, "data");

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
                b.add("\nif ($N.$N()) {$>", loggerField, "is" + CommonUtils.capitalize(level.toLowerCase()) + "Enabled");
            }
            for (var param : paramsForLevel) {
                b.add("\ngen.writeStringField($S, String.valueOf($N));", param.getSimpleName(), param.getSimpleName());
            }
            if (i > minLevelIdx) {
                b.add("$<\n}");
            }
        }

        return new LogInMarker(b.add("$<\n});\n").build(), LEVELS.get(minLevelIdx));
    }

    private CodeBlock.Builder ifLogLevelEnabled(CodeBlock.Builder cb, String loggerFieldName, String logLevel, Runnable r) {
        cb.add("if ($N.$N()) {$>\n", loggerFieldName, "is" + CommonUtils.capitalize(logLevel.toLowerCase()) + "Enabled");
        r.run();
        cb.add("$<\n}");

        return cb;
    }
}
