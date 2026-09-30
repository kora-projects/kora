create table kora_scheduling_db_scheduler_jobs
(
    task_name            varchar(350),
    task_instance        varchar(350),
    task_data            blob,
    execution_time       timestamp(6) with time zone,
    picked               number(1, 0),
    picked_by            varchar(50),
    last_success         timestamp(6) with time zone,
    last_failure         timestamp(6) with time zone,
    consecutive_failures number(19, 0),
    last_heartbeat       timestamp(6) with time zone,
    version              number(19, 0),
    priority             smallint,
    constraint kora_scheduling_db_scheduler_jobs_pk primary key (task_name, task_instance)
);

create index kora_scheduling_db_scheduler_jobs_execution_time_idx on kora_scheduling_db_scheduler_jobs (execution_time);
create index kora_scheduling_db_scheduler_jobs_last_heartbeat_idx on kora_scheduling_db_scheduler_jobs (last_heartbeat);
create index kora_scheduling_db_scheduler_jobs_priority_execution_time_idx on kora_scheduling_db_scheduler_jobs (priority desc, execution_time asc);
