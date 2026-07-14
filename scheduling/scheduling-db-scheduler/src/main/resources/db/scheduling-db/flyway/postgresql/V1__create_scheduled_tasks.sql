create table scheduled_tasks
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

create index execution_time_idx on scheduled_tasks (execution_time);
create index last_heartbeat_idx on scheduled_tasks (last_heartbeat);
create index priority_execution_time_idx on scheduled_tasks (priority desc, execution_time asc);

