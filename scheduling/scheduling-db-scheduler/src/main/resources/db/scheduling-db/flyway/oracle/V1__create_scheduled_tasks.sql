create table scheduled_tasks
(
    task_name            varchar(100),
    task_instance        varchar(100),
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
    primary key (task_name, task_instance)
);

create index scheduled_tasks_execution_time_idx on scheduled_tasks (execution_time);
create index scheduled_tasks_last_heartbeat_idx on scheduled_tasks (last_heartbeat);
create index scheduled_tasks_priority_execution_time_idx on scheduled_tasks (priority desc, execution_time asc);

