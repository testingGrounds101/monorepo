Grades SOAP service
===================

This project contains a SOAP web service consumed by the PeopleSoft
SIS system.  The interesting bits:

  * Grade query handling:

      nyugrades/nyugrades-api/api/src/java/edu/nyu/classes/nyugrades/api/NYUGradesService.java
      nyugrades/nyugrades-impl/impl/src/java/edu/nyu/classes/nyugrades/impl/NYUGradesServiceImpl.java

  * Database access:

      nyugrades/nyugrades-api/api/src/java/edu/nyu/classes/nyugrades/api/DBService.java
      nyugrades/nyugrades-impl/impl/src/java/edu/nyu/classes/nyugrades/impl/DBServiceImpl.java

  * Session handling:

      nyugrades/nyugrades-api/api/src/java/edu/nyu/classes/nyugrades/api/NYUGradesSessionService.java
      nyugrades/nyugrades-impl/impl/src/java/edu/nyu/classes/nyugrades/impl/NYUGradesSessionServiceImpl.java


Database schema requirements
----------------------------

The following tables and indexes must be created in the Sakai CLE database:

  create table nyu_t_grades_ws_session (sessionid varchar(64) primary key, username varchar(99), last_used integer);

  create table nyu_t_grades_audit (system_timestamp number not null, netid varchar(99) not null, emplid varchar(99), gradeletter varchar(10), db_timestamp timestamp default systimestamp);

  create index nyu_t_grades_audit_systime on nyu_t_grades_audit (system_timestamp);

  create index nyu_t_grades_audit_dbtime on nyu_t_grades_audit (db_timestamp);

Data format returned
--------------------

PeopleSoft needs the SOAP response to list grades in a somewhat
particular format, so this web service uses some custom serializer
classes to bring that about.  The format PeopleSoft expects looks like
this:

  <grades>
    <grade>
      <netid>abc1234</netid>
      <emplid>9191919191</emplid>
      <gradeletter>A+</gradeletter>
    </grade>
    <grade>
      <netid>def1234</netid>
      <emplid>8282828282</emplid>
      <gradeletter>C-</gradeletter>
    </grade>
    ...
  </grades>

Where a given NetID couldn't be resolved to an emplid, a special
emplid is returned.  That emplid value is defined as MISSING_EMPLID in
NYUGradesServiceImpl.java and is "N000000000" at the time of writing.
