package org.sakaiproject.starfish.jobs;

import java.nio.file.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.text.SimpleDateFormat;

import com.opencsv.CSVWriter;

import org.sakaiproject.db.cover.SqlService;

import java.io.FileWriter;
import java.io.IOException;

public class InactiveStudents {

    private final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static int eventIdBeforeNDaysAgo(Connection conn, int days) throws SQLException {
        int stepSize = 2000000;
        int maxEventId;

        try (PreparedStatement ps = conn.prepareStatement("select max(event_id) from sakai_event")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    maxEventId = rs.getInt(1);
                } else {
                    throw new RuntimeException("No events?  Highly irregular!");
                }
            }
        }

        // Inlining days here to help Oracle make good decisions with query planning...
        try (PreparedStatement ps = conn.prepareStatement(String.format("select 1 " +
                                                                        "from sakai_event " +
                                                                        "where event_id = ? AND event_date < (trunc(sys_extract_utc(current_timestamp), 'DDD') - %s)",
                                                                        days))) {
            for (int i = 0; i < 100; i++) {
                int candidateEventId = maxEventId - ((i + 1) * stepSize);

                ps.clearParameters();
                ps.setInt(1, candidateEventId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return candidateEventId;
                    }
                }
            }
        }

        return 0;
    }

    public static void writeToFile(Path outputFile) {
        try {
            Connection conn = null;
            try {
                conn = SqlService.borrowConnection();

                Map<String, Date> netids = new HashMap<>();

                int lastWeekStartEventId = eventIdBeforeNDaysAgo(conn, 7);
                try (PreparedStatement ps = conn.prepareStatement
                     (String.format
                      ("select distinct enrl.netid" +
                       " from (" +
                       "   select distinct ss.site_id" +
                       "   from sakai_event se" +
                       "   inner join sakai_session sess on sess.session_id = se.session_id" +
                       "   inner join sakai_site ss on ss.site_id = se.context" +
                       "   inner join sakai_site_user ssu on ssu.site_id = ss.site_id AND ssu.user_id = sess.session_user AND permission = 1" +
                       "   where se.event_id >= %d AND se.event_date >= trunc(sys_extract_utc(current_timestamp), 'DDD') - 7 AND ss.published = '1'" +
                       " ) active_sites" +
                       " inner join sakai_realm sr on sr.realm_id = concat('/site/', active_sites.site_id)" +
                       " inner join sakai_realm_provider srp on srp.realm_key = sr.realm_key" +
                       " inner join nyu_mv_class_tbl class on replace(class.stem_name, ':', '_') = srp.provider_id" +
                       " inner join nyu_t_student_enrollments enrl on enrl.stem_name = class.stem_name" +
                       " where class.START_DT < CURRENT_DATE" +
                       "   AND class.END_DT > CURRENT_DATE" +
                       "   AND class.SESSION_CODE != 'REG' " +
                       "   AND class.SSR_COMPONENT not in ('INT','FLD')",
                       lastWeekStartEventId))) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            netids.put(rs.getString("netid"), null);
                        }
                    }
                }

                int lastMonthStartEventId = eventIdBeforeNDaysAgo(conn, 28);
                Date now = new Date();
                Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                long pastWeekMs = (7 * 24 * 60 * 60 * 1000);


                try (PreparedStatement ps = conn.prepareStatement
                     (String.format
                      ("select map.eid, max(se.event_date) as event_date" +
                       " from sakai_event se" +
                       " inner join sakai_session sess on sess.session_id = se.session_id" +
                       " inner join sakai_user_id_map map on map.user_id = sess.session_user" +
                       " where se.event_id >= %s AND se.event_date >= trunc(sys_extract_utc(current_timestamp), 'DDD') - 28" +
                       " group by map.eid",
                       lastMonthStartEventId))) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String netid = rs.getString("eid");
                            Date eventDate = rs.getTimestamp("event_date", utcCal);

                            if (!netids.containsKey(netid)) {
                                continue;
                            }

                            if ((now.getTime() - eventDate.getTime()) < pastWeekMs) {
                                // User has recently done something
                                netids.remove(netid);
                            } else {
                                netids.put(netid, eventDate);
                            }
                        }
                    }
                }

                CSVWriter writer = new CSVWriter(new FileWriter(outputFile.toString()), ',');
                writer.writeNext(new String[] { "netid", "last event time", "inactive weeks" });
                long oneWeekMs = (7 * 24 * 60 * 60 * 1000);

                for (Map.Entry<String, Date> entry : netids.entrySet()) {
                    if (entry.getValue() == null) {
                        writer.writeNext(new String[] { entry.getKey(), "N/A", "4+" });
                    } else {
                        String weeks = String.valueOf((now.getTime() - entry.getValue().getTime()) / oneWeekMs);

                        writer.writeNext(new String[] { entry.getKey(), DATE_FORMAT.format(entry.getValue()), weeks });
                    }
                }

                writer.close();
            } finally {
                if (conn != null) {
                    SqlService.returnConnection(conn);
                }
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
