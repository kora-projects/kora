package ru.tinkoff.kora.logging.aspect;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.slf4j.event.Level.*;

public class LogAspectMonoTest extends AbstractLogAspectTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import reactor.core.publisher.Mono;
            """;
    }

    @Test
    public void testLogPrintsInAndOut() {
        var aopProxy = compile("""
            public class Target {
              @Log
              public Mono<String> test() { return Mono.empty(); }
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
              public Mono<Void> test() { 
                return Mono.error(new RuntimeException("OPS")); 
              }
            }
            """);


        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        mockLevel(log, WARN);
        var o = Mockito.inOrder(log);
        assertThrows(RuntimeException.class, () -> ((Mono<?>) aopProxy.invoke("test")).block());
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
              public Mono<Void> test() { 
                return Mono.error(new RuntimeException("OPS")); 
              }
            }
            """);


        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        mockLevel(log, DEBUG);
        var o = Mockito.inOrder(log);
        assertThrows(RuntimeException.class, () -> ((Mono<?>) aopProxy.invoke("test")).block());
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
              public Mono<String> test() { 
                return Mono.error(new RuntimeException("OPS")); 
              }
            }
            """);


        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        mockLevel(log, WARN);
        var o = Mockito.inOrder(log);
        assertThrows(RuntimeException.class, () -> ((Mono<?>) aopProxy.invoke("test")).block());
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
              public Mono<String> test() { 
                return Mono.error(new RuntimeException("OPS")); 
              }
            }
            """);


        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        mockLevel(log, DEBUG);
        var o = Mockito.inOrder(log);
        assertThrows(RuntimeException.class, () -> ((Mono<?>) aopProxy.invoke("test")).block());
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
              public Mono<Void> test() { return Mono.empty(); }
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
              public Mono<Void> test() { return Mono.empty(); }
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
              public Mono<Void> test(String arg1, @Log(TRACE) String arg2, @Log.off String arg3) { return Mono.empty(); }
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
              public Mono<String> test() { return Mono.just("test-result"); }
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        aopProxy.invoke("test");
        var o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info("<");
        o.verifyNoMoreInteractions();

        reset(log, DEBUG);
        aopProxy.invoke("test");
        o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(outData.capture(), eq("<"));
        o.verifyNoMoreInteractions();
        verifyOutData(Map.of("out", "test-result"));
    }

    @Test
    public void testLogResultsOff() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              @Log.off
              public Mono<String> test() { return Mono.just("test-result"); }
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        aopProxy.invoke("test");
        var o = Mockito.inOrder(log);
        o.verify(log).info("<");
        o.verifyNoMoreInteractions();

        reset(log, DEBUG);
        aopProxy.invoke("test");
        o = Mockito.inOrder(log);
        o.verify(log).info("<");
        o.verifyNoMoreInteractions();
    }

    @Test
    public void logResultSameLevelAsOut() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              @Log.result(INFO)
              public Mono<String> test() { return Mono.just("test-result"); }
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        aopProxy.invoke("test");
        var o = Mockito.inOrder(log);
        o.verify(log).info(outData.capture(), eq("<"));
        o.verifyNoMoreInteractions();
        verifyOutData(Map.of("out", "test-result"));
    }
}
