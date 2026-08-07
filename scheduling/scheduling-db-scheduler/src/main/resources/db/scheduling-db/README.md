# Kora scheduling-db schema resources

`db-scheduler` does not create the `scheduled_tasks` table automatically.
Applications should apply the schema with their regular migration tool before the scheduler starts.

Flyway locations:

- `classpath:db/scheduling-db/flyway/postgresql`
- `classpath:db/scheduling-db/flyway/mysql`
- `classpath:db/scheduling-db/flyway/mariadb`
- `classpath:db/scheduling-db/flyway/mssql`
- `classpath:db/scheduling-db/flyway/oracle`
- `classpath:db/scheduling-db/flyway/hsql`

Liquibase changelog:

- `classpath:db/scheduling-db/liquibase/changelog.yaml`

The Liquibase changelog contains database-specific changesets guarded by `dbms`.
