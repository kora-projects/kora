package ru.tinkoff.kora.logging.aspect;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.slf4j.event.Level.*;

public class LogAspectTest extends AbstractLogAspectTest {

    @Test
    public void testLogPrintsInAndOut() {
        var aopProxy = compile("""
            public class Target {
              @Log
              public void test() {}
            }
            """);


        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

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
              public void test() {}
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

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
              public void test() {}
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

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
              public void test(String arg1, @Log(TRACE) String arg2, @Log.off String arg3, int arg4, boolean arg5) {}
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        aopProxy.invoke("test", "test1", "test2", "test3", 1, true);
        var o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(">");
        o.verifyNoMoreInteractions();

        reset(log, DEBUG);
        aopProxy.invoke("test", "test1", "test2", "test3", 1, true);
        o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(inData.capture(), eq(">"));
        o.verifyNoMoreInteractions();
        verifyInData(Map.of("arg1", "test1", "arg4", "1", "arg5", "true"));
        o.verify(log).isTraceEnabled();
        o.verifyNoMoreInteractions();

        reset(log, TRACE);
        aopProxy.invoke("test", "test1", "test2", "test3", 1, true);
        o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(inData.capture(), eq(">"));
        o.verifyNoMoreInteractions();
        verifyInData(Map.of("arg1", "test1", "arg2", "test2", "arg4", "1", "arg5", "true"));
        o.verify(log).isTraceEnabled();
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogArgsSameLevelAsIn() {
        var aopProxy = compile("""
            public class Target {
              @Log.in
              public void test(@Log(INFO) String arg1, @Log(TRACE) String arg2, @Log.off String arg3) {}
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        aopProxy.invoke("test", "test1", "test2", "test3");
        var o = Mockito.inOrder(log);
        o.verify(log).isInfoEnabled();
        o.verify(log).info(inData.capture(), eq(">"));
        o.verifyNoMoreInteractions();
        verifyInData(Map.of("arg1", "test1"));
        o.verify(log).isTraceEnabled();
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogResults() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              public String test() { return "test-result"; }
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
    public void testLogResultsPrimitive() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              public int test() { return 1; }
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
        verifyOutData(Map.of("out", "1"));
    }

    @Test
    public void testLogResultsOff() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              @Log.off
              public String test() { return "test-result"; }
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
              public String test() { return "test-result"; }
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

    @Test
    public void testLogArgsWithMapper() {
        compile(List.of(new AopAnnotationProcessor()), """
            public class Target {
              @Log.in
              public void test(@Mapping(MyLogArgMapper.class) String arg1) {}
            }
            """, """
            import ru.tinkoff.kora.logging.common.arg.StructuredArgumentMapper;
            import com.fasterxml.jackson.core.JsonGenerator;

            public final class MyLogArgMapper implements StructuredArgumentMapper<String> {
                public void write(JsonGenerator gen, String value) throws java.io.IOException {
                  gen.writeString("mapped-" + value);
                }
            }
            """);
        compileResult.assertSuccess();
        var aopProxy = new TestObject(
            compileResult.loadClass("$Target__AopProxy"),
            newObject("$Target__AopProxy", factory, newObject("MyLogArgMapper"))
        );
        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, DEBUG);
        aopProxy.invoke("test", "value");
        var o = Mockito.inOrder(log);
        o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(inData.capture(), eq(">"));
        o.verifyNoMoreInteractions();
        verifyInData(Map.of("arg1", "mapped-value"));
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogArgsWithGenericMapper() {
        compile(List.of(new AopAnnotationProcessor()), """
            public class Target {
              @Log.in
              public void test(@Mapping(MyLogArgMapper.class) String arg1) {}
            }
            """, """
            import ru.tinkoff.kora.logging.common.arg.StructuredArgumentMapper;
            import com.fasterxml.jackson.core.JsonGenerator;

            public final class MyLogArgMapper <T> implements StructuredArgumentMapper<T> {
                public void write(JsonGenerator gen, T value) throws java.io.IOException {
                  gen.writeString("mapped-" + value);
                }
            }
            """);
        compileResult.assertSuccess();
        var aopProxy = new TestObject(
            compileResult.loadClass("$Target__AopProxy"),
            newObject("$Target__AopProxy", factory, newObject("MyLogArgMapper"))
        );
        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, DEBUG);
        aopProxy.invoke("test", "value");
        var o = Mockito.inOrder(log);
        o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(inData.capture(), eq(">"));
        o.verifyNoMoreInteractions();
        verifyInData(Map.of("arg1", "mapped-value"));
        o.verifyNoMoreInteractions();
    }

}
