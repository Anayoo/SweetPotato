drop table test_user;

create table test_user(
    id int(12) primary key auto_increment,
    username varchar(12) not null,
    password varchar(32) not null
);

insert into test_user values (1, 'testuser', '123456');
insert into test_user values (2, 'test', '234567');