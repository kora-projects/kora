create table kora_scheduling_db_scheduler_jobs
(
    task_name            varchar(350) not null,
    task_instance        varchar(350) not null,
    task_data            blob,
    execution_time       datetime(6) not null,
    picked               boolean      not null,
    picked_by            varchar(50),
    last_success         datetime(6) null,
    last_failure         datetime(6) null,
    consecutive_failures int,
    last_heartbeat       datetime(6) null,
    version              bigint       not null,
    priority             smallint,
    constraint kora_scheduling_db_scheduler_jobs_pk primary key (task_name, task_instance),
    index kora_scheduling_db_scheduler_jobs_execution_time_idx (execution_time),
    index kora_scheduling_db_scheduler_jobs_last_heartbeat_idx (last_heartbeat),
    index kora_scheduling_db_scheduler_jobs_priority_execution_time_idx (priority desc, execution_time asc)
);
