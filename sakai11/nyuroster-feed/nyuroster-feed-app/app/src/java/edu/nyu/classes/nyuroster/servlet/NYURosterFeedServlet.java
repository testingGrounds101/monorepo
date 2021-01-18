package edu.nyu.classes.nyuroster.servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.sakaiproject.db.cover.SqlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NYURosterFeedServlet extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(NYURosterFeedServlet.class);

    // THINKME: Make the following configuration items hot reloadable?
    // Set relatively low to avoid hitting the max Oracle clause limit.  We
    // could make this higher, but we'd need to fire multiple Oracle queries for
    // long lists.
    private static final int MAX_QUERY = 200;

    // Don't let someone request *all* sections as we'll load them into memory...
    private static final int MAX_ALLOWED_RESULTS = 100000;

    private class AccessDeniedException extends Exception {}

    private String allowedIPPatternConfig = "127\\.0\\.0\\.1";

    // POST [this]
    // Content-type: text/json
    //
    // ["SP15:SOME-CE:1000:S:001", "SP15:SOME-CE:1000:S:002", "SP15:SOME-CE:1000:S:003"]
    //
    // and we respond with a mapping of rosters to sites, like:
    //
    // 200 OK
    // Content-type: text/json
    //
    // {
    //   "SP15:SOME-CE:1000:S:001": "77312fda-3c2b-40f8-9f44-71577175eb8f",
    //   "SP15:SOME-CE:1000:S:002": "d4a202ca-dc1c-43ed-8195-e71007576135",
    //   "SP15:SOME-CE:1000:S:003": null
    // }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        checkAccessPermission(request);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode query = mapper.readTree(request.getInputStream());

        if (query.size() > MAX_QUERY) {
            throw new ServletException(String.format("Can't query more than %d items in a single request",
                                                     MAX_QUERY));
        }

        List<String> rosters = new ArrayList<String>(query.size());
        for (JsonNode elt : query) {
            rosters.add(elt.asText());
        }

        try {
            renderResponse(response, findMatchingSiteIds(rosters));
        } catch (Exception e) {
            renderError(response, e);
        }
    }

    // Two types of GET request are supported:
    //
    //   GET [this]?rosterId=some_specific_id -- we respond with the same mapping response as the above POST
    //
    //   GET [this]?schoolCodes=UB,GB -- we respond with a mapping of all rosters for a given school
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        checkAccessPermission(request);

        String rosterId = request.getParameter("rosterId");
        String schoolCodes = request.getParameter("schoolCodes");

        if (nullOrEmpty(rosterId) && nullOrEmpty(schoolCodes)) {
            throw new ServletException("Either need a value for parameter 'rosterId', or a comma-separated list of 'schoolCodes'");
        }

        try {
            if (nullOrEmpty(rosterId)) {
                // Search by school code
                String startDateString = request.getParameter("startDate");

                Date startDate;
                try {
                    startDate = new SimpleDateFormat("yyyy-MM-dd").parse(startDateString);
                } catch (Exception e) {
                    LOG.error("startDate parse failed: " + e);
                    e.printStackTrace();
                    throw new ServletException("Missing or invalid value for 'startDate' parameter.  Should be: yyyy-mm-dd");
                }

                renderResponse(response, findBySchoolCode(Arrays.asList(schoolCodes.split(", *")),
                                                          startDate));
            } else {
                // Search by specific roster
                renderResponse(response, findMatchingSiteIds(Arrays.asList(new String[] { rosterId })));
            }
        } catch (Exception e) {
            renderError(response, e);
        }
    }

    private static boolean nullOrEmpty(String val) {
        return (val == null || val.isEmpty());
    }

    private void renderResponse(HttpServletResponse response, Map<String, String> rosterToSiteMapping)
        throws ServletException {
        ObjectMapper mapper = new ObjectMapper();

        try {
            response.setContentType("text/json");
            response.setStatus(200);
            mapper.writeValue(response.getOutputStream(), rosterToSiteMapping);
        } catch (Exception e) {
            renderError(response, e);
        }
    }

    private void renderError(HttpServletResponse response, Throwable e) throws ServletException {
        response.setContentType("text/plain");
        response.setStatus(500);
        throw new ServletException(e);
    }

    // Given a list of roster IDs, find the sites that they are attached to.
    //
    // If a roster has been attached to more than one site, it's undefined which
    // one you'll get back.
    private Map<String, String> findMatchingSiteIds(final List<String> rosters) throws Exception {
        final Map<String, String> result = new HashMap<String, String>(rosters.size());

        for (String roster : rosters) {
            result.put(roster, null);
        }

        DB.connection(new DBAction() {
            public void execute(Connection connection) throws SQLException {
                PreparedStatement ps = null;
                ps = connection.prepareStatement(String.format("select srp.provider_id, sr.realm_id" +
                                                               " from sakai_realm_provider srp" +
                                                               " inner join sakai_realm sr on sr.realm_key = srp.realm_key" +
                                                               " where srp.provider_id in (%s)" +
                                                               "   AND sr.realm_id NOT like '%%/group/%%'",
                                                               DB.placeholders(rosters)));
                try {
                    for (int i = 0; i < rosters.size(); i++) {
                        ps.setString(i + 1, rosters.get(i));
                    }

                    ResultSet rs = ps.executeQuery();

                    while (rs.next()) {
                        String siteId = extractSite(rs.getString("realm_id"));
                        if (siteId != null) {
                            result.put(rs.getString("provider_id"), siteId);
                        }
                    }

                    rs.close();
                } finally {
                    connection.commit();
                    if (ps != null) {
                        ps.close();
                    }
                }
            }
        });

        return result;
    }

    private class TooManyResultsException extends RuntimeException {}

    private Map<String, String> findBySchoolCode(final List<String> schoolCodes, final Date startDate) throws Exception {
        for (String schoolCode : schoolCodes) {
            if (!schoolCode.matches("^[A-Za-z0-9]+$")) {
                throw new IllegalArgumentException("Invalid school code");
            }
        }

        final Map<String, String> result = new HashMap<String, String>();

        DB.connection(new DBAction() {
            public void execute(Connection connection) throws SQLException {
                PreparedStatement ps = null;

                String query = String.format("select srp.provider_id, sr.realm_id" +
                                                    " from nyu_t_course_catalog cc " +
                                                    " inner join sakai_realm_provider srp on srp.provider_id = replace(cc.stem_name, ':', '_')" +
                                                    " inner join sakai_realm sr on sr.realm_key = srp.realm_key" +
                                                    " inner join NYU_V_NON_COLLAB_SITES ncs on concat('/site/', ncs.site_id) = sr.realm_id" +
                                                    " where cc.acad_group in (%s) AND sr.realm_id NOT like '%%/group/%%' AND cc.effdt >= ?",
                                                    DB.placeholders(schoolCodes));

                ps = connection.prepareStatement(query);

                for (int i = 0; i < schoolCodes.size(); i++) {
                    ps.setString(i + 1, schoolCodes.get(i));
                }

                ps.setDate(schoolCodes.size() + 1, new java.sql.Date(startDate.getTime()));

                ResultSet rs = null;

                try {
                    rs = ps.executeQuery();
                    int rowCount = 0;
                    while (rs.next()) {
                        rowCount++;

                        if (rowCount >= MAX_ALLOWED_RESULTS) {
                            throw new TooManyResultsException();
                        }

                        String siteId = extractSite(rs.getString("realm_id"));
                        if (siteId != null) {
                            result.put(rs.getString("provider_id"), siteId);
                        }
                    }
                } finally {
                    if (rs != null) { rs.close(); }
                    if (ps != null) { ps.close(); }
                }
            }
        });

        return result;
    }


    private String extractSite(String realmId) {
        if (realmId.matches("/site/[a-z0-9-]{36}")) {
            return realmId.substring("/site/".length());
        } else {
            return null;
        }
    }

    private void checkAccessPermission(HttpServletRequest req)
        throws ServletException {
        String remoteAddr = req.getRemoteAddr();

        String pattern = HotReloadConfigurationService.getString("nyuroster.allowed.ips", allowedIPPatternConfig);

        if (!remoteAddr.matches(pattern)) {
            throw new ServletException(new AccessDeniedException());
        }
    }

    /// DB shim
    interface DBAction {
        public void execute(Connection conn) throws SQLException;
    }

    static class DB {
        public static String placeholders(List<?> l) {
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < l.size(); i++) {
                if (sb.length() > 0) {
                    sb.append(",");
                }

                sb.append("?");
            }

            return sb.toString();
        }

        public static void connection(DBAction action) throws SQLException {
            Connection connection = null;
            boolean oldAutoCommit;

            try {
                connection = SqlService.borrowConnection();
                oldAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);

                action.execute(connection);

                connection.setAutoCommit(oldAutoCommit);
            } finally {
                if (connection != null) {
                    SqlService.returnConnection(connection);
                }
            }
        }
    }
}
