package edu.nyu.classes.seats.storage.migrations;

import edu.nyu.classes.seats.storage.db.*;
import java.util.*;

public class V2__GroupSync extends BaseMigration {

    final static String TABLE_DEFS =
        "create table seat_sakai_group_sync_queue (                                                                    " +
        "    id varchar2(255) primary key,                                                                             " +
        "    action varchar2(255) not null,                                                                            " +
        "    arg1 varchar2(255) not null,                                                                              " +
        "    arg2 varchar2(255)                                                                                        " +
        ");                                                                                                            " +

        "alter table seat_group add (sakai_group_id varchar2(255));                                                    ";

    public void migrate(DBConnection connection) throws Exception {
        for (String ddl : TABLE_DEFS.split(";")) {
            if (ddl.trim().isEmpty()) {
                continue;
            }

            connection.run(ddl.trim()).executeUpdate();
        }

        List<String> seatGroupIds = connection.run("select id from seat_group where sakai_group_id is null")
            .executeQuery()
            .getStringColumn("id");

        int sequence = 0;
        for (String seatGroupId : seatGroupIds) {
            sequence += 1;
            connection
                .run("insert into seat_sakai_group_sync_queue (id, action, arg1) VALUES (?, ?, ?)")
                .param(String.format("%013d_%016d_%s", 0, sequence, connection.uuid()))
                .param("SYNC_SEAT_GROUP")
                .param(seatGroupId)
                .executeUpdate();
        }
    }
}

