package edu.nyu.classes.groupersync.jobs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.db.api.SqlService;

import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class UpdatedSites {
    private static final Log log = LogFactory.getLog(UpdatedSites.class);
    private static final Pattern SITE_ID_PATTERN = Pattern.compile("/site/([^/]*)/?");
    private static final Pattern UUID_PATTERN = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
    private static final long MARGIN_OF_ERROR_MS = 10000;
    private final SqlService sqlService;

    public UpdatedSites(SqlService sqlService) {
        this.sqlService = sqlService;
    }

    private String extractSiteId(String s) {
        if (s.startsWith("~") || s.startsWith("/site/~")) {
            // Not interested in people's workspaces
            return null;
        }

        Matcher m = SITE_ID_PATTERN.matcher(s);
        String result = null;

        if (m.find()) {
            result = m.group(1);
        } else {
            m = UUID_PATTERN.matcher(s);

            if (m.matches()) {
                result = s;
            }
        }

        if (result != null) {
            return result;
        } else {
            log.error("Could not get a site ID out of: " + s);
            return null;
        }
    }

    private void addUpdatedSites(Connection db, String selectColumn, String table,
                                 Timestamp since, String where,
                                 List<UpdatedSite> result)
            throws SQLException {
        String sql = "select %s, modifiedon from %s where modifiedon >= ? AND %s";

        PreparedStatement ps = db.prepareStatement(String.format(sql, selectColumn, table, where));

        ps.setTimestamp(1, since, new java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")));

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            String siteId = extractSiteId(rs.getString(1));

            if (siteId != null) {
                result.add(new UpdatedSite(siteId, rs.getTimestamp(2)));
            }
        }

        rs.close();
        ps.close();
    }

    private void addSitesWithUpdatedRosters(Connection db, Timestamp since, List<UpdatedSite> result)
            throws SQLException {

        // The horrible substr/instr business below extracts the contents after
        // the last slash, which is the UUID of either the group or the site_id.
        String grouperJoinClause = "inner join grouper_group_definitions ggd on substr(sr.realm_id, instr(sr.realm_id, '/', -1) + 1) = ggd.sakai_group_id";

        if (db.getMetaData().getDatabaseProductName().contains("MySQL")) {
            grouperJoinClause = "inner join grouper_group_definitions ggd on substring_index(sr.realm_id, '/', -1) = ggd.sakai_group_id";
        }

        // The cm_member_container_t only gives us modification stamps down to
        // day granularity, so we're going to pull back some updates
        // unnecessarily here.  Hopefully the update process will be fast enough
        // for this not to matter too much.
        //
        // We also only look for changes to rosters that have been linked to a
        // site via a group.  Otherwise, when there's a full roster sync we end
        // up marking all sites as needing checking, which would work, but would
        // be needlessly slow.
        String sql = ("select distinct sr.realm_id, cm.last_modified_date " +
                "from cm_member_container_t cm " +
                "inner join sakai_realm_provider srp on srp.provider_id = cm.enterprise_id " +
                "inner join sakai_realm sr on sr.realm_key = srp.realm_key " +
                grouperJoinClause + " " +
                "where cm.class_discr = 'org.sakaiproject.coursemanagement.impl.SectionCmImpl' AND cm.last_modified_date >= ?");

        PreparedStatement ps = db.prepareStatement(sql);

        // Deliberately truncate to the day here.
        ps.setDate(1, new java.sql.Date(since.getTime()));

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            String siteId = extractSiteId(rs.getString(1));

            if (siteId != null) {
                result.add(new UpdatedSite(siteId, rs.getTimestamp(2)));
            }
        }

        rs.close();
        ps.close();
    }


    private void addSitesWithGrouperSyncChanges(Connection db, Timestamp since, List<UpdatedSite> result)
            throws SQLException {

        String sql = (
                      // Find by group
                      "select ssg.site_id, gd.mtime " +
                      "from grouper_group_definitions gd " +
                      "inner join sakai_site_group ssg on ssg.group_id = gd.sakai_group_id " +
                      "where gd.mtime >= ?" +

                      " UNION " +

                      // And by site
                      "select ss.site_id, gd.mtime " +
                      "from grouper_group_definitions gd " +
                      "inner join sakai_site ss on ss.site_id = gd.sakai_group_id " +
                      "where gd.mtime >= ?"
        );

        PreparedStatement ps = db.prepareStatement(sql);

        ps.setLong(1, since.getTime());
        ps.setLong(2, since.getTime());

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            String siteId = extractSiteId(rs.getString(1));

            if (siteId != null) {
                result.add(new UpdatedSite(siteId, new Timestamp(rs.getLong(2))));
            }
        }

        rs.close();
        ps.close();
    }


    public List<UpdatedSite> listSince(Date since) {
        List<UpdatedSite> result = new ArrayList<UpdatedSite>();

        Timestamp sinceTime = new Timestamp(since.getTime() - MARGIN_OF_ERROR_MS);

        Connection db = null;
        try {
            db = sqlService.borrowConnection();
            // Sites whose realms were updated
            addUpdatedSites(db, "realm_id", "sakai_realm", sinceTime, "realm_id like '/site/%'", result);

            // Sites that were updated directly
            addUpdatedSites(db, "site_id", "sakai_site", sinceTime, "1 = 1", result);

            // Sites whose attached rosters were changed
            addSitesWithUpdatedRosters(db, sinceTime, result);

            // Sites whose Grouper sync status was changed
            addSitesWithGrouperSyncChanges(db, sinceTime, result);

        } catch (SQLException e) {
            throw new RuntimeException("DB error when looking for updated sites: " + e, e);
        } finally {
            if (db != null) {
                sqlService.returnConnection(db);
            }
        }

        return result;
    }
}

