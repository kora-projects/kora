package ru.tinkoff.kora.annotation.processor.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class BuildEnvironment {
    public static final Logger log = LoggerFactory.getLogger("ru.tinkoff.kora");
    private static final AtomicBoolean INIT = new AtomicBoolean(false);
    private static Path buildDir = Paths.get(".");

    public static synchronized void init(ProcessingEnvironment processingEnv) {
        if (!INIT.compareAndSet(false, true)) {
            return;
        }
        try {
            var resource = processingEnv.getFiler().getResource(StandardLocation.SOURCE_OUTPUT, "", "out");
            var sourceOutput = Paths.get(resource.toUri()).toAbsolutePath()
                .getParent();
            var dir = sourceOutput.getParent();
            if (dir.getFileName().toString().equals("java")) {
                buildDir = dir.getParent().getParent().getParent().getParent();
            } else if (dir.getFileName().toString().startsWith("generated-")) {
                buildDir = dir.getParent();
            } else {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Build dir was no detected, there will be no build log for kora");
                return;
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Build dir was no detected, there will be no build log for kora");
            return;
        }
        initLog(processingEnv);
    }

    private static void initLog(ProcessingEnvironment processingEnv) {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
            return;
        }
        var ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        var kora = ctx.getLogger("ru.tinkoff.kora");
        kora.setAdditive(false);
        kora.detachAndStopAllAppenders();
        var appender = new FileAppender<ILoggingEvent>();
        var fileName = DateTimeFormatter.ofPattern("yyyy-MM-dd-hh-mm").format(LocalDateTime.now()) + ".log";
        appender.setFile(buildDir.resolve("kora").resolve("log").resolve(fileName).toString());

        var patternLayoutEncoder = new PatternLayoutEncoder();
        patternLayoutEncoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{100} %X %msg%n");
        patternLayoutEncoder.setContext(ctx);
        patternLayoutEncoder.start();
        appender.setEncoder(patternLayoutEncoder);
        appender.setContext(ctx);
        appender.start();
        kora.addAppender(appender);

        if (log instanceof ch.qos.logback.classic.Logger logger) {
            logger.setLevel(Level.valueOf(processingEnv.getOptions().getOrDefault("koraLogLevel", "DEBUG")));
        }
    }
}
