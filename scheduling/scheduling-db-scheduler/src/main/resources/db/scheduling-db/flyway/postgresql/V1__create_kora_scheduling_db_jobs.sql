create table kora_scheduling_db_jobs
(
    task_name            text                     not null,
    task_instance        text                     not null,
    task_data            bytea,
    execution_time       timestamp with time zone not null,
    picked               boolean                  not null,
    picked_by            text,
    last_success         timestamp with time zone,
    last_failure         timestamp with time zone,
    consecutive_failures int,
    last_heartbeat       timestamp with time zone,
    version              bigint                   not null,
    priority             smallint,
    primary key (task_name, task_instance)
);

create index kora_scheduling_db_jobs_execution_time_idx on kora_scheduling_db_jobs (execution_time);
create index kora_scheduling_db_jobs_last_heartbeat_idx on kora_scheduling_db_jobs (last_heartbeat);
create index kora_scheduling_db_jobs_priority_execution_time_idx on kora_scheduling_db_jobs (priority desc, execution_time asc);

