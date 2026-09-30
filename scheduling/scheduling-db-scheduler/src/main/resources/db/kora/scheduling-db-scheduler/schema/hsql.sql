create table kora_scheduling_db_scheduler_jobs
(
    task_name            varchar(350),
    task_instance        varchar(350),
    task_data            blob,
    execution_time       timestamp with time zone,
    picked               bit,
    picked_by            varchar(50),
    last_success         timestamp with time zone,
    last_failure         timestamp with time zone,
    consecutive_failures int,
    last_heartbeat       timestamp with time zone,
    version              bigint,
    priority             smallint,
    constraint kora_scheduling_db_scheduler_jobs_pk primary key (task_name, task_instance)
);
