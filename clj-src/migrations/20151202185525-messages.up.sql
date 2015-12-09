create table messages (
      id serial primary key, 
      message text, 
      username text, 
      time_received timestamp default current_timestamp
);
