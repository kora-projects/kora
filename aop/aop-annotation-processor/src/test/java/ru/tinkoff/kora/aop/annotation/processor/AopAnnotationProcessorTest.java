package ru.tinkoff.kora.aop.annotation.processor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.Mockito;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.aop.annotation.processor.aoptarget.AopTarget1;
import ru.tinkoff.kora.aop.annotation.processor.aoptarget.AopTarget2;
import ru.tinkoff.kora.aop.annotation.processor.aoptarget.AopTarget3;
import ru.tinkoff.kora.aop.annotation.processor.aoptarget.AopTarget4;
import ru.tinkoff.kora.common.Tag;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AopAnnotationProcessorTest {

    @Test
    void testAop1() throws Exception {
        var classLoader = TestUtils.annotationProcess(AopTarget1.class, new AopAnnotationProcessor());
        var cl = classLoader.loadClass("ru.tinkoff.kora.aop.annotation.processor.aoptarget.$AopTarget1__AopProxy");
        assertThat(cl).isNotNull();
        var listener = Mockito.mock(AopTarget1.ProxyListener1.class);
        Constructor<?> constructor = cl.getConstructor(String.class, Integer.class, AopTarget1.ProxyListener1.class);
        Assertions.assertArrayEquals(constructor.getParameters()[1].getAnnotation(Tag.class).value(), new Class<?>[]{String.class});

        var instance = (AopTarget1) constructor.newInstance("test", 1, listener);

        instance.shouldNotBeProxied1();
        var m1 = ReflectionUtils.findMethod(AopTarget1.class, "shouldNotBeProxied2").get();
        ReflectionUtils.invokeMethod(m1, instance);

        verifyNoInteractions(listener);

        instance.testMethod1();
        verify(listener).call(eq("testMethod1"));

        var m2 = ReflectionUtils.findMethod(AopTarget1.class, "testMethod2", String.class).get();
        ReflectionUtils.invokeMethod(m2, instance, "arg1");
        verify(listener).call(eq("testMethod2"));
    }

    @Test
    void testAop2() throws Exception {
        var classLoader = TestUtils.annotationProcess(AopTarget2.class, new AopAnnotationProcessor());
        var cl = classLoader.loadClass("ru.tinkoff.kora.aop.annotation.processor.aoptarget.$AopTarget2__AopProxy");
        assertThat(cl).isNotNull();
        var listener = Mockito.mock(AopTarget2.ProxyListener2.class);
        var instance = (AopTarget2) cl.getConstructor(AopTarget2.ProxyListener2.class).newInstance(listener);


        var order = Mockito.inOrder(listener);
        instance.testMethod1();
        order.verify(listener).call("TestAnnotation21");
        order.verify(listener).call("TestAnnotation22");
        order.verifyNoMoreInteractions();

        Mockito.reset(listener);
        order = Mockito.inOrder(listener);
        instance.testMethod2();
        order.verify(listener).call("TestAnnotation21Method");
        order.verify(listener).call("TestAnnotation22");
        order.verifyNoMoreInteractions();

        Mockito.reset(listener);
        order = Mockito.inOrder(listener);
        instance.testMethod3();
        order.verify(listener).call("TestAnnotation22");
        order.verify(listener).call("TestAnnotation21Method");
        order.verifyNoMoreInteractions();

        instance.testMethod4("test");
        order.verify(listener).call("TestAnnotation21");
        order.verify(listener).call("TestAnnotation22Param");
        order.verifyNoMoreInteractions();
    }

    @Test
    void testAop3() throws Exception {
        var classLoader = TestUtils.annotationProcess(AopTarget3.class, new AopAnnotationProcessor());
        var cl = classLoader.loadClass("ru.tinkoff.kora.aop.annotation.processor.aoptarget.$AopTarget3__AopProxy");
        assertThat(cl).isNotNull();
        var instance = (AopTarget3) cl.getConstructor().newInstance();

        assertThat(instance.testMethod1()).isEqualTo("string1/testMethod1");
        assertThat(instance.testMethod2()).isEqualTo("string2/testMethod2");
    }

    @Test
    void testAop4() throws Exception {
        var classLoader = TestUtils.annotationProcess(AopTarget4.class, new AopAnnotationProcessor());
        var cl = classLoader.loadClass("ru.tinkoff.kora.aop.annotation.processor.aoptarget.$AopTarget4__AopProxy");
        assertThat(cl).isNotNull();
        var instance = (AopTarget4) cl.getConstructor().newInstance();

        assertThat(instance.getClass())
            .hasDeclaredMethods("_testMethod1_AopProxy_Aspect4");
    }
}
