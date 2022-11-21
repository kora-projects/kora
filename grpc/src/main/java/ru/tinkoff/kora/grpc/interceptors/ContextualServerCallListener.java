package ru.tinkoff.kora.grpc.interceptors;

import io.grpc.ForwardingServerCallListener;
import io.grpc.ServerCall;
import ru.tinkoff.kora.common.Context;

import java.util.function.Consumer;

public class ContextualServerCallListener<ReqT> extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
    private final Context context;
    protected ContextualServerCallListener(ServerCall.Listener<ReqT> delegate, Context context) {
        super(delegate);
        this.context = context;
    }

    @Override
    public void onHalfClose() {
        try {
            context.inject();
            super.onHalfClose();
        } finally {
            Context.clear();
        }
    }

    @Override
    public void onCancel() {
        try {
            context.inject();
            super.onCancel();
        } finally {
            Context.clear();
        }
    }

    @Override
    public void onComplete() {
        try {
            context.inject();
            super.onComplete();
        } finally {
            Context.clear();
        }
    }

    @Override
    public void onReady() {
        try {
            context.inject();
            super.onReady();
        } finally {
            Context.clear();
        }
    }

    @Override
    public void onMessage(ReqT message){
        try {
            context.inject();
            super.onMessage(message);
        } finally {
            Context.clear();
        }
    }

}
