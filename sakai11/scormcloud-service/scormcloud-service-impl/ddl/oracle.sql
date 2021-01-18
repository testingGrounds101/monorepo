create table scs_scorm_job (uuid varchar2(36) primary key, siteid varchar2(36), externalid varchar2(255), resourceid varchar2(255), title varchar2(255), graded int, ctime number, mtime number, retry_count int default 0, status varchar2(32), deleted int default 0, error_message varchar2(1024));
create table scs_scorm_course (uuid varchar2(36) primary key, siteid varchar2(36), externalid varchar2(255), resourceid varchar2(255), title varchar2(255), graded int, ctime number, mtime number, deleted int);
create table scs_scorm_registration (uuid varchar(36) primary key, courseid varchar2(36), userid varchar2(36), ctime number, mtime number);
create table scs_scorm_job_info (jobname varchar2(36) primary key, last_run_time number);
create table scs_scorm_scores (registrationid varchar2(36) primary key, score double precision);

CREATE INDEX scs_scorm_job_status on scs_scorm_job (status);
CREATE INDEX scs_scorm_course_site_ext_id on scs_scorm_course (siteid, externalid);
CREATE INDEX scs_scorm_course_mtime on scs_scorm_course (mtime, graded);
CREATE INDEX scs_scorm_registration_cuser on scs_scorm_registration (courseid, userid);
