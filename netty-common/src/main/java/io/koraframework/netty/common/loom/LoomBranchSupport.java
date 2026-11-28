package io.koraframework.netty.common.loom;

import java.lang.invoke.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class LoomBranchSupport {
    private static final Supplier<VirtualThreadSchedulerProxy> DEFAULT_SCHEDULER;
    private static final Thread DEFAULT_SCHEDULER_THREAD;
    private static final MethodHandle TO_JDK_SCHEDULER;
    private static final MethodHandle TO_OUR_SCHEDULER;
    private static final MethodHandle CURRENT_SCHEDULER;
    private static final MethodHandle JDK_EXECUTE;
    private static final MethodHandle SET_SCHEDULER;
    private static final Class<?> PROXY_LAMBDA_CLASS;

    static {
        MethodHandle toJdkScheduler;
        MethodHandle toOurScheduler;
        MethodHandle currentScheduler;
        MethodHandle jdkExecute;
        MethodHandle setScheduler;
        try {
            MethodType executeMethodType = MethodType.methodType(void.class, Thread.class, Runnable.class);
            Class<?> jdkVirtualThreadScheduler = Class.forName("java.lang.Thread$VirtualThreadScheduler");

            currentScheduler = MethodHandles.lookup().findStatic(jdkVirtualThreadScheduler, "current", MethodType.methodType(jdkVirtualThreadScheduler))
                .asType(MethodType.methodType(Object.class));

            jdkExecute = MethodHandles.lookup().findVirtual(jdkVirtualThreadScheduler, "execute", executeMethodType);
            MethodHandle ourExecute = MethodHandles.lookup().findStatic(LoomBranchSupport.class, "executeOrUnwrap", MethodType.methodType(void.class, VirtualThreadSchedulerProxy.class, Thread.class, Runnable.class));

            toJdkScheduler = LambdaMetafactory.metafactory(
                MethodHandles.lookup(),
                "execute",
                MethodType.methodType(jdkVirtualThreadScheduler, VirtualThreadSchedulerProxy.class),
                executeMethodType,
                ourExecute,
                executeMethodType
            ).dynamicInvoker().asType(MethodType.methodType(Object.class, VirtualThreadSchedulerProxy.class));

            toOurScheduler = LambdaMetafactory.metafactory(
                MethodHandles.lookup(),
                "execute",
                MethodType.methodType(VirtualThreadSchedulerProxy.class, jdkVirtualThreadScheduler),
                executeMethodType,
                jdkExecute,
                executeMethodType
            ).dynamicInvoker().asType(MethodType.methodType(VirtualThreadSchedulerProxy.class, Object.class));

            Class<?> ofVirtual = Class.forName("java.lang.Thread$Builder$OfVirtual");
            setScheduler = MethodHandles.lookup().findVirtual(ofVirtual, "scheduler", MethodType.methodType(ofVirtual, jdkVirtualThreadScheduler))
                .asType(MethodType.methodType(void.class, Object.class, Object.class));
        } catch (Exception e) {
            jdkExecute = null;
            toJdkScheduler = null;
            toOurScheduler = null;
            currentScheduler = null;
            setScheduler = null;
        }

        TO_JDK_SCHEDULER = toJdkScheduler;
        TO_OUR_SCHEDULER = toOurScheduler;
        CURRENT_SCHEDULER = currentScheduler;
        JDK_EXECUTE = jdkExecute == null ? null : jdkExecute.asType(MethodType.methodType(void.class, Object.class, Thread.class, Runnable.class));
        SET_SCHEDULER = setScheduler;
        PROXY_LAMBDA_CLASS = TO_JDK_SCHEDULER != null ? toJdkScheduler(NoopSchedulerProxy.INSTANCE).getClass() : null;

        if (isSupported()) {
            FutureTask<Object> task = new FutureTask<>(CurrentSchedulerCallable.INSTANCE);
            DEFAULT_SCHEDULER_THREAD = Thread.ofVirtual().name("default-scheduler-exposer", 1L).factory().newThread(task);
            DEFAULT_SCHEDULER_THREAD.start();
            DEFAULT_SCHEDULER = new DefaultSchedulerSupplier(task);
        } else {
            DEFAULT_SCHEDULER_THREAD = null;
            DEFAULT_SCHEDULER = null;
        }
    }

    private static final class NoopSchedulerProxy implements VirtualThreadSchedulerProxy {
        private static final NoopSchedulerProxy INSTANCE = new NoopSchedulerProxy();

        @Override
        public void execute(Thread thread, Runnable runnable) {
        }
    }

    private static final class CurrentSchedulerCallable implements Callable<Object> {
        private static final CurrentSchedulerCallable INSTANCE = new CurrentSchedulerCallable();

        @Override
        public Object call() {
            return currentPrivate();
        }
    }

    private static final class DefaultSchedulerSupplier implements Supplier<VirtualThreadSchedulerProxy> {
        private final FutureTask<Object> task;
        private VirtualThreadSchedulerProxy value;

        private DefaultSchedulerSupplier(FutureTask<Object> task) {
            this.task = task;
        }

        @Override
        public synchronized VirtualThreadSchedulerProxy get() {
            if (value == null) {
                try {
                    value = toOurScheduler(task.get());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return value;
        }
    }

    public static boolean isSupported() {
        return TO_OUR_SCHEDULER != null;
    }

    public static void runOnDefaultScheduler(Runnable r) {
        DEFAULT_SCHEDULER.get().execute(DEFAULT_SCHEDULER_THREAD, r);
    }

    public static VirtualThreadSchedulerProxy currentScheduler() {
        return toOurScheduler(currentPrivate());
    }

    private static Object currentPrivate() {
        try {
            return CURRENT_SCHEDULER.invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object toJdkScheduler(VirtualThreadSchedulerProxy proxy) {
        try {
            return TO_JDK_SCHEDULER.invokeExact(proxy);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static VirtualThreadSchedulerProxy toOurScheduler(Object scheduler) {
        if (scheduler.getClass() == PROXY_LAMBDA_CLASS) {
            UnwrapClass unwrapClass = new UnwrapClass();
            try {
                JDK_EXECUTE.invokeExact(scheduler, (Thread) null, (Runnable) unwrapClass);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            return unwrapClass.proxy;
        } else {
            try {
                return (VirtualThreadSchedulerProxy) TO_OUR_SCHEDULER.invokeExact(scheduler);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void setScheduler(Object builder, VirtualThreadSchedulerProxy scheduler) {
        try {
            SET_SCHEDULER.invokeExact(builder, toJdkScheduler(scheduler));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static void executeOrUnwrap(VirtualThreadSchedulerProxy target, Thread thread, Runnable task) {
        if (task.getClass() == UnwrapClass.class) {
            ((UnwrapClass) task).proxy = target;
            return;
        }
        target.execute(thread, task);
    }

    private static final class UnwrapClass implements Runnable {
        VirtualThreadSchedulerProxy proxy;

        @Override
        public void run() {
            throw new UnsupportedOperationException();
        }
    }

    public interface VirtualThreadSchedulerProxy {
        void execute(Thread thread, Runnable task);
    }
}
