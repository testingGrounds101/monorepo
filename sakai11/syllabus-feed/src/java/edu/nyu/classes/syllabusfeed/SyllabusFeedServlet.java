package edu.nyu.classes.syllabusfeed;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.cover.ContentHostingService;
import org.sakaiproject.db.cover.SqlService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;


public class SyllabusFeedServlet extends HttpServlet {

    private static final Log LOG = LogFactory.getLog(SyllabusFeedServlet.class);

    private static final String BASE_JOINS =
        (" from nyu_t_course_catalog cc " +
         " inner join sakai_realm_provider srp on srp.provider_id = replace(cc.stem_name, ':', '_')" +
         " inner join sakai_realm sr on sr.realm_key = srp.realm_key " +
         " inner join NYU_V_NON_COLLAB_SITES ncs on concat('/site/', ncs.site_id) = sr.realm_id" +
         " inner join sakai_site ss on concat('/site/', ss.site_id) = sr.realm_id" +
         " inner join sakai_syllabus_item ssi on ssi.contextId = ss.site_id" +
         " inner join sakai_syllabus_data ssd on ssd.surrogateKey = ssi.id AND ssd.status = 'posted'" +
         " inner join sakai_syllabus_attach ssa on ssa.syllabusId = ssd.id AND export = 1"
         );


    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        reportErrors(Arrays.asList("Request method not supported"), response);
        return;
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        if (request.getParameter("token") == null) {
            reportErrors(Arrays.asList("Missing parameter 'token': expected a HMAC token"), response);
            return;
        }

        ClientConfig clientConfig = determineClientIdentity(request);

        if (clientConfig == null) {
            reportErrors(Arrays.asList("Token unrecognized"), response);
            return;
        }

