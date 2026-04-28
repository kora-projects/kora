package io.koraframework.redis.jedis;

import io.koraframework.redis.jedis.telemetry.JedisObservation;
import io.koraframework.redis.jedis.telemetry.JedisTelemetry;
import redis.clients.jedis.CommandObject;
import redis.clients.jedis.executors.CommandExecutor;

public class KoraCommandExecutor implements CommandExecutor {

    private final CommandExecutor realCommandExecutor;
    private final JedisTelemetry telemetry;

    public KoraCommandExecutor(CommandExecutor realCommandExecutor) {
        this(realCommandExecutor, null);
    }

    public KoraCommandExecutor(CommandExecutor realCommandExecutor, JedisTelemetry telemetry) {
        this.realCommandExecutor = realCommandExecutor;
        this.telemetry = telemetry;
    }

    @Override
    public <T> T executeCommand(CommandObject<T> commandObject) {
        if (telemetry == null) {
            return realCommandExecutor.executeCommand(commandObject);
        }

        JedisObservation observation = telemetry.observe(commandObject);
        try {
            var result = realCommandExecutor.executeCommand(commandObject);
            return result;
        } catch (Throwable t) {
            observation.observeError(t);
            throw t;
        } finally {
            observation.end();
        }
    }

    @Override
    public <T> T broadcastCommand(CommandObject<T> commandObject) {
        if (telemetry == null) {
            return realCommandExecutor.broadcastCommand(commandObject);
        }

        JedisObservation observation = telemetry.observe(commandObject);
        try {
            observation.onStart();
            T result = realCommandExecutor.broadcastCommand(commandObject);
            return result;
        } catch (Throwable t) {
            observation.observeError(t);
            throw t;
        } finally {
            observation.end();
        }
    }

    @Override
    public void close() throws Exception {
        realCommandExecutor.close();
    }
}
