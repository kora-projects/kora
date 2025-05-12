package ru.tinkoff.kora.annotation.processor.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class BuildEnvironment {

    public static final Logger log = LoggerFactory.getLogger("ru.tinkoff.kora");
    private static final AtomicBoolean INIT = new AtomicBoolean(false);
    private static Path buildDir = Paths.get(".");

    private BuildEnvironment() {}

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
                return;
            }
        } catch (IOException e) {
            return;
        }
        initLog(processingEnv);

        var thread = getShutdownThread();
        Runtime.getRuntime().addShutdownHook(thread);
    }

    private static Thread getShutdownThread() {
        var thread = new Thread(() -> {
            try {
                log.info("Annotation processing shutdown...");
                close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.setName("kora-ap-shutdown");
        return thread;
    }

    private static void initLog(ProcessingEnvironment processingEnv) {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
            return;
        }
        var ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        var kora = ctx.getLogger("ru.tinkoff.kora");
        kora.setAdditive(false);
        kora.detachAndStopAllAppenders();
        var consoleAppender = new ConsoleAppender<ILoggingEvent>();
        var fileAppender = new FileAppender<ILoggingEvent>();
        var fileName = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh-mm-ss").format(LocalDateTime.now()) + ".log";
        fileAppender.setFile(buildDir.resolve("kora").resolve("log").resolve(fileName).toString());

        var patternLayoutEncoder = new PatternLayoutEncoder();
        patternLayoutEncoder.setPattern("%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n");
        patternLayoutEncoder.setCharset(StandardCharsets.UTF_8);
        patternLayoutEncoder.setContext(ctx);
        patternLayoutEncoder.start();
        fileAppender.setEncoder(patternLayoutEncoder);
        fileAppender.setContext(ctx);
        fileAppender.start();
        consoleAppender.setEncoder(patternLayoutEncoder);
        consoleAppender.setContext(ctx);
        consoleAppender.start();
        kora.addAppender(fileAppender);
        kora.addAppender(consoleAppender);

        if (log instanceof ch.qos.logback.classic.Logger logger) {
            logger.setLevel(Level.valueOf(processingEnv.getOptions().getOrDefault("koraLogLevel", "INFO")));
        }
    }

    public static synchronized void close() {
        var ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        var kora = ctx.getLogger("ru.tinkoff.kora");
        kora.detachAndStopAllAppenders();
    }
}
