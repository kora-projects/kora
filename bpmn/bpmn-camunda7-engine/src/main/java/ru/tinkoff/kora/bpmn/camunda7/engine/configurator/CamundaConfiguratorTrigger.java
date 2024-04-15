package ru.tinkoff.kora.bpmn.camunda7.engine.configurator;

import org.camunda.bpm.engine.ProcessEngine;
import ru.tinkoff.kora.application.graph.Lifecycle;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CamundaConfiguratorTrigger implements Lifecycle {

    private final ProcessEngine processEngine;
    private final List<CamundaConfigurator> camundaConfigurators;

    public CamundaConfiguratorTrigger(ProcessEngine processEngine,
                                      List<CamundaConfigurator> camundaConfigurators) {
        this.processEngine = processEngine;
        this.camundaConfigurators = camundaConfigurators;
    }

    @Override
    public void init() {
        var setups = camundaConfigurators.stream()
            .map(c -> CompletableFuture.runAsync(() -> {
                try {
                    c.setup(processEngine);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }))
            .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(setups).join();
    }

    @Override
    public void release() throws Exception {
        // do nothing
    }
}
