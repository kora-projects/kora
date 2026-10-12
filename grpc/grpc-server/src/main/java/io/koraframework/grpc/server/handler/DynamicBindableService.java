package io.koraframework.grpc.server.handler;

import io.grpc.BindableService;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.koraframework.application.graph.RefreshListener;
import io.koraframework.application.graph.ValueOf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DynamicBindableService implements BindableService, RefreshListener {

    private final ValueOf<BindableService> service;
    private final Map<String, DynamicServerCall<?, ?>> methods = new ConcurrentHashMap<>();

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
        // A rebuilt server builder binds the service again: reuse the call objects the running server already holds
        var call = (DynamicServerCall<Req, Res>) methods.computeIfAbsent(method.getMethodDescriptor().getFullMethodName(), _ -> new DynamicServerCall<>(method.getServerCallHandler()));
        call.setCurrentCall(method.getServerCallHandler());
        return method.withServerCallHandler(call);
    }

    @SuppressWarnings("unchecked")
    private <Req, Res> void replaceMethod(ServerMethodDefinition<Req, Res> method) {
        var key = method.getMethodDescriptor().getFullMethodName();
        DynamicServerCall<Req, Res> call = (DynamicServerCall<Req, Res>) methods.get(key);
        call.setCurrentCall(method.getServerCallHandler());
    }
}
