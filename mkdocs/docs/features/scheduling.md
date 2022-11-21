# Scheduling

## Подключение


## Использование jdk

Для шедулинга поверх обычного `java.util.concurrent.ScheduledExecutorService`:

- подключить зависимость: `ru.tinkoff.kora:scheduling-jdk`
- подключить модуль `ru.tinkoff.kora.scheduling.jdk.SchedulingJdkModule`
- отметить нужный метод одной из аннотаций:
    - `@ru.tinkoff.kora.scheduling.jdk.annotation.ScheduleAtFixedRate`
    - `@ru.tinkoff.kora.scheduling.jdk.annotation.ScheduleOnce`
    - `@ru.tinkoff.kora.scheduling.jdk.annotation.ScheduleWithFixedDelay`
      
      Параметры аннотаций соответствуют параметрам методов `scheduleAtFixedRate`, `schedule`, `scheduleWithFixedDelay` соответственно.
      Так же все аннотации имеют аргумент `config` при наличии которого значения параметра возьмутся из конфигурации по указанному пути.

## Использование quartz
Для шедулинга поверх quartz:

- подключить зависимость: `ru.tinkoff.kora:scheduling-quartz`
- подключить модуль `ru.tinkoff.kora.scheduling.quartz.QuartzModule`
- отметить нужный метод одной из аннотаций:
    - `@ru.tinkoff.kora.scheduling.quartz.ScheduleWithCron` для использования крон выражения из аннотации или из конфигурации
    - `@ru.tinkoff.kora.scheduling.quartz.ScheduleWithTrigger` для указания тега триггера
