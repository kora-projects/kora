package ru.tinkoff.kora.http.server.common.privateapi;

import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.PromiseOf;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.http.server.common.PrivateHttpServerConfig;

public class ReadinessHandler extends ProbeHandler<ReadinessProbe, ReadinessProbeFailure> {
    private final ValueOf<PrivateHttpServerConfig> config;

    public ReadinessHandler(ValueOf<PrivateHttpServerConfig> config, All<PromiseOf<ReadinessProbe>> probes) {
        super(probes);
        this.config = config;
    }

    @Override
    public String routeTemplate() {
        return this.config.get().readinessPath();
    }

    @Override
    protected ReadinessProbeFailure performProbe(ReadinessProbe probe) throws Exception {return probe.probe();}

    @Override
    protected String getMessage(ReadinessProbeFailure failure) {return failure.message();}
}
