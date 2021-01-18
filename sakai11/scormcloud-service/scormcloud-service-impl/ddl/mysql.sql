create table scs_scorm_job (uuid varchar(36) primary key, siteid varchar(36), externalid varchar(255), resourceid varchar(255), title varchar(255), graded int, ctime bigint, mtime bigint, retry_count int default 0, status varchar(32), deleted int default 0, error_message varchar(1024));

alter table scs_scorm_job add index (status);

create table scs_scorm_course (uuid varchar(36) primary key, siteid varchar(36), externalid varchar(255), resourceid varchar(255), title varchar(255), graded int, ctime bigint, mtime bigint, deleted int);

alter table scs_scorm_course add index (siteid, externalid);
alter table scs_scorm_course add index (mtime, graded);

create table scs_scorm_registration (uuid varchar(36) primary key, courseid varchar(36), userid varchar(36), ctime bigint, mtime bigint);

alter table scs_scorm_registration add index (courseid, userid);

create table scs_scorm_job_info (jobname varchar(36) primary key, last_run_time bigint);

create table scs_scorm_scores (registrationid varchar(36) primary key, score double);
