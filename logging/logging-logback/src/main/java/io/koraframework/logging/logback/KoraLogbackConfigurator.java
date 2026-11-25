package io.koraframework.logging.logback;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.jul.*;
import ch.qos.logback.classic.spi.*;
import ch.qos.logback.classic.util.*;
import ch.qos.logback.core.*;
import ch.qos.logback.core.encoder.*;
import ch.qos.logback.core.spi.*;
import io.koraframework.logging.logback.text.ColorConsoleTextEncoderFactory;
import io.koraframework.logging.logback.text.ConsoleTextEncoderFactory;
import org.jspecify.annotations.*;
import org.slf4j.bridge.*;

import java.util.*;
import java.util.function.Consumer;

/**
 * The single Logback {@link Configurator} of Kora, discovered by Logback via {@link ServiceLoader}.
 * <p>
 * Configuration is resolved in this order:
 * <ol>
 *     <li>a Logback configuration file, when present, always wins and is applied as usual</li>
 *     <li>otherwise one {@link LogbackEncoderFactory} is selected among the ones found on the classpath and its encoder
 *     is attached to the root logger through a {@link ConsoleAppender} wrapped into a {@link KoraAsyncAppender}</li>
 * </ol>
 * The encoder is selected by {@value #ENCODER_PROPERTY}, by name, for example {@code json}. When the property is not
 * set, the factory with the highest {@link LogbackEncoderFactory#priority()} wins. Setting the encoder to
 * {@value #ENCODER_NONE} hands configuration back to Logback's own defaults.
 * <p>
 * Every property is also readable as an environment variable, see {@link KoraLogbackProperties}, so tests can be
 * switched to a human readable encoder with {@code KORA_LOGGING_ENCODER=pretty}.
 */
@ConfiguratorRank(ConfiguratorRank.NOMINAL)
public class KoraLogbackConfigurator extends ContextAwareBase implements Configurator {

    /** Name of the {@link LogbackEncoderFactory} to use, or {@value #ENCODER_NONE}. */
    public static final String ENCODER_PROPERTY = "kora.logging.encoder";
    /** Root logger level to start with. */
    public static final String ROOT_LEVEL_PROPERTY = "kora.logging.levels.root";

    /** Whether {@code java.util.logging} is routed into Logback, {@code true} by default. */
    public static final String JUL_BRIDGE_PROPERTY = "kora.logging.config.jul-bridge";

    public static final String ENCODER_NONE = "none";

    public static final String CONSOLE_APPENDER_NAME = "KORA_CONSOLE";
    public static final String ASYNC_APPENDER_NAME = "KORA_ASYNC";

    @Override
    public ExecutionStatus configure(LoggerContext context) {
        var status = this.configureLogging(context);
        this.configureJulBridge(context);
        return status;
    }

    private ExecutionStatus configureLogging(LoggerContext context) {
        context.setPackagingDataEnabled(false);
        var classLoader = KoraLogbackConfigurator.class.getClassLoader();
        if (classLoader.getResource("logback.xml") != null || classLoader.getResource("logback-test.xml") != null) {
            var joran = new DefaultJoranConfigurator();
            joran.setContext(context);
            if (joran.configure(context) == ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY) {
                this.addInfo("Logback configuration file was applied, Kora encoder selection is skipped");
                return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY;
            }
        }

        context.getLogger("org.flywaydb").setLevel(Level.WARN);

        var selected = KoraLogbackProperties.get(ENCODER_PROPERTY);
        if (selected != null && selected.equalsIgnoreCase(ENCODER_NONE)) {
            this.addInfo(ENCODER_PROPERTY + "=" + ENCODER_NONE + ", falling back to Logback defaults");
            return ExecutionStatus.INVOKE_NEXT_IF_ANY;
        }

        var factory = this.selectFactory(this.encoderFactories(), selected);
        if (factory == null) {
            return ExecutionStatus.INVOKE_NEXT_IF_ANY;
        }

        this.addInfo("Logging with " + factory.name() + " encoder from " + factory.getClass().getCanonicalName());
        this.configureDefault(context, factory);
        return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY;
    }

