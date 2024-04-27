package ru.tinkoff.kora.bpmn.camunda7.engine.configurator;

import org.camunda.bpm.engine.FilterService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.bpmn.camunda7.engine.Camunda7EngineConfig;

import java.time.Duration;

public final class FilterProcessEngineConfigurator implements ProcessEngineConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(FilterProcessEngineConfigurator.class);

    private final Camunda7EngineConfig engineConfig;

    public FilterProcessEngineConfigurator(Camunda7EngineConfig engineConfig) {
        this.engineConfig = engineConfig;
    }

    @Override
    public void setup(ProcessEngine engine) {
        if (engineConfig.filter() != null && !engineConfig.filter().create().isBlank()) {
            logger.debug("Camunda7 Configurator filter creating...");
            final long started = System.nanoTime();

            final String filterName = engineConfig.filter().create();
            FilterService filterService = engine.getFilterService();
            Filter filter = filterService.createFilterQuery().filterName(filterName).singleResult();
            if (filter == null) {
                filter = filterService.newTaskFilter(filterName);
                filterService.saveFilter(filter);
                logger.info("Camunda7 Configurator filter created in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
            } else {
                logger.debug("Camunda7 Configurator filter already exist");
            }
        }
    }
}
