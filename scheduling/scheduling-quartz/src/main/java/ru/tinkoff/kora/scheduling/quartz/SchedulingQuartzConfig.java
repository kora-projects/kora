package ru.tinkoff.kora.scheduling.quartz;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface SchedulingQuartzConfig {

    /**
     * <b>Русский</b>: Ожидать ли завершения задач перед остановкой планировщика при плавной остановке.
     * <hr>
     * <b>English</b>: Whether to wait for tasks to complete before scheduler shutdown during graceful shutdown.
     */
    default boolean waitForJobComplete() {
        return true;
    }

    /**
     * <b>Русский</b>: Удалять ли из постоянного хранилища {@link org.quartz.Scheduler} задачи, которые больше не зарегистрированы в графе приложения (например, после удаления или переименования класса с {@link ScheduleWithCron}) при старте планировщика.
     * <p>
     * При выключенном значении такие «осиротевшие» задачи остаются в хранилище, и {@link org.quartz.Scheduler} логирует {@link org.quartz.JobPersistenceException}: Couldn't retrieve job because a required class was not found при каждом старте, пока их повторно обрабатывает обработчик misfire.
     * <p>
     * Включать с осторожностью: очистка удаляет все задачи, отсутствующие в текущем графе приложения, поэтому она может удалить чужие задачи, если планировщик используется совместно с другими источниками задач — например, задачи, добавленные в {@link org.quartz.Scheduler} напрямую (вне {@link ScheduleWithCron}/{@link ScheduleWithTrigger}), или задачи, зарегистрированные другим экземпляром приложения в кластерной/многопоточной конфигурации (например, при поэтапном (rolling) развёртывании, когда один экземпляр ещё не знает о задачах другого).
     * <hr>
     * <b>English</b>: Whether to remove from the persistent {@link org.quartz.Scheduler} store jobs that are no longer registered in the application graph (e.g. after a class with {@link ScheduleWithCron} was deleted or renamed) during scheduler startup.
     * <p>
     * When disabled, such orphaned jobs remain in the store and {@link org.quartz.Scheduler} logs {@link org.quartz.JobPersistenceException}: Couldn't retrieve job because a required class was not found on every startup while its misfire handler retries.
     * <p>
     * Enable with caution: cleanup removes every job absent from the current application graph, so it can remove foreign jobs if the scheduler is shared with other job sources — e.g. jobs added to the {@link org.quartz.Scheduler} directly (outside {@link ScheduleWithCron}/{@link ScheduleWithTrigger}), or jobs registered by another application instance in a clustered/multi-instance setup (for example, during a rolling deployment when one instance does not yet know about another instance's jobs).
     */
    default boolean cleanupOrphanedJobs() {
        return false;
    }
}