        if (request.getParameter("get") == null) {
            handleListing(clientConfig, request, response);
        } else {
            handleFetch(clientConfig, request, response);
        }
    }

    private class ClientConfig {
        public boolean restrictLocations;
        public List<String> allowedLocations;
        public String token;

        public ClientConfig(boolean restrictLocations, List<String> allowedLocations, String token) {
            this.restrictLocations = restrictLocations;
            this.allowedLocations = Objects.requireNonNull(allowedLocations);
            this.token = token;
        }
    }

    private ClientConfig determineClientIdentity(HttpServletRequest request)
        throws ServletException, IOException {
        String token = request.getParameter("token");

        String algorithm = HotReloadConfigurationService.getString("nyu.syllabus-feed.hmac-algorithm", "HmacSHA256");

        for (String client : HotReloadConfigurationService.getString("nyu.syllabus-feed.clients", "").split(" *, *")) {
            HMACSession hmac = new HMACSession(algorithm);

            String secret = HotReloadConfigurationService.getString("nyu.syllabus-feed.client." + client + ".secret", "");
            String restrictLocations = HotReloadConfigurationService.getString("nyu.syllabus-feed.client." + client + ".restrict-locations", "true");
            String allowedLocations = HotReloadConfigurationService.getString("nyu.syllabus-feed.client." + client + ".allowed-locations", "");

            long maxAge = Long.valueOf(HotReloadConfigurationService.getString("nyu.syllabus-feed.max-token-age", "86400000"));

            if (hmac.tokenOk(token, secret, maxAge)) {
                return new ClientConfig(!"false".equals(restrictLocations),
                                        Arrays.asList(allowedLocations.split(" *, *")),
                                        token);
            }
        }

        // Token invalid
        return null;
    }

    private void handleFetch(ClientConfig config, HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        String stemName = request.getParameter("get");

        List<String> errors = new ArrayList<>();

        if (stemName.isEmpty()) {
            errors.add("Missing parameter 'get': expected a valid roster stem name");
        }

        if (reportErrors(errors, response)) {
            return;
        }

        String locationRestriction = "";
        if (config.restrictLocations) {
            locationRestriction = "cc.location in (" +
                config.allowedLocations.stream().map(l -> "?").collect(Collectors.joining(", "))
                + ") AND ";
        }

        Connection conn = null;

        try {
            conn = SqlService.borrowConnection();

            try (PreparedStatement ps = conn.prepareStatement("select ssa.syllabusAttachName, ssa.syllabusAttachType, ssa.attachmentId" +
                                                              BASE_JOINS +
                                                              " where " +
                                                              locationRestriction +
                                                              " cc.stem_name = ?")) {
                int i = 0;
                if (config.restrictLocations) {
                    for (; i < config.allowedLocations.size(); i++) {
                        ps.setString(i + 1, config.allowedLocations.get(i));
                    }
                }

                ps.setString(i + 1, stemName);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String filename = rs.getString("syllabusAttachName");
                        String mimeType = rs.getString("syllabusAttachType");
                        String contentId = rs.getString("attachmentId");

                        SecurityService.pushAdvisor(new SecurityAdvisor() {
                            public SecurityAdvice isAllowed(String userId, String function, String reference) {
                                if (("/content" + contentId).equals(reference)) {
                                    return SecurityAdvice.ALLOWED;
                                } else {
                                    return SecurityAdvice.PASS;
                                }
                            }
                        });

                        try {
                            ContentResource resource = ContentHostingService.getResource(contentId);
                            InputStream stream = resource.streamContent();

                            response.setContentType(mimeType);
                            response.addHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", filename));

                            OutputStream out = response.getOutputStream();
                            byte[] buf = new byte[4096];

                            int len;
                            while ((len = stream.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                        } catch (PermissionException | IdUnusedException | ServerOverloadException | TypeException e) {
                            String msg = String.format("Error fetching resource for stem_name=%s: ",
                                                       stemName);
                            LOG.error(msg, e);
                            throw new ServletException(msg, e);
                        } finally {
                            SecurityService.popAdvisor();
                        }
                    } else {
                        response.setStatus(404);
                        response.setContentType("text/plain");
                        response.getWriter().write("Document not found");
                    }
                }
            }
        } catch (SQLException e) {
            String msg = String.format("Error processing request for stem_name=%s: ",
                                       stemName);
            LOG.error(msg, e);
            throw new ServletException(msg, e);
        } finally {
            if (conn != null) {
                SqlService.returnConnection(conn);
            }
        }
    }

    private void handleListing(ClientConfig config, HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        String locationCSV = request.getParameter("location");
        String strm = request.getParameter("strm");

        List<String> errors = new ArrayList<>();

        if (locationCSV == null) {
            errors.add("Missing parameter 'location': expected a comma-separated list of location codes");
        }

        if (strm == null) {
            errors.add("Missing parameter 'strm': expected a single strm STRM number (e.g. 1186)");
        } else {
            try {
                Long.valueOf(strm);
            } catch (NumberFormatException e) {
                errors.add("Invalid parameter 'strm': expected a single strm STRM number (e.g. 1186)");
            }
        }

        if (reportErrors(errors, response)) {
            return;
        }

        List<String> locations = Arrays.asList(locationCSV.split(" *, *"))
            .stream()
            .filter(location -> !config.restrictLocations || config.allowedLocations.indexOf(location) >= 0)
            .collect(Collectors.toList());

        handleQuery(config, strm, locations, response);
    }


    private boolean reportErrors(List<String> errors, HttpServletResponse response)
        throws IOException {
        if (errors.isEmpty()) {
            return false;
        }

        response.setContentType("text/plain");
        response.setStatus(400);
        Writer out = response.getWriter();
        out.write("Request contained the following errors:\n");
        for (String error: errors) {
            out.write("  * " + error);
            out.write("\n");
        }

        return true;
    }

    private void handleQuery(ClientConfig config,
                             String strm,
                             List<String> locationCodes,
                             HttpServletResponse response)
        throws ServletException, IOException {
        Connection conn = null;

        response.setContentType("text/json; charset=utf-8");

        if (locationCodes.isEmpty()) {
            response.getWriter().write("[]");
            return;
        }

        try {
            conn = SqlService.borrowConnection();

            JsonFactory jsonFactory = new JsonFactory();
            JsonGenerator json = jsonFactory.createGenerator(response.getOutputStream(), JsonEncoding.UTF8);

            String locationPlaceholders = locationCodes.stream().map(l -> "?").collect(Collectors.joining(", "));

            try (PreparedStatement ps = conn.prepareStatement("select cc.stem_name," +
                                                              "  cc.crse_id," +
                                                              "  cc.class_section," +
                                                              "  cc.catalog_nbr," +
                                                              "  cc.strm," +
                                                              "  cc.location," +
                                                              "  ssa.lastModifiedTime syllabus_last_modified," +
                                                              "  sr.modifiedon realm_modifiedon" +
                                                              BASE_JOINS +
                                                              " where cc.location in (" + locationPlaceholders + ") AND cc.strm = ?"
                                                              )) {
                int i = 0;
                for (; i < locationCodes.size(); i++) {
                    ps.setString(i + 1, locationCodes.get(i));
                }
                ps.setString(i + 1, strm);

                try (ResultSet rs = ps.executeQuery()) {
                    json.writeStartArray();
                    while (rs.next()) {
                        json.writeStartObject();
                        json.writeStringField("stem_name", rs.getString("stem_name"));
                        json.writeStringField("crse_id", rs.getString("crse_id"));
                        json.writeStringField("class_section", rs.getString("class_section"));
                        json.writeStringField("catalog_nbr", rs.getString("catalog_nbr"));
                        json.writeStringField("strm", rs.getString("strm"));
                        json.writeStringField("location", rs.getString("location"));

                        // URL, last_modified
                        long syllabusLastModified = rs.getLong("syllabus_last_modified");
                        long realmLastModified = rs.getDate("realm_modifiedon") == null ? 0 : rs.getDate("realm_modifiedon").getTime();

                        json.writeNumberField("last_modified", Math.max(syllabusLastModified, realmLastModified));
                        json.writeStringField("url",
                                              String.format("%s/syllabus-feed?get=%s&token=%s",
                                                            ServerConfigurationService.getServerUrl(),
                                                            URLEncoder.encode(rs.getString("stem_name"), "UTF-8"),
                                                            config.token));


                        json.writeEndObject();
                    }
                    json.writeEndArray();
                    json.close();
                }
            }
        } catch (SQLException e) {
            String msg = String.format("Error processing request for strm=%s; locations=%s: ",
                                       strm, locationCodes);
            LOG.error(msg, e);
            throw new ServletException(msg, e);
        } finally {
            if (conn != null) {
                SqlService.returnConnection(conn);
            }
        }
    }
}