    /**
     * Routes {@code java.util.logging} into Logback, whatever configured Logback itself.
     * <p>
     * The default console handler of the JUL root logger is removed so records are not printed twice, and a
     * {@link LevelChangePropagator} mirrors Logback levels into JUL. Without it JUL keeps its own {@code INFO} root
     * level and filters records before the bridge ever sees them, so a library logging through JUL could not be turned
     * to {@code DEBUG} from Logback, and every disabled record would still be built only to be dropped later.
     * <p>
     * Both steps are skipped when already done, so a configuration file installing the bridge itself does not get
     * every record twice. Disabled with {@value #JUL_BRIDGE_PROPERTY}{@code =false}.
     */
    public void configureJulBridge(LoggerContext context) {
        if (!KoraLogbackProperties.getBoolean(JUL_BRIDGE_PROPERTY, true, new WarnConsumer(this))) {
            this.addInfo(JUL_BRIDGE_PROPERTY + "=false, java.util.logging is left as it is");
            return;
        }

        var hasPropagator = false;
        var listeners = context.getCopyOfListenerList();
        for (int i = 0; i < listeners.size(); i++) {
            if (listeners.get(i) instanceof LevelChangePropagator) {
                hasPropagator = true;
                break;
            }
        }

        if (!hasPropagator) {
            var propagator = new LevelChangePropagator();
            propagator.setContext(context);
            propagator.setResetJUL(true);
            propagator.start();
            context.addListener(propagator);
        }

        if (!SLF4JBridgeHandler.isInstalled()) {
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
            this.addInfo("java.util.logging is routed into Logback");
        }
    }

    /**
     * Installs the Kora logging pipeline into the given context, ignoring any Logback configuration file.
     */
    public void configureDefault(LoggerContext context, LogbackEncoderFactory factory) {
        var encoder = factory.create(context);
        encoder.setContext(context);
        if (!encoder.isStarted()) {
            encoder.start();
        }

        var appender = this.createAppender(context, encoder);
        var root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(this.rootLevel());
        root.addAppender(appender);
    }

    protected List<LogbackEncoderFactory> encoderFactories() {
        var factories = new ArrayList<LogbackEncoderFactory>();
        var loader = ServiceLoader.load(LogbackEncoderFactory.class, this.getClass().getClassLoader());
        for (var factory : loader) {
            factories.add(factory);
        }
        return factories;
    }

    /**
     * @return factory to log with, or {@code null} when the selection can not be satisfied
     */
    @Nullable
    public LogbackEncoderFactory selectFactory(List<LogbackEncoderFactory> factories, @Nullable String selected) {
        if (factories.isEmpty()) {
            this.addWarn("No " + LogbackEncoderFactory.class.getSimpleName() + " was found on classpath");
            return null;
        }

        if (selected != null) {
            for (int i = 0; i < factories.size(); i++) {
                var factory = factories.get(i);
                if (factory.name().equalsIgnoreCase(selected)) {
                    return factory;
                }
            }
            this.addError(ENCODER_PROPERTY + "=" + selected + " is unknown, available encoders are: " + names(factories));
            return null;
        }

        var bestMatch = factories.getFirst();
        for (int i = 1; i < factories.size(); i++) {
            var current = factories.get(i);
            var p1 = current.priority();
            var p2 = bestMatch.priority();

            if (p1 > p2) {
                bestMatch = current;
            } else if (p1 == p2) {
                if (current.name().compareTo(bestMatch.name()) < 0) {
                    bestMatch = current;
                }
            }
        }

        return bestMatch;
    }

    protected Level rootLevel() {
        return Level.toLevel(KoraLogbackProperties.get(ROOT_LEVEL_PROPERTY, Level.INFO.levelStr), Level.INFO);
    }

    protected Appender<ILoggingEvent> createAppender(LoggerContext context, Encoder<ILoggingEvent> encoder) {
        var consoleAppender = new ConsoleAppender<ILoggingEvent>();
        consoleAppender.setContext(context);
        consoleAppender.setName(CONSOLE_APPENDER_NAME);
        consoleAppender.setEncoder(encoder);
        consoleAppender.start();

        var asyncAppender = new KoraAsyncAppender();
        asyncAppender.setContext(context);
        asyncAppender.setName(ASYNC_APPENDER_NAME);
        asyncAppender.addAppender(consoleAppender);
        asyncAppender.start();
        return asyncAppender;
    }

    private static String names(List<LogbackEncoderFactory> factories) {
        var nameList = new ArrayList<String>(factories.size());
        for (int i = 0; i < factories.size(); i++) {
            nameList.add(factories.get(i).name());
        }
        Collections.sort(nameList);
        return nameList.toString();
    }

    private static final class WarnConsumer implements Consumer<String> {
        private final KoraLogbackConfigurator configurator;

        WarnConsumer(KoraLogbackConfigurator configurator) {
            this.configurator = configurator;
        }

        @Override
        public void accept(String message) {
            this.configurator.addWarn(message);
        }
    }
}
