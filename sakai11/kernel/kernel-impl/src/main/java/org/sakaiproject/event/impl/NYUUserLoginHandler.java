package org.sakaiproject.event.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.sakaiproject.db.cover.SqlService;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.Tool;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NYUUserLoginHandler {

    private final static String OUR_ADDED_PROPERTY = "nyu-login-handler-added";
    private static Logger LOG = LoggerFactory.getLogger(NYUUserLoginHandler.class);


    public void invoke(Session sakaiSession) {
        long startTime = System.currentTimeMillis();
        Connection connection = null;

        List<Exception> exceptions = new ArrayList<>();

        try {
            connection = SqlService.borrowConnection();

            try {
                handleAutoTools(sakaiSession, connection);
            } catch (Exception e) {
                exceptions.add(e);
            }

            try {
                handleInstructorAutoTools(sakaiSession, connection);
            } catch (Exception e) {
                exceptions.add(e);
            }

            try {
                handleMyConnectionVisibility(sakaiSession, connection);
            } catch (Exception e) {
                exceptions.add(e);
            }

        } catch (SQLException e) {
            exceptions.add(e);
        } finally {
            if (connection != null) {
                SqlService.returnConnection(connection);
            }
        }

        LOG.info("Processed user tools in " + (System.currentTimeMillis() - startTime) + " ms");

        if (!exceptions.isEmpty()) {
            LOG.error(String.format("Failure in NYU Login handler for user '%s'",
                                    ((sakaiSession != null) ? sakaiSession.getUserId() : "<null>")));

            for (Exception e : exceptions) {
                e.printStackTrace();
            }
        }
    }


    private void handleMyConnectionVisibility(Session sakaiSession, Connection connection) throws Exception {
        String userId = sakaiSession.getUserId();

        if (userId == null || userId.isEmpty()) {
            return;
        }

        PreparedStatement ps = null;
        ResultSet rs = null;


        try {
            // If the user is a member of any site with the
            // "my-connections-widget-activated" property, show the widget.
            ps = connection.prepareStatement("select count(1)" +
                                             " from sakai_site_user ssu" +
                                             " inner join sakai_site_property ssp on ssu.site_id = ssp.site_id" +
                                             "   AND ssp.name = ?" +
                                             " where ssu.user_id = ?");

            ps.setString(1, "my-connections-widget-activated");
            ps.setString(2, userId);

            rs = ps.executeQuery();

            if (rs.next()) {
                if (rs.getInt(1) == 1) {
                    sakaiSession.setAttribute("my-connections-widget-activated", "true");
                }
            }
        } finally {
            if (ps != null) {
                try { ps.close (); } catch (Exception e) {}
            }
            if (rs != null) {
                try { rs.close (); } catch (Exception e) {}
            }
        }
    }


    // In situations where database connectivity is unreliable,
    // SiteService.getSite will cherrily return incorrect results (such as sites
    // with pages and tools missing).  When we write these back to the database
    // we risk losing data.
    //
    // Mitigate by doing our own checks against the database.
    private Site getWorkspaceWithSanityChecking(String userId, Connection connection) throws IdUnusedException, SQLException {
        String siteId = "~" + userId;
        Site workspace = SiteService.getSite(siteId);

        try (PreparedStatement pageCountQuery = connection.prepareStatement("select count(1) from sakai_site_page where site_id = ?")) {
            try (PreparedStatement toolCountQuery = connection.prepareStatement("select count(1) from sakai_site_tool where site_id = ?")) {

                int pageCount = 0;
                int toolCount = 0;

                pageCountQuery.setString(1, siteId);
                toolCountQuery.setString(1, siteId);

                try (ResultSet rs = pageCountQuery.executeQuery()) {
                    if (rs.next()) {
                        pageCount = rs.getInt(1);
                    }
                }

                try (ResultSet rs = toolCountQuery.executeQuery()) {
                    if (rs.next()) {
                        toolCount = rs.getInt(1);
                    }
                }

                if (workspace.getPages().size() != pageCount) {
                    throw new RuntimeException(String.format("Mismatch on page count for user workspace '%s': getSite said %d but we saw %d",
                                                             userId,
                                                             workspace.getPages().size(),
                                                             pageCount));
                }

                int toolCountFromWorkspace = workspace
                    .getPages()
                    .stream()
                    .collect(Collectors.summingInt(page -> page.getTools().size()));

                if (toolCountFromWorkspace != toolCount) {
                    throw new RuntimeException(String.format("Mismatch on tool count for user workspace '%s': getSite said %d but we saw %d",
                                                             userId,
                                                             toolCountFromWorkspace,
                                                             toolCount));
                }
            }
        }


        return workspace;
    }

    private void handleAutoTools(Session sakaiSession, Connection connection) throws Exception {
        String userId = sakaiSession.getUserId();
        Site workspace = getWorkspaceWithSanityChecking(userId, connection);

        String toolsToAdd = HotReloadConfigurationService.getString("nyu.auto-tools.add", "");
        String toolsToRemove = HotReloadConfigurationService.getString("nyu.auto-tools.remove", "");

        if (!"".equals(toolsToRemove)) {
            removeToolsIfPresent(workspace, toolsToRemove.split(", *"));
        }

        if (!"".equals(toolsToAdd)) {
            addToolsIfMissing(workspace, toolsToAdd.split(", *"));
        }
    }


    private void handleInstructorAutoTools(Session sakaiSession, Connection connection) throws Exception {
        String userId = sakaiSession.getUserId();

        String instructorToolsStr = HotReloadConfigurationService.getString("nyu.instructor-auto-tools", "");

        if ("".equals(instructorToolsStr.trim())) {
            return;
        }

        String[] instructorTools = instructorToolsStr.split(", *");
        User user = UserDirectoryService.getUser(userId);

        // Creates the workspace if it doesn't already exist
        Site workspace = getWorkspaceWithSanityChecking(userId, connection);

        if (isInstructor(user, connection)) {
            addToolsIfMissing(workspace, instructorTools);
        } else {
            removeToolsIfPresent(workspace, instructorTools);
        }
    }


    private void addToolsIfMissing(Site workspace, String[] instructorTools) throws Exception {
        Set<String> foundTools = new HashSet<>(16);

        // Add missing
        for (SitePage page : workspace.getPages()) {
            for (ToolConfiguration tool : page.getTools()) {
                foundTools.add(tool.getToolId());
            }
        }

        boolean updated = false;

        for (String registration : instructorTools) {
            if (!foundTools.contains(registration)) {
                Tool tool = ToolManager.getTool(registration);

                LOG.info("Adding tool '{}' to workspace {}", registration, workspace.getId());
                SitePage page = workspace.addPage();
                page.setTitle(tool.getTitle());
                ResourcePropertiesEdit properties = page.getPropertiesEdit();
                properties.addProperty(OUR_ADDED_PROPERTY, "true");

                page.addTool(tool);
                updated = true;
            }
        }

        if (updated) {
            SiteService.save(workspace);
        }
    }

    private void removeToolsIfPresent(Site workspace, String[] instructorTools) throws Exception {
        List<SitePage> toRemove = new ArrayList<>();

        for (SitePage page : workspace.getPages()) {
            if (!"true".equals(page.getProperties().getProperty("nyu-login-handler-added"))) {
                continue;
            }

            List<ToolConfiguration> tools = page.getTools();

            ToolConfiguration tool = tools.get(0);
            for (String registration : instructorTools) {
                if (registration.equals(tool.getToolId())) {
                    LOG.info("Removing tool '{}' from workspace {}", registration, workspace.getId());
                    toRemove.add(page);
                    break;
                }
            }
        }

        if (!toRemove.isEmpty()) {
            for (SitePage page : toRemove) {
                workspace.removePage(page);
            }

            SiteService.save(workspace);
        }
    }

    private boolean isInstructor(User user, Connection connection) throws SQLException {
        String eid = user.getEid();

        if (eid == null) {
            return false;
        }

        String pretendInstructors = HotReloadConfigurationService.getString("nyu.pretend-instructors", "");
        if (("," + pretendInstructors.trim().replace(" ", "") + ",").indexOf("," + eid + ",") >= 0) {
            return true;
        }

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            if (SqlService.getVendor().equals("oracle")) {
                ps = connection.prepareStatement("select 1 from nyu_t_instructors where netid = ? AND rownum <= 1");
            } else {
                // MySQL
                ps = connection.prepareStatement("select 1 from nyu_t_instructors where netid = ? LIMIT 1");
            }
            ps.setString(1, eid);
            rs = ps.executeQuery();

            if (rs.next()) {
                return true;
            }

            return false;
        } finally {
            if (ps != null) {
                try { ps.close (); } catch (Exception e) {}
            }
            if (rs != null) {
                try { rs.close (); } catch (Exception e) {}
            }
        }
    }

}
