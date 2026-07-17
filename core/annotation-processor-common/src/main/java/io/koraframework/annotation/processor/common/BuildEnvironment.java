package io.koraframework.annotation.processor.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.ConsoleAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class BuildEnvironment {

    private static final Logger logger = LoggerFactory.getLogger("io.koraframework");
    private static final AtomicBoolean INIT = new AtomicBoolean(false);
    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);
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
                INIT.set(false);
                return;
            }
        } catch (IOException e) {
            INIT.set(false);
            return;
        }
        initLog(processingEnv);

        if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            var thread = getShutdownThread();
            Runtime.getRuntime().addShutdownHook(thread);
        }
    }

    private static Thread getShutdownThread() {
        var thread = new Thread(() -> {
            try {
                logger.info("Annotation processing shutdown...");
                close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.setName("kora-ap-shutdown");
        thread.setDaemon(true);
        return thread;
    }

    private static void initLog(ProcessingEnvironment processingEnv) {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
            return;
        }
        var ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        var kora = ctx.getLogger("io.koraframework");
        kora.setAdditive(false);
        kora.detachAndStopAllAppenders();
        var consoleAppender = new ConsoleAppender<ILoggingEvent>();
        var fileName = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh-mm-ss").format(LocalDateTime.now()) + ".log";
        var file = buildDir.resolve("kora").resolve("log").resolve(fileName);

        var fileAppender = new NonLockingFileAppender(file, createEncoder(ctx));
        fileAppender.setContext(ctx);
        fileAppender.start();
        var consoleEncoder = createEncoder(ctx);
        consoleEncoder.start();
        consoleAppender.setEncoder(consoleEncoder);
        consoleAppender.setContext(ctx);
        consoleAppender.start();
        kora.addAppender(fileAppender);
        kora.addAppender(consoleAppender);

        if (logger instanceof ch.qos.logback.classic.Logger logger) {
            logger.setLevel(Level.valueOf(processingEnv.getOptions().getOrDefault("koraLogLevel", "INFO")));
        }
    }

    public static synchronized void close() {
        if (!INIT.compareAndSet(true, false)) {
            return;
        }
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx)) {
            return;
        }
        var kora = ctx.getLogger("io.koraframework");
        logger.info("Logger shutdown...");
        kora.detachAndStopAllAppenders();
    }

    private static PatternLayoutEncoder createEncoder(LoggerContext ctx) {
        var encoder = new PatternLayoutEncoder();
        encoder.setPattern("%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n");
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.setContext(ctx);
        return encoder;
    }

    private static final class NonLockingFileAppender extends AppenderBase<ILoggingEvent> {
        private final Path file;
        private final PatternLayoutEncoder encoder;

        private NonLockingFileAppender(Path file, PatternLayoutEncoder encoder) {
            this.file = file;
            this.encoder = encoder;
        }

        @Override
        public void start() {
            try {
                Files.createDirectories(this.file.getParent());
            } catch (IOException e) {
                this.addError("Failed to create Kora annotation processor log directory", e);
                return;
            }
            this.encoder.start();
            super.start();
        }

        @Override
        public void stop() {
            super.stop();
            this.encoder.stop();
        }

        @Override
        protected synchronized void append(ILoggingEvent eventObject) {
            try {
                Files.write(this.file, this.encoder.encode(eventObject), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                this.addError("Failed to write Kora annotation processor log", e);
            }
        }
    }
}
