create table grouper_status (setting varchar2(255) PRIMARY KEY, value varchar2(255));

create table grouper_group_definitions (group_id varchar2(255) PRIMARY KEY, 
       grouper_group_id varchar(255) NOT NULL,
       sakai_group_id varchar2(255) NOT NULL,
       description varchar2(512) NOT NULL,
       deleted NUMBER(1,0) DEFAULT 0,
       mtime NUMBER NOT NULL
);

create index grouper_groups_sakai_id on grouper_group_definitions (sakai_group_id);

create table grouper_group_users (group_id varchar2(255), netid varchar2(255), role varchar2(30),
       PRIMARY KEY (group_id, netid),
       FOREIGN KEY (group_id) references grouper_group_definitions (group_id));

create index grouper_groups_role on grouper_group_users (role);


-- Tables/views exposed to grouper
create view grouper_groups as select grouper_group_id as group_id, description from grouper_group_definitions where deleted != 1;

create or replace view grouper_memberships as select gd.grouper_group_id as group_id, u.netid, gd.description as group_description
from grouper_group_definitions gd
inner join grouper_group_users u on u.group_id = gd.group_id
where u.role = 'viewer';

create or replace view grouper_managers as select gd.grouper_group_id as group_id, u.netid, gd.description as group_description
from grouper_group_definitions gd
inner join grouper_group_users u on u.group_id = gd.group_id
where u.role = 'manager';

create table grouper_sync_status (group_id varchar2(255) PRIMARY KEY,
       grouper_group_id varchar2(255) NOT NULL,
       status varchar2(100));

alter table grouper_sync_status add (update_mtime TIMESTAMP WITH TIME ZONE);

create index grouper_sync_status_grpid on grouper_sync_status (grouper_group_id);

-- CLASSES-2675 add new ready_for_sync_time
ALTER TABLE grouper_group_definitions add (ready_for_sync_time NUMBER default 0);
CREATE INDEX grouper_group_ready_sync on grouper_group_definitions (ready_for_sync_time);

-- CLASSES-2675 provide email addresses too
--
-- NOTE: Google domain is hard-coded below.  Adjust as appropriate for
-- the envionment you're targeting (@nyu.edu for production)
--
create or replace view grouper_groupsync_users as
select gu.group_id, gu.role, coalesce(ntu.email, concat(gu.netid, '@gqa.nyu.edu')) email
from grouper_group_users gu
left outer join nyu_t_users ntu on ntu.netid = gu.netid
where gu.role = 'viewer'
union
select gu.group_id, gu.role, concat(gu.netid, '@gqa.nyu.edu') email
from grouper_group_users gu
left outer join nyu_t_users ntu on ntu.netid = gu.netid
where gu.role = 'manager';
