package io.koraframework.netty.common.loom;

import io.koraframework.netty.common.loom.queue.*;
import io.netty.channel.*;
import io.netty.util.*;
import io.netty.util.concurrent.*;
import io.netty.util.internal.*;
import org.jspecify.annotations.*;

import java.lang.invoke.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.function.*;

public final class LoomCarrierGroup extends MultiThreadIoEventLoopGroup {

    Runner[] runners;

    private LoomCarrierGroup(Factory factory, int nThreads, Executor executor, IoHandlerFactory ioHandlerFactory) {
        super(nThreads, executor, ioHandlerFactory, factory, nThreads);
    }

    @Override
    protected IoEventLoop newChild(Executor executor, IoHandlerFactory ioHandlerFactory, Object... args) {
        var factory = (Factory) args[0];
        if (runners == null) {
            runners = new Runner[(int) args[1]];
        }
        int index = 0;
        while (index < runners.length && runners[index] != null) {
            index++;
        }
        var runner = new Runner(index, factory, ioHandlerFactory);
        runners[index] = runner;
        executor.execute(runner);
        return runner.delegate;
    }

    private static Thread unstartedVirtualThread(String name, Consumer<Thread.Builder.OfVirtual> builderModifier, Runnable task) {
        var builder = Thread.ofVirtual().name(name);
        builderModifier.accept(builder);
        return builder.unstarted(task);
    }

    public static final class Factory {
        final EventLoopLoomFactory holder;
        final LoomCarrierConfig config;

        Factory(EventLoopLoomFactory holder, LoomCarrierConfig config) {
            this.holder = holder;
            this.config = config;
        }

        public EventLoopGroup create(int nThreads, Executor executor, IoHandlerFactory ioHandlerFactory) {
            return new LoomCarrierGroup(this, nThreads, executor, ioHandlerFactory);
        }
    }

    final class Runner implements Runnable, EventLoopVirtualThreadScheduler, ThreadFactory, Executor, LoomBranchSupport.VirtualThreadSchedulerProxy {

        private static final VarHandle ACTIVE_THREADS_LOCAL;

