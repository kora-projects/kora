package io.koraframework.netty.common.loom;

import io.netty.channel.*;

abstract class DelegateIoHandler implements IoHandler {
    private final IoHandler delegate;

    DelegateIoHandler(IoHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void initialize() {
        delegate.initialize();
    }

    @Override
    public int run(IoHandlerContext context) {
        return delegate.run(context);
    }

    @Override
    public void prepareToDestroy() {
        delegate.prepareToDestroy();
    }

    @Override
    public void destroy() {
        delegate.destroy();
    }

    @Override
    public IoRegistration register(IoHandle handle) throws Exception {
        return delegate.register(handle);
    }

    @Override
    public void wakeup() {
        delegate.wakeup();
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return delegate.isCompatible(handleType);
    }
}
