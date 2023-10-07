package ru.tinkoff.kora.common.util.loom;

import java.util.concurrent.Executor;

public class VirtualThreadExecutorHolder {
    public static final Executor executor = ru.tinkoff.kora.application.graph.internal.loom.VirtualThreadExecutorHolder.executor;
}
