-- schema for dj dash

drop table if exists geocode cascade;
create table  geocode (
	   ip varchar(255) primary key,
	   city varchar(255),
	   country varchar(255),
	   lat numeric(10,5),
	   lng numeric(10,5),
	   region varchar(255)
);
