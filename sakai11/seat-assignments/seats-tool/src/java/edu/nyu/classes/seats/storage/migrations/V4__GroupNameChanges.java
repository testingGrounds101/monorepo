package edu.nyu.classes.seats.storage.migrations;

import edu.nyu.classes.seats.storage.db.DBConnection;

public class V4__GroupNameChanges extends BaseMigration {

    public void migrate(DBConnection connection) throws Exception {
        connection.run("update seat_group set name = REGEXP_REPLACE(name, '^.*\\-', '') WHERE name like '%-%'").executeUpdate();
    }
}

