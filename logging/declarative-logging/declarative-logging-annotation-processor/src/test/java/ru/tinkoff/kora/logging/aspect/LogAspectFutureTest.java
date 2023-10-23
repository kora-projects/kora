package ru.tinkoff.kora.logging.aspect;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Objects;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.slf4j.event.Level.*;

public class LogAspectFutureTest extends AbstractLogAspectTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import java.util.concurrent.CompletionStage;
            import java.util.concurrent.CompletableFuture;
            """;
    }

    @Test
    public void testLogPrintsInAndOut() {
        var aopProxy = compile("""
            public class Target {
              @Log
              public CompletionStage<String> test() { return CompletableFuture.completedFuture(null); }
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
    public void testLogInPrintsIn() {
        var aopProxy = compile("""
            public class Target {
              @Log.in
              public CompletionStage<Void> test() { return CompletableFuture.completedFuture(null); }
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
              public CompletionStage<Void> test() { return CompletableFuture.completedFuture(null); }
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
              public CompletionStage<Void> test(String arg1, @Log(TRACE) String arg2, @Log.off String arg3) { return CompletableFuture.completedFuture(null); }
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
              public CompletionStage<String> test() { return CompletableFuture.completedFuture("test-result"); }
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
              public CompletionStage<String> test() { return CompletableFuture.completedFuture("test-result"); }
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
              public CompletionStage<String> test() { return CompletableFuture.completedFuture("test-result"); }
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
