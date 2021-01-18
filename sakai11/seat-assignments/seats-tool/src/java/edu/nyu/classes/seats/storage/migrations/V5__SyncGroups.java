package edu.nyu.classes.seats.storage.migrations;

import edu.nyu.classes.seats.storage.db.DBConnection;

import java.util.List;

public class V5__SyncGroups extends BaseMigration {

    public void migrate(DBConnection connection) throws Exception {
        List<String> seatGroupIds = connection.run("select id from seat_group")
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