        static {
            try {
                ACTIVE_THREADS_LOCAL = MethodHandles.lookup()
                    .findVarHandle(Runner.class, "activeThreadsLocal", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        final int id;
        final Factory factory;
        final ManualIoEventLoop delegate;
        final StickyScheduler stickyScheduler = new StickyScheduler(this);
        final IoScheduler ioScheduler = new IoScheduler(this);
        final AttributeMap attributeMap = new DefaultAttributeMap();

        final String workerThreadName;
        final long timeSliceLatencyNanos;
        final long timeSliceThroughputNanos;
        final long fifoSwitchTimeNanos;
        final long taskFifoThresholdNanos;
        final long blockTimeNanos;
        final int throughputModeThreshold;
        final int workSpillThreshold;
        final long workSpillMinQueueAgeNanos;

        IoHandler backingHandler;
        Thread carrier;
        Runnable ioContinuation;
        Thread ioThread;
        volatile boolean ioContinuationScheduled;
        final Queue<Runnable> globalLoomQueue;
        final LocalTaskQueue localLoomQueue;
        volatile boolean loomNested;
        volatile boolean expediteWrite = false;
        volatile boolean block;
        boolean continuationsFifo = false;
        long continuationTime = 0L;
        boolean throughputMode = false;
        volatile boolean idle = false;
        int activeThreadsLocal = 0;
        final AtomicInteger activeThreadsExternal = new AtomicInteger();
        volatile boolean drained = false;
        final AtomicInteger enqueuing = new AtomicInteger();
        final Consumer<Thread.Builder.OfVirtual> builderConfigurer = new VirtualThreadBuilderConfigurer();

        int warmupTasks;

        Runner(int id, Factory factory, IoHandlerFactory ioHandlerFactory) {
            this.id = id;
            this.factory = factory;
            var cfg = factory.config;
            workerThreadName = "loom-on-netty-" + id;
            timeSliceLatencyNanos = cfg.timeSliceLatency().toNanos();
            timeSliceThroughputNanos = cfg.timeSliceThroughput().toNanos();
            fifoSwitchTimeNanos = cfg.fifoSwitchTime().toNanos();
            taskFifoThresholdNanos = cfg.taskFifoThreshold().toNanos();
            blockTimeNanos = cfg.blockTime().toNanos();
            throughputModeThreshold = cfg.throughputModeThreshold();
            workSpillThreshold = cfg.workSpillThreshold();
            workSpillMinQueueAgeNanos = cfg.workSpillMinQueueAge().toNanos();
            warmupTasks = cfg.normalWarmupTasks();
            delegate = new ManualIoEventLoop(null, new RunnerIoHandlerFactory(ioHandlerFactory));
            globalLoomQueue = new MpscUnboundedArrayQueue<>(cfg.globalQueueChunkSize());
            localLoomQueue = new LocalTaskQueue(cfg.localQueueInitialCapacity());
        }

        private final class RunnerIoHandlerFactory implements IoHandlerFactory {
            private final IoHandlerFactory delegateFactory;

            private RunnerIoHandlerFactory(IoHandlerFactory delegateFactory) {
                this.delegateFactory = delegateFactory;
            }

            @Override
            public IoHandler newHandler(ThreadAwareExecutor ioExecutor) {
                backingHandler = delegateFactory.newHandler(ioExecutor);
                return new RunnerIoHandler(backingHandler);
            }
        }

        private final class RunnerIoHandler extends DelegateIoHandler {
            private RunnerIoHandler(IoHandler delegate) {
                super(delegate);
            }

            @Override
            public void wakeup() {
                if (block || delegate.isShuttingDown()) {
                    block = false;
                    super.wakeup();
                }
                Thread thread = Thread.currentThread();
                if (isOnRunner(thread)) {
                    if (!throughputMode) {
                        expediteWrite = true;
                    }
                }
            }
        }

        @Override
        public AttributeMap attributeMap() {
            return attributeMap;
        }

        @Override
        public EventExecutor eventLoop() {
            return delegate;
        }

        boolean isOnRunner(Thread thread) {
            if (!thread.isVirtual()) {
                return false;
            }
            if (PrivateLoomSupport.isCarrierThreadSupported()) {
                return PrivateLoomSupport.getCarrierThread(thread) == carrier;
            }
            Object scheduler;
            if (LoomBranchSupport.isSupported()) {
                assert thread == Thread.currentThread();
                scheduler = LoomBranchSupport.currentScheduler();
            } else {
                scheduler = PrivateLoomSupport.getScheduler(thread);
            }
            return ownsScheduler(scheduler);
        }

        private boolean ownsScheduler(Object scheduler) {
            if (scheduler == this) {
                return true;
            } else if (scheduler instanceof IoScheduler s) {
                return s.runner == this;
            } else if (scheduler instanceof StickyScheduler s) {
                return s.io == this;
            } else {
                return false;
            }
        }

        int activeThreads() {
            return (int) ACTIVE_THREADS_LOCAL.getOpaque(this) + activeThreadsExternal.get();
        }

        private boolean isFallingBehind() {
            if (workSpillMinQueueAgeNanos <= 0 || !PrivateLoomSupport.isCarrierThreadSupported() || !isOnRunner(Thread.currentThread())) {
                return true;
            }
            long oldestTime = localLoomQueue.peekLastTime();
            return oldestTime > 0L && System.nanoTime() - oldestTime >= workSpillMinQueueAgeNanos;
        }

        private final class VirtualThreadBuilderConfigurer implements Consumer<Thread.Builder.OfVirtual> {
            @Override
            public void accept(Thread.Builder.OfVirtual b) {
                boolean branchSupported = LoomBranchSupport.isSupported();
                if (!branchSupported && !PrivateLoomSupport.isSupported()) {
                    return;
                }
                if (warmupTasks > 0) {
                    warmupTasks--;
                    if (!branchSupported) {
                        PrivateLoomSupport.setScheduler(b, PrivateLoomSupport.getDefaultScheduler());
                    }
                    return;
                }

                Runner dst = Runner.this;
                int active = activeThreads();
                if (active >= workSpillThreshold && isFallingBehind()) {
                    Runner[] allRunners = runners;
                    for (int i = 0; i < allRunners.length; i++) {
                        Runner runner = allRunners[i];
                        if (runner == null) {
                            continue;
                        }
                        int a = runner.activeThreads();
                        if (a < active) {
                            dst = runner;
                            active = a;
                        }
                    }
                }
                if (branchSupported) {
                    LoomBranchSupport.setScheduler(b, dst.stickyScheduler);
                } else {
                    PrivateLoomSupport.setScheduler(b, dst.stickyScheduler);
                }
            }
        }

        @Override
        public Thread newThread(Runnable r) {
            return unstartedVirtualThread(workerThreadName, builderConfigurer, r);
        }

        @Override
        public void run() {
            carrier = Thread.currentThread();

            ioThread = unstartedVirtualThread(
                "loom-on-netty-" + id + "-io",
                new IoThreadBuilderConfigurer(),
                new IoThreadRunner()
            );
            ioThread.start();
            assert ioContinuationScheduled;

            while (!delegate.isTerminated()) {
                boolean ioContinuationScheduled = this.ioContinuationScheduled;
                if (!ioContinuationScheduled && localLoomQueue.isEmpty() && globalLoomQueue.isEmpty()) {
                    LockSupport.park();
                    ioContinuationScheduled = this.ioContinuationScheduled;
                }
                if (ioContinuationScheduled) {
                    this.ioContinuationScheduled = false;
                    Runnable ioTask = this.ioContinuation;
                    this.ioContinuation = null;
                    ioTask.run();
                }
                globalToLocal();
                throughputMode = localLoomQueue.size() > throughputModeThreshold;
                if (runContinuations(null, System.nanoTime() + timeSlice()) || expediteWrite) {
                    block = false;
                }
            }
            drained = true;
            while (ioThread.isAlive()) {
                if (ioContinuationScheduled) {
                    ioContinuationScheduled = false;
                    Runnable ioTask = this.ioContinuation;
                    this.ioContinuation = null;
                    ioTask.run();
                } else {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                }
            }
            while (enqueuing.get() != 0) {
                Thread.onSpinWait();
            }
            globalToLocal();
            while (!localLoomQueue.isEmpty()) {
                runOnDefaultScheduler(localLoomQueue.pollLast());
            }
        }

        private final class IoThreadBuilderConfigurer implements Consumer<Thread.Builder.OfVirtual> {
            @Override
            public void accept(Thread.Builder.OfVirtual b) {
                if (LoomBranchSupport.isSupported()) {
                    LoomBranchSupport.setScheduler(b, ioScheduler);
                } else if (PrivateLoomSupport.isSupported()) {
                    PrivateLoomSupport.setScheduler(b, ioScheduler);
                }
            }
        }

        private final class IoThreadRunner implements Runnable {
            @Override
            public void run() {
                FastThreadLocalThread.runWithFastThreadLocal(new RunIoTask());
            }
        }

        private final class RunIoTask implements Runnable {
            @Override
            public void run() {
                runIo();
            }
        }

        private static void runOnDefaultScheduler(Runnable command) {
            if (LoomBranchSupport.isSupported()) {
                LoomBranchSupport.runOnDefaultScheduler(command);
            } else {
                PrivateLoomSupport.getDefaultScheduler().execute(command);
            }
        }

        private void runIo() {
            delegate.setOwningThread(Thread.currentThread());
            ThreadExecutorMap.setCurrentExecutor(delegate);
            factory.holder.targetScheduler.set(this);

            while (!delegate.isShuttingDown()) {
                long waitNanos;
                if (block) {
                    waitNanos = blockTimeNanos;
                    idle = true;
                } else {
                    waitNanos = -1;
                }
                block = delegate.run(waitNanos, timeSlice()) == 0;
                idle = false;
                expediteWrite = false;
                if (!localLoomQueue.isEmpty() || !globalLoomQueue.isEmpty()) {
                    Thread.yield();
                }
                if (delegate.runNonBlockingTasks(timeSlice()) != 0) {
                    block = false;
                }
            }

            while (!delegate.isTerminated()) {
                delegate.runNow(TimeUnit.MILLISECONDS.toNanos(10));
                Thread.yield();
            }
        }

        private void globalToLocal() {
            int moved = 0;
            long now = 0L;
            while (true) {
                Runnable task = globalLoomQueue.poll();
                if (task == null) {
                    break;
                }
                if (moved == 0) {
                    now = System.nanoTime();
                }
                moved++;
                localLoomQueue.addFirst(now, task);
            }
            if (moved > 0) {
                activeThreadsExternal.addAndGet(-moved);
                ACTIVE_THREADS_LOCAL.setRelease(this, activeThreadsLocal + moved);
            }
        }

        private long timeSlice() {
            return throughputMode ? timeSliceThroughputNanos : timeSliceLatencyNanos;
        }

        private boolean runContinuations(@Nullable Runnable immediateTask, long deadline) {
            assert !loomNested;

            boolean ranAny = false;
            loomNested = true;
            long now = System.nanoTime();
            do {
                Runnable task;
                if (immediateTask == null) {
                    if (localLoomQueue.isEmpty()) {
                        break;
                    }
                    task = continuationsFifo ? localLoomQueue.pollLast() : localLoomQueue.pollFirst();
                } else {
                    task = immediateTask;
                    immediateTask = null;
                }
                ranAny = true;
                task.run();
                ACTIVE_THREADS_LOCAL.setRelease(this, activeThreadsLocal - 1);
                if (immediateTask == null && localLoomQueue.isEmpty()) {
                    break;
                }
                long end = System.nanoTime();
                continuationTime += end - now;
                now = end;

                if (continuationTime > fifoSwitchTimeNanos) {
                    if (continuationsFifo) {
                        continuationsFifo = false;
                    } else {
                        long oldestTime = localLoomQueue.peekLastTime();
                        continuationsFifo = oldestTime > 0L && now - oldestTime > taskFifoThresholdNanos;
                    }
                    continuationTime = 0;
                }
            } while (now < deadline && !expediteWrite);
            loomNested = false;
            return ranAny;
        }

        private void executeIo(Thread thread, Runnable command) {
            if (thread == ioThread && !drained) {
                Thread t = Thread.currentThread();
                ioContinuation = command;
                ioContinuationScheduled = true;
                if (t != carrier && !isOnRunner(t)) {
                    LockSupport.unpark(carrier);
                }
            } else {
                runOnDefaultScheduler(command);
            }
        }

        private void executeIo(Runnable command) {
            if (!drained) {
                Runnable ioContinuation = this.ioContinuation;
                if (ioContinuation == null) {
                    ioContinuation = command;
                    this.ioContinuation = command;
                }
                if (ioContinuation == command) {
                    Thread t = Thread.currentThread();
                    ioContinuationScheduled = true;
                    if (t != carrier && !isOnRunner(t)) {
                        LockSupport.unpark(carrier);
                    }
                    return;
                }
            }
            runOnDefaultScheduler(command);
        }

        @Override
        public void execute(Runnable task) {
            execute(null, task);
        }

        @Override
        public void execute(Thread thread, Runnable command) {
            if (delegate.isShuttingDown()) {
                runOnDefaultScheduler(command);
                return;
            }

            var curThread = Thread.currentThread();
            if (curThread == carrier) {
                ACTIVE_THREADS_LOCAL.setRelease(this, activeThreadsLocal + 1);
                if (idle && !loomNested && !expediteWrite) {
                    long time = System.nanoTime();
                    runContinuations(command, time + timeSliceLatencyNanos);
                } else {
                    long schedTime = localLoomQueue.isEmpty() ? 0L : System.nanoTime();
                    localLoomQueue.addFirst(schedTime, command);
                }
            } else if (curThread == ioThread || isOnRunner(curThread)) {
                ACTIVE_THREADS_LOCAL.setRelease(this, activeThreadsLocal + 1);
                long schedTime = localLoomQueue.isEmpty() ? 0L : System.nanoTime();
                localLoomQueue.addFirst(schedTime, command);
            } else {
                activeThreadsExternal.incrementAndGet();
                if (!enqueueExternal(command)) {
                    activeThreadsExternal.decrementAndGet();
                    return;
                }

                if (idle || block) {
                    backingHandler.wakeup();
                }
                LockSupport.unpark(carrier);
            }
        }

        boolean enqueueExternal(Runnable command) {
            enqueuing.incrementAndGet();
            try {
                if (drained) {
                    runOnDefaultScheduler(command);
                    return false;
                }
                globalLoomQueue.add(command);
                return true;
            } finally {
                enqueuing.decrementAndGet();
            }
        }
    }

    static final class LocalTaskQueue {
        private Runnable[] tasks;
        private long[] times;
        private int head;
        private int tail;
        private int mask;

        LocalTaskQueue(int initialCapacity) {
            int cap = Integer.highestOneBit(initialCapacity - 1) << 1;
            this.tasks = new Runnable[cap];
            this.times = new long[cap];
            this.mask = cap - 1;
        }

        boolean isEmpty() {
            return head == tail;
        }

        int size() {
            return (tail - head) & mask;
        }

        void addFirst(long time, Runnable task) {
            head = (head - 1) & mask;
            tasks[head] = task;
            times[head] = time;
            if (head == tail) {
                grow();
            }
        }

        Runnable pollFirst() {
            int h = head;
            Runnable task = tasks[h];
            tasks[h] = null;
            head = (h + 1) & mask;
            return task;
        }

        Runnable pollLast() {
            int t = (tail - 1) & mask;
            Runnable task = tasks[t];
            tasks[t] = null;
            tail = t;
            return task;
        }

        long peekLastTime() {
            if (head == tail) {
                return 0L;
            }
            return times[(tail - 1) & mask];
        }

        private void grow() {
            int oldCap = tasks.length;
            int newCap = oldCap << 1;
            Runnable[] newTasks = new Runnable[newCap];
            long[] newTimes = new long[newCap];
            int rightCount = oldCap - head;
            System.arraycopy(tasks, head, newTasks, 0, rightCount);
            System.arraycopy(tasks, 0, newTasks, rightCount, head);
            System.arraycopy(times, head, newTimes, 0, rightCount);
            System.arraycopy(times, 0, newTimes, rightCount, head);
            tasks = newTasks;
            times = newTimes;
            head = 0;
            tail = oldCap;
            mask = newCap - 1;
        }
    }

    record IoScheduler(Runner runner) implements Executor, EventLoopVirtualThreadScheduler, LoomBranchSupport.VirtualThreadSchedulerProxy {
        @Override
        public AttributeMap attributeMap() {
            return runner.attributeMap();
        }

        @Override
        public EventExecutor eventLoop() {
            return runner.eventLoop();
        }

        @Override
        public void execute(Thread thread, Runnable task) {
            runner.executeIo(thread, task);
        }

        @Override
        public void execute(Runnable command) {
            runner.executeIo(command);
        }
    }

    record StickyScheduler(Runner io) implements Executor, EventLoopVirtualThreadScheduler, LoomBranchSupport.VirtualThreadSchedulerProxy {
        private static final LoomBranchSupport.VirtualThreadSchedulerProxy DEFAULT_BRANCH_PROXY =
            new DefaultBranchSchedulerProxy();

        private static final class DefaultBranchSchedulerProxy implements LoomBranchSupport.VirtualThreadSchedulerProxy {
            @Override
            public void execute(Thread thread, Runnable runnable) {
                LoomBranchSupport.runOnDefaultScheduler(runnable);
            }
        }

        @Override
        public void execute(Runnable command) {
            Thread currentThread = Thread.currentThread();
            if (currentThread == io.ioThread) {
                io.execute(command);
                return;
            }
            if (currentThread.isVirtual()) {
                Executor sched = PrivateLoomSupport.getScheduler(currentThread);
                if (sched == io.ioScheduler || sched == io.stickyScheduler || sched == io) {
                    io.execute(command);
                    return;
                }
                var defaultScheduler = PrivateLoomSupport.getDefaultScheduler();
                if (sched == defaultScheduler) {
                    defaultScheduler.execute(command);
                    return;
                }
            } else if (currentThread instanceof ForkJoinWorkerThread fjwt) {
                var defaultScheduler = PrivateLoomSupport.getDefaultScheduler();
                if (fjwt.getPool() == defaultScheduler) {
                    defaultScheduler.execute(command);
                    return;
                }
            }
            io.execute(command);
        }

        @Override
        public void execute(Thread thread, Runnable task) {
            Thread currentThread = Thread.currentThread();
            LoomBranchSupport.VirtualThreadSchedulerProxy dst;
            if (currentThread.isVirtual()) {
                dst = LoomBranchSupport.currentScheduler();
                if (dst instanceof IoScheduler s) {
                    dst = s.runner;
                } else if (dst instanceof StickyScheduler s) {
                    dst = s.io;
                } else if (!(dst instanceof EventLoopVirtualThreadScheduler)) {
                    dst = DEFAULT_BRANCH_PROXY;
                }
            } else {
                dst = io;
            }
            dst.execute(thread, task);
        }

        @Override
        public AttributeMap attributeMap() {
            return io.attributeMap();
        }

        @Override
        public EventExecutor eventLoop() {
            return io.eventLoop();
        }
    }
}
