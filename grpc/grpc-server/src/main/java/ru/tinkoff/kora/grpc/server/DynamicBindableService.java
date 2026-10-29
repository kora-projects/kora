package ru.tinkoff.kora.grpc.server;

import io.grpc.BindableService;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import ru.tinkoff.kora.application.graph.RefreshListener;
import ru.tinkoff.kora.application.graph.ValueOf;

import java.util.concurrent.ConcurrentHashMap;

public final class DynamicBindableService implements BindableService, RefreshListener {
    final ValueOf<BindableService> service;
    final ConcurrentHashMap<String, DynamicServerCall<?, ?>> methods = new ConcurrentHashMap<>();

    public DynamicBindableService(ValueOf<BindableService> service) {
        this.service = service;
    }

    @Override
    public ServerServiceDefinition bindService() {
        var definition = service.get().bindService();
        var dynamicDefinition = ServerServiceDefinition.builder(definition.getServiceDescriptor());
        definition.getMethods().forEach(method -> dynamicDefinition.addMethod(initMethod(method)));

        return dynamicDefinition.build();
    }

    @Override
    public void graphRefreshed() {
        service.get().bindService().getMethods().forEach(this::replaceMethod);
    }

    @SuppressWarnings("unchecked")
    private <Req, Res> ServerMethodDefinition<Req, Res> initMethod(ServerMethodDefinition<Req, Res> method) {
        var call = (DynamicServerCall<Req, Res>) methods.computeIfAbsent(method.getMethodDescriptor().getFullMethodName(),
            key -> new DynamicServerCall<>(method.getServerCallHandler()));
        return method.withServerCallHandler(call);
    }

    @SuppressWarnings("unchecked")
    private <Req, Res> void replaceMethod(ServerMethodDefinition<Req, Res> method) {
        var key = method.getMethodDescriptor().getFullMethodName();
        DynamicServerCall<Req, Res> call = (DynamicServerCall<Req, Res>) methods.get(key);
        call.setCurrentCall(method.getServerCallHandler());
    }
}
