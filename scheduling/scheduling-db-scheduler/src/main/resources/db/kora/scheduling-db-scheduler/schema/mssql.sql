create table kora_scheduling_db_scheduler_jobs
(
  task_name            varchar(350)   not null,
  task_instance        varchar(350)   not null,
  task_data            varbinary(max),
  execution_time       datetimeoffset not null,
  picked               bit,
  picked_by            varchar(50),
  last_success         datetimeoffset,
  last_failure         datetimeoffset,
  consecutive_failures int,
  last_heartbeat       datetimeoffset,
  [version]            bigint         not null,
  priority             smallint,
  constraint kora_scheduling_db_scheduler_jobs_pk primary key (task_instance, task_name),
  index kora_scheduling_db_scheduler_jobs_execution_time_idx (execution_time),
  index kora_scheduling_db_scheduler_jobs_last_heartbeat_idx (last_heartbeat),
  index kora_scheduling_db_scheduler_jobs_priority_execution_time_idx (priority desc, execution_time asc)
);
