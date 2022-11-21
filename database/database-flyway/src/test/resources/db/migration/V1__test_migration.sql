create table test_migrated_table(
                                    id int primary key,
                                    name varchar(128)
);

insert into test_migrated_table values (100, 'foo');
