package ru.tinkoff.kora.logging.aspect;

import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.util.List;
import java.util.Optional;

import static ru.tinkoff.kora.logging.aspect.LogAspectClassNames.log;
import static ru.tinkoff.kora.logging.aspect.LogAspectClassNames.logIn;
import static ru.tinkoff.kora.logging.aspect.LogAspectClassNames.logOff;
import static ru.tinkoff.kora.logging.aspect.LogAspectClassNames.logOut;
import static ru.tinkoff.kora.logging.aspect.LogAspectClassNames.logResult;

public class LogAspectUtils {
    public static final List<String> LEVELS = List.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE");

    @Nullable
    public static String logInLevel(ExecutableElement method, ProcessingEnvironment env) {
        var logAnnotation = AnnotationUtils.findAnnotation(method, log);
        var logInAnnotation = AnnotationUtils.findAnnotation(method, logIn);
        if (logAnnotation == null && logInAnnotation == null) {
            return null;
        }
        if (logInAnnotation != null) {
            var level = parseLogLevel(logInAnnotation, env);
            if (level != null) {
                return level;
            }
        }
        if (logAnnotation != null) {
            var level = parseLogLevel(logAnnotation, env);
            if (level != null) {
                return level;
            }
        }
        return "INFO";
    }

    @Nullable
    public static String logOutLevel(ExecutableElement method, ProcessingEnvironment env) {
        var logAnnotation = AnnotationUtils.findAnnotation(method, log);
        var logOutAnnotation = AnnotationUtils.findAnnotation(method, logOut);
        if (logAnnotation == null && logOutAnnotation == null) {
            return null;
        }
        if (logOutAnnotation != null) {
            var level = parseLogLevel(logOutAnnotation, env);
            if (level != null) {
                return level;
            }
        }
        if (logAnnotation != null) {
            var level = parseLogLevel(logAnnotation, env);
            if (level != null) {
                return level;
            }
        }
        return "INFO";
    }

    @Nullable
    public static String logResultLevel(ExecutableElement method, String logOutLevel, ProcessingEnvironment env) {
        var logOffAnnotation = AnnotationUtils.findAnnotation(method, logOff);
        if (logOffAnnotation != null) {
            return null;
        }
        var logResultAnnotation = AnnotationUtils.findAnnotation(method, logResult);
        var level = parseLogLevel(logResultAnnotation, env);
        if (level != null) {
            if (LEVELS.indexOf(logOutLevel) > LEVELS.indexOf(level)) {
                return logOutLevel;
            }
            return level;
        }
        if (LEVELS.indexOf(logOutLevel) > LEVELS.indexOf("DEBUG")) {
            return logOutLevel;
        }
        return "DEBUG";
    }

    @Nullable
    public static String logParameterLevel(VariableElement parameter, String logInLevel, ProcessingEnvironment env) {
        var logOffAnnotation = AnnotationUtils.findAnnotation(parameter, logOff);
        if (logOffAnnotation != null) {
            return null;
        }
        var logAnnotation = AnnotationUtils.findAnnotation(parameter, log);
        var logLevel = parseLogLevel(logAnnotation, env);
        if (logLevel != null) {
            if (LEVELS.indexOf(logInLevel) > LEVELS.indexOf(logLevel)) {
                return logInLevel;
            }
            return logLevel;
        }
        if (LEVELS.indexOf(logInLevel) > LEVELS.indexOf("DEBUG")) {
            return logInLevel;
        }
        return "DEBUG";
    }

    private static String parseLogLevel(AnnotationMirror annotation, ProcessingEnvironment env) {
        return Optional.ofNullable(annotation)
            .flatMap(annotationMirror -> env.getElementUtils().getElementValuesWithDefaults(annotationMirror)
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().getSimpleName().toString().equals("value"))
                .map(entry -> entry.getValue().getValue().toString())
                .findFirst()
            ).orElse(null);
    }
}
