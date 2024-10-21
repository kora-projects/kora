package ru.tinkoff.kora.aop.annotation.processor;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AopAnnotationProcessorTest extends AbstractAnnotationProcessorTest {

    @Test
    public void testAopBeforeAndAfterCalled() {
        compile(List.of(new AopAnnotationProcessor()), """
            public class AopTarget {
                @ru.tinkoff.kora.aop.annotation.processor.TestAnnotation1("testAopBeforeAndAfterCalled")
                public void test() {}
            }
            """);
        assertSuccess();
        var aopTarget = loadClass("AopTarget");
        var aopProxy = loadClass("$AopTarget__AopProxy");

        assertThat(aopTarget).isNotNull();
        assertThat(aopProxy).isNotNull().isAssignableTo(aopTarget);

        var listener = Mockito.mock(TestMethodCallListener.class);

        var testObject = newObject("$AopTarget__AopProxy", listener);
        invoke(testObject, "test");

        var order = Mockito.inOrder(listener);
        order.verify(listener).before("testAopBeforeAndAfterCalled");
        order.verify(listener).after(eq("testAopBeforeAndAfterCalled"), isNull());
        order.verify(listener, never()).thrown(any(), any());
        order.verifyNoMoreInteractions();
    }

    @Test
    public void testNotAnnotatedMethodsNotProxied() {
        compile(List.of(new AopAnnotationProcessor()), """
            public class AopTarget {
                public void test1() {}
                @ru.tinkoff.kora.aop.annotation.processor.TestAnnotation1("testNotAnnotatedMethodsNotProxied")
                public void test2() {}
            }
            """);
        assertSuccess();
        var aopProxy = loadClass("$AopTarget__AopProxy");

        var methods = Stream.of(aopProxy.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers())).toList();

        assertThat(methods).hasSize(1);
        assertThat(methods.get(0).getName()).isEqualTo("test2");
    }

    @Test
    public void testClassLevelAspectsApplied() {
        compile(List.of(new AopAnnotationProcessor()), """
            @ru.tinkoff.kora.aop.annotation.processor.TestAnnotation1("testClassLevelAspectsApplied")
            public class AopTarget {
                public void test1() {}
                public void test2() {}
            }
            """);
        assertSuccess();
        var aopProxy = loadClass("$AopTarget__AopProxy");

        var methods = Stream.of(aopProxy.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers())).toList();
        assertThat(methods).hasSize(2);

        var listener = Mockito.mock(TestMethodCallListener.class);

        var testObject = newObject("$AopTarget__AopProxy", listener);
        invoke(testObject, "test1");
        invoke(testObject, "test2");

        var order = Mockito.inOrder(listener);
        order.verify(listener).before("testClassLevelAspectsApplied");
        order.verify(listener).after(eq("testClassLevelAspectsApplied"), isNull());
        order.verify(listener).before("testClassLevelAspectsApplied");
        order.verify(listener).after(eq("testClassLevelAspectsApplied"), isNull());
        order.verify(listener, never()).thrown(any(), any());
        order.verifyNoMoreInteractions();
    }

    @Test
    public void testParameterLevelAspectApplied() {
        compile(List.of(new AopAnnotationProcessor()), """
            public class AopTarget {
                public void test(@ru.tinkoff.kora.aop.annotation.processor.TestAnnotation1("testParameterLevelAspectApplied") String param) {}
            }
            """);
        assertSuccess();

        var listener = Mockito.mock(TestMethodCallListener.class);

        var testObject = newObject("$AopTarget__AopProxy", listener);
        invoke(testObject, "test", "param");

        var order = Mockito.inOrder(listener);
        order.verify(listener).before("testParameterLevelAspectApplied");
        order.verify(listener).after(eq("testParameterLevelAspectApplied"), isNull());
        order.verify(listener, never()).thrown(any(), any());
        order.verifyNoMoreInteractions();
    }

    @Test
    public void testMethodLevelAnnotationAppliedInCorrectOrder() {
        compile(List.of(new AopAnnotationProcessor()), """
            public class AopTarget {
                @ru.tinkoff.kora.aop.annotation.processor.TestAnnotation1("TestAnnotation1")
                @ru.tinkoff.kora.aop.annotation.processor.TestAnnotation2("TestAnnotation2")
                public void test() {}
            }
            """);
        assertSuccess();

        var listener = Mockito.mock(TestMethodCallListener.class);

        var testObject = newObject("$AopTarget__AopProxy", listener);
        invoke(testObject, "test");

        var order = Mockito.inOrder(listener);
        order.verify(listener).before("TestAnnotation1");
        order.verify(listener).before("TestAnnotation2");
        order.verify(listener).after(eq("TestAnnotation2"), isNull());
        order.verify(listener).after(eq("TestAnnotation1"), isNull());
        order.verify(listener, never()).thrown(any(), any());
        order.verifyNoMoreInteractions();
    }

    @Test
    public void testClassAndMethodLevelAspectAppliedInCorrectOrder() {
        compile(List.of(new AopAnnotationProcessor()), """
            @ru.tinkoff.kora.aop.annotation.processor.TestAnnotation2("TestAnnotation2")
            public class AopTarget {
                @ru.tinkoff.kora.aop.annotation.processor.TestAnnotation1("TestAnnotation1")
                @ru.tinkoff.kora.aop.annotation.processor.TestAnnotation2("TestAnnotation2")
                public void classLevelOverriden() {}
            
                @ru.tinkoff.kora.aop.annotation.processor.TestAnnotation1("TestAnnotation1")
                public void classLevelFirst() {}
            }
            """);
        assertSuccess();

        var listener = Mockito.mock(TestMethodCallListener.class);

        var testObject = newObject("$AopTarget__AopProxy", listener);
        invoke(testObject, "classLevelOverriden");

        var order = Mockito.inOrder(listener);
        order.verify(listener).before("TestAnnotation1");
        order.verify(listener).before("TestAnnotation2");
        order.verify(listener).after(eq("TestAnnotation2"), isNull());
        order.verify(listener).after(eq("TestAnnotation1"), isNull());
        order.verify(listener, never()).thrown(any(), any());
        order.verifyNoMoreInteractions();
        Mockito.reset(listener);

        invoke(testObject, "classLevelFirst");

        order = Mockito.inOrder(listener);
        order.verify(listener).before("TestAnnotation2");
        order.verify(listener).before("TestAnnotation1");
        order.verify(listener).after(eq("TestAnnotation1"), isNull());
        order.verify(listener).after(eq("TestAnnotation2"), isNull());
        order.verify(listener, never()).thrown(any(), any());
        order.verifyNoMoreInteractions();
    }

    @Test
    public void testTaggedConstructorParamsPropagetedToProxy() {
        compile(List.of(new AopAnnotationProcessor()), """
            public class AopTarget {
                public AopTarget(@Nullable  String arg1, @Tag(String.class) Integer arg2){}
                @ru.tinkoff.kora.aop.annotation.processor.TestAnnotation1("test")
                public void test() {}
            }
            """);
        assertSuccess();
        var aopTarget = loadClass("AopTarget");
        var aopProxy = loadClass("$AopTarget__AopProxy");

        var targetConstructor = aopTarget.getConstructors()[0];
        var aopProxyConstructor = aopProxy.getConstructors()[0];
        for (int i = 0; i < targetConstructor.getParameterCount(); i++) {
            var targetParam = targetConstructor.getParameters()[i];
            var proxyParam = aopProxyConstructor.getParameters()[i];
            assertThat(targetParam.getAnnotations()).containsExactly(proxyParam.getAnnotations());
        }
    }

    @Test
    public void componentAnnotationPropagadedOnProxy() {
        compile(List.of(new AopAnnotationProcessor()), """
            @Component
            public class AopTarget {
                @ru.tinkoff.kora.aop.annotation.processor.TestAnnotation1("test")
                public void test() {}
            }
            """);
        assertSuccess();
        var aopTarget = loadClass("AopTarget");
        var aopProxy = loadClass("$AopTarget__AopProxy");

        assertThat(aopProxy.getAnnotations()).contains(aopTarget.getAnnotations());
    }

    @Test
    public void interfacesAreNotBeingProcessedByAopProcessor() {
        compile(List.of(new AopAnnotationProcessor()), """
            public interface AopTarget {
                @ru.tinkoff.kora.aop.annotation.processor.TestAnnotation1("test")
                void test();
            }
            """);
        assertSuccess();

        var aopTarget = loadClass("AopTarget");

        assertThatThrownBy(() -> loadClass("$AopTarget__AopProxy"))
            .isInstanceOf(IllegalStateException.class)
            .hasCauseExactlyInstanceOf(ClassNotFoundException.class);
    }
}
