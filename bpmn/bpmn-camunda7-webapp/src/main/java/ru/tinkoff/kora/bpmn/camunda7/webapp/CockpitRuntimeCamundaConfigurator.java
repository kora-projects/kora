package ru.tinkoff.kora.bpmn.camunda7.webapp;

import org.camunda.bpm.cockpit.Cockpit;
import org.camunda.bpm.cockpit.CockpitRuntimeDelegate;
import org.camunda.bpm.cockpit.db.CommandExecutor;
import org.camunda.bpm.cockpit.impl.DefaultCockpitRuntimeDelegate;
import org.camunda.bpm.cockpit.impl.db.CommandExecutorImpl;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.bpmn.camunda7.engine.KoraProcessEngineConfiguration;
import ru.tinkoff.kora.bpmn.camunda7.engine.configurator.CamundaConfigurator;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;

/**
 * Replacing the {@link CockpitRuntimeDelegate} on server startup makes sure that the following scenario in cockpit
 * to view process instances works: Cockpit / Process Definitions / Any Definition, e.g. HelloWorld by embedding the command execution in a transaction.
 */
public class CockpitRuntimeCamundaConfigurator implements CamundaConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(CockpitRuntimeCamundaConfigurator.class);

    private final JdbcConnectionFactory jdbcConnectionFactory;
    private final KoraProcessEngineConfiguration processEngineConfiguration;

    public CockpitRuntimeCamundaConfigurator(JdbcConnectionFactory jdbcConnectionFactory, KoraProcessEngineConfiguration processEngineConfiguration) {
        this.jdbcConnectionFactory = jdbcConnectionFactory;
        this.processEngineConfiguration = processEngineConfiguration;
    }

    @Override
    public void setup(ProcessEngine processEngine) {
        CockpitRuntimeDelegate cockpitRuntimeDelegate = Cockpit.getRuntimeDelegate();
        Cockpit.setCockpitRuntimeDelegate(new DefaultCockpitRuntimeDelegate() {
            @Override
            protected CommandExecutor createCommandExecutor(String processEngineName) {
                return new CommandExecutorImpl(processEngineConfiguration, getMappingFiles()) {
                    @Override
                    public <T> T executeCommand(Command<T> command) {
                        return jdbcConnectionFactory.inTx(connection -> {
                            T result = super.executeCommand(command);
                            if (connection.getAutoCommit()) {
                                connection.setAutoCommit(false);
                            }
                            return result;
                        });
                    }
                };
            }
        });
        logger.debug("Replaced CockpitRuntimeDelegate {} with {} to enable transactions for the Cockpit", cockpitRuntimeDelegate, Cockpit.getRuntimeDelegate());
    }
}
