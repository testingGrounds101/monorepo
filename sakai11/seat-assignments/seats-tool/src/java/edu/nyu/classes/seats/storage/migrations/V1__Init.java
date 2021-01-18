package edu.nyu.classes.seats.storage.migrations;

import edu.nyu.classes.seats.storage.db.*;

public class V1__Init extends BaseMigration {

    final static String TABLE_DEFS =
        "create table seat_group_section (                                                                            " +
        "    id varchar2(255) primary key,                                                                            " +
        "    primary_stem_name varchar2(255) not null,                                                                " +
        "    site_id varchar2(255) not null,                                                                          " +
        "    provisioned number(1) not null,                                                                          " +
        "    has_split number(1) not null,                                                                            " +
        "    constraint uniq_section_id_siteid unique (primary_stem_name, site_id)                                    " +
        ");                                                                                                           " +

        "create index sgs_site on seat_group_section (site_id);                                                       " +

        "create table seat_group_section_rosters (                                                                    " +
        "    sakai_roster_id varchar2(255) not null,                                                                  " +
        "    role varchar2(255) not null,                                                                             " +
        "    section_id varchar2(255) not null,                                                                       " +
        "    constraint fk_seat_group_section_id foreign key (section_id) references seat_group_section (id)          " +
        ");                                                                                                           " +

        "create index sgsr_section_roster on seat_group_section_rosters (section_id, sakai_roster_id);                " +

        "create table seat_group (                                                                                    " +
        "    id varchar2(255) primary key,                                                                            " +
        "    name varchar2(255) not null,                                                                             " +
        "    description varchar2(4000),                                                                              " +
        "    section_id varchar2(255) not null,                                                                       " +
        "    constraint fk_seat_section_id foreign key (section_id) references seat_group_section (id)                " +
        ");                                                                                                           " +

        "create index sg_section on seat_group (section_id);                                                          " +

        "create table seat_group_members (                                                                            " +
        "    group_id varchar2(255) not null,                                                                         " +
        "    netid varchar2(255) not null,                                                                            " +
        "    official number(1) not null,                                                                             " +
        "    role varchar2(255) not null,                                                                             " +
        "    primary key (group_id, netid),                                                                           " +
        "    constraint fk_seat_group_id foreign key (group_id) references seat_group (id)                            " +
        ");                                                                                                           " +

        "create table seat_meeting (                                                                                  " +
        "    id varchar2(255) primary key,                                                                            " +
        "    group_id varchar2(255) not null,                                                                         " +
        "    location varchar2(255) not null,                                                                         " +
        "    constraint fk_seat_meeting_group_id foreign key (group_id) references seat_group (id)                    " +
        ");                                                                                                           " +

        "create index sm_group_id on seat_meeting (group_id);                                                         " +

        "create table seat_meeting_assignment (                                                                       " +
        "    id varchar2(255) primary key,                                                                            " +
        "    meeting_id varchar2(255) not null,                                                                       " +
        "    editable_until number not null,                                                                          " +
        "    netid varchar2(255) not null,                                                                            " +
        "    seat varchar2(32) not null,                                                                              " +
        "    constraint uniq_seat_assignment_netid unique (meeting_id, netid),                                        " +
        "    constraint uniq_seat_assignment_seat unique (meeting_id, seat),                                          " +
        "    constraint fk_seat_meeting_id foreign key (meeting_id) references seat_meeting (id)                      " +
        ");                                                                                                           " +

        "create index sma_meeting on seat_meeting_assignment(meeting_id);                                             " +

        "create table seat_sync_queue (                                                                               " +
        "    site_id varchar2(255) primary key,                                                                       " +
        "    last_sync_requested_time number default 0,                                                               " +
        "    last_sync_time number default 0                                                                          " +
        ");                                                                                                           " +

        "create index ssq_req_time_by_site on seat_sync_queue(last_sync_requested_time, site_id);                     " +

        "create table seat_sync_locks (                                                                               " +
        "    site_id varchar2(255) primary key,                                                                       " +
        "    lock_time number default 0                                                                               " +
        ");                                                                                                           " +

        "create table seat_audit (                                                                                    " +
        "    id varchar2(255) primary key,                                                                            " +
        "    timestamp_ms number not null,                                                                            " +
        "    event_code varchar2(32) not null,                                                                        " +
        "    json clob not null,                                                                                      " +
        "    logged_in_user varchar2(255),                                                                            " +
        "    group_id varchar2(255),                                                                                  " +
        "    group_name varchar2(255),                                                                                " +
        "    meeting_id varchar2(255),                                                                                " +
        "    meeting_location varchar2(255),                                                                          " +
        "    netid varchar2(255),                                                                                     " +
        "    primary_stem_name varchar2(255),                                                                         " +
        "    section_id varchar2(255),                                                                                " +
        "    seat varchar2(255)                                                                                       " +
        ");                                                                                                           ";


    public void migrate(DBConnection connection) throws Exception {
        for (String ddl : TABLE_DEFS.split(";")) {
            if (ddl.trim().isEmpty()) {
                continue;
            }

            connection.run(ddl.trim()).executeUpdate();
        }
    }
}
