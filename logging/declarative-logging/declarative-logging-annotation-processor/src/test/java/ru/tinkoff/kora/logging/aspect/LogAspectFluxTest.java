package ru.tinkoff.kora.logging.aspect;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.slf4j.event.Level.*;

public class LogAspectFluxTest extends AbstractLogAspectTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import reactor.core.publisher.Flux;
            """;
    }

    @Test
    public void testLogPrintsInAndOut() {
        var aopProxy = compile("""
            public class Target {
              @Log
              public Flux<String> test() { return Flux.empty(); }
            }
            """);


        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));
        reset(log, INFO);

        aopProxy.invoke("test");

        var o = Mockito.inOrder(log);
        o.verify(log).info(">");
        o.verify(log).info("<");
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogPrintsInAndOutWhenExceptionInWarn() {
        var aopProxy = compile("""
            public class Target {
              @Log
              public Flux<Void> test() { 
                return Flux.error(new RuntimeException("OPS")); 
              }
            }
            """);


        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        mockLevel(log, WARN);
        var o = Mockito.inOrder(log);
        assertThrows(RuntimeException.class, () -> ((Flux<?>) aopProxy.invoke("test")).blockFirst());
        o.verify(log).info(">");
        o.verify(log).warn(outData.capture(), eq("<"));
        verifyOutData(Map.of("errorType", "java.lang.RuntimeException",
            "errorMessage", "OPS"));
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogPrintsInAndOutWhenExceptionInDebug() {
        var aopProxy = compile("""
            public class Target {
              @Log
              public Flux<Void> test() { 
                return Flux.error(new RuntimeException("OPS")); 
              }
            }
            """);


        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        mockLevel(log, DEBUG);
        var o = Mockito.inOrder(log);
        assertThrows(RuntimeException.class, () -> ((Flux<?>) aopProxy.invoke("test")).blockFirst());
        o.verify(log).info(">");
        o.verify(log).warn(outData.capture(), eq("<"), any(Throwable.class));
        verifyOutData(Map.of("errorType", "java.lang.RuntimeException",
            "errorMessage", "OPS"));
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogPrintsOutWhenExceptionInWarn() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              public Flux<String> test() { 
                return Flux.error(new RuntimeException("OPS")); 
              }
            }
            """);


        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        mockLevel(log, WARN);
        var o = Mockito.inOrder(log);
        assertThrows(RuntimeException.class, () -> ((Flux<?>) aopProxy.invoke("test")).blockFirst());
        o.verify(log).warn(outData.capture(), eq("<"));
        verifyOutData(Map.of("errorType", "java.lang.RuntimeException",
            "errorMessage", "OPS"));
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogPrintsOutWhenExceptionInDebug() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              public Flux<String> test() { 
                return Flux.error(new RuntimeException("OPS")); 
              }
            }
            """);


        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        mockLevel(log, DEBUG);
        var o = Mockito.inOrder(log);
        assertThrows(RuntimeException.class, () -> ((Flux<?>) aopProxy.invoke("test")).blockFirst());
        o.verify(log).warn(outData.capture(), eq("<"), any(Throwable.class));
        verifyOutData(Map.of("errorType", "java.lang.RuntimeException",
            "errorMessage", "OPS"));
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogInPrintsIn() {
        var aopProxy = compile("""
            public class Target {
              @Log.in
              public Flux<Void> test() { return Flux.empty(); }
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));
        reset(log, INFO);

        aopProxy.invoke("test");

        var o = Mockito.inOrder(log);
        o.verify(log).info(">");
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogOutPrintsOut() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              public Flux<Void> test() { return Flux.empty(); }
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));
        reset(log, INFO);

        aopProxy.invoke("test");

        var o = Mockito.inOrder(log);
        o.verify(log).info("<");
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogArgs() {
        var aopProxy = compile("""
            public class Target {
              @Log.in
              public Flux<Void> test(String arg1, @Log(TRACE) String arg2, @Log.off String arg3) { return Flux.empty(); }
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        aopProxy.invoke("test", "test1", "test2", "test3");
        var o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(">");
        o.verifyNoMoreInteractions();

        reset(log, DEBUG);
        aopProxy.invoke("test", "test1", "test2", "test3");
        o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(inData.capture(), eq(">"));
        o.verifyNoMoreInteractions();
        verifyInData(Map.of("arg1", "test1"));
        o.verify(log).isTraceEnabled();
        o.verifyNoMoreInteractions();

        reset(log, TRACE);
        aopProxy.invoke("test", "test1", "test2", "test3");
        o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(inData.capture(), eq(">"));
        o.verifyNoMoreInteractions();
        verifyInData(Map.of("arg1", "test1", "arg2", "test2"));
        o.verify(log).isTraceEnabled();
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogResults() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              public Flux<String> test() { return Flux.just("test-result"); }
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        var o = Mockito.inOrder(log);
        aopProxy.invoke("test");
        o.verify(log).isInfoEnabled();
        o.verify(log).info("<<<");
        o.verify(log).info("<");
        o.verifyNoMoreInteractions();

        reset(log, DEBUG);
        aopProxy.invoke("test");
        o = Mockito.inOrder(log);
        o.verify(log).isInfoEnabled();
        o.verify(log).info(outData.capture(), eq("<<<"));
        verifyOutData(Map.of("out", "test-result"));
        o.verify(log).info(eq("<"));
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogResultsOff() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              @Log.off
              public Flux<String> test() { return Flux.just("test-result"); }
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        var o = Mockito.inOrder(log);
        aopProxy.invoke("test");
        o.verify(log).isInfoEnabled();
        o.verify(log).info("<");
        o.verifyNoMoreInteractions();

        reset(log, DEBUG);
        o = Mockito.inOrder(log);
        aopProxy.invoke("test");
        o.verify(log).isInfoEnabled();
        o.verify(log).info("<");
        o.verifyNoMoreInteractions();
    }

    @Test
    public void logResultSameLevelAsOut() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              @Log.result(INFO)
              public Flux<String> test() { return Flux.just("test-result"); }
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        var o = Mockito.inOrder(log);
        aopProxy.invoke("test");
        o.verify(log, VerificationModeFactory.calls(2)).isInfoEnabled();
        o.verify(log).info(outData.capture(), eq("<<<"));
        verifyOutData(Map.of("out", "test-result"));
        o.verify(log).info("<");
        o.verifyNoMoreInteractions();
    }
}
