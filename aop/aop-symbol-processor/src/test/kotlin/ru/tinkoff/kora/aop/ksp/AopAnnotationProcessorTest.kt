package ru.tinkoff.kora.aop.ksp

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.platform.commons.util.ReflectionUtils
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import ru.tinkoff.kora.aop.ksp.aoptarget.AopTarget1
import ru.tinkoff.kora.aop.ksp.aoptarget.AopTarget2
import ru.tinkoff.kora.aop.ksp.aoptarget.AopTarget3
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.ksp.common.symbolProcess

@KspExperimental
class AopAnnotationProcessorTest {
    @Test
    @Throws(Exception::class)
    fun testAop1() {
        val classLoader: ClassLoader = symbolProcess(AopTarget1::class, AopSymbolProcessorProvider())
        val cl = classLoader.loadClass("ru.tinkoff.kora.aop.ksp.aoptarget.\$AopTarget1__AopProxy")
        Assertions.assertNotNull(cl)
        val listener: AopTarget1.ProxyListener1 = Mockito.mock(AopTarget1.ProxyListener1::class.java)
        val constructor = cl.getConstructor(String::class.java, Int::class.javaPrimitiveType, AopTarget1.ProxyListener1::class.java)
        val tagAnnotation = constructor.parameters[1].annotations[0] as Tag
        Assertions.assertArrayEquals(tagAnnotation.value, arrayOf(String::class))

        val instance: AopTarget1 = constructor.newInstance("test", 1, listener) as AopTarget1
        instance.shouldNotBeProxied1()
        val m1 = ReflectionUtils.findMethod(AopTarget1::class.java, "shouldNotBeProxied2").get()
        ReflectionUtils.invokeMethod(m1, instance)
        Mockito.verifyNoInteractions(listener)
        instance.testMethod1()
        Mockito.verify(listener).call(ArgumentMatchers.eq("testMethod1"))
        val m2 = ReflectionUtils.findMethod(AopTarget1::class.java, "testMethod2", String::class.java).get()
        ReflectionUtils.invokeMethod(m2, instance, "arg1")
        Mockito.verify(listener).call(ArgumentMatchers.eq("testMethod2"))
    }

    @Test
    @Throws(Exception::class)
    fun testAop2() {
        val classLoader: ClassLoader = symbolProcess(AopTarget2::class, AopSymbolProcessorProvider())
        val cl = classLoader.loadClass("ru.tinkoff.kora.aop.ksp.aoptarget.\$AopTarget2__AopProxy")
        Assertions.assertNotNull(cl)
        val listener: AopTarget2.ProxyListener2 = Mockito.mock(AopTarget2.ProxyListener2::class.java)
        val instance: AopTarget2 = cl.getConstructor(AopTarget2.ProxyListener2::class.java).newInstance(listener) as AopTarget2
        var order = Mockito.inOrder(listener)
        instance.testMethod1()
        order.verify(listener).call("TestAnnotation21")
        order.verify(listener).call("TestAnnotation22")
        order.verifyNoMoreInteractions()
        Mockito.reset(listener)
        order = Mockito.inOrder(listener)
        instance.testMethod2()
        order.verify(listener).call("TestAnnotation21Method")
        order.verify(listener).call("TestAnnotation22")
        order.verifyNoMoreInteractions()
        Mockito.reset(listener)
        order = Mockito.inOrder(listener)
        instance.testMethod3()
        order.verify(listener).call("TestAnnotation22")
        order.verify(listener).call("TestAnnotation21Method")
        order.verifyNoMoreInteractions()
        instance.testMethod4("test")
        order.verify(listener).call("TestAnnotation21")
        order.verify(listener).call("TestAnnotation22Param")
        order.verifyNoMoreInteractions()
    }

    @Test
    @Throws(Exception::class)
    fun testAop3() {
        val classLoader: ClassLoader = symbolProcess(AopTarget3::class, AopSymbolProcessorProvider())
        val cl = classLoader.loadClass("ru.tinkoff.kora.aop.ksp.aoptarget.\$AopTarget3__AopProxy")
        Assertions.assertNotNull(cl)
        val instance: AopTarget3 = cl.getConstructor().newInstance() as AopTarget3
        Assertions.assertEquals(instance.testMethod1(), "string1/testMethod1")
        Assertions.assertEquals(instance.testMethod2(), "string2/testMethod2")
    }
}
