package edu.nyu.classes.groupersync.tool;

import edu.nyu.classes.groupersync.api.GrouperSyncException;
import edu.nyu.classes.groupersync.api.GrouperSyncService;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.tool.cover.ToolManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import javax.naming.Context;

public class GrouperSyncServlet extends HttpServlet {

    private static final Log log = LogFactory.getLog(GrouperSyncServlet.class);

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        checkAccessControl();

        if (Objects.toString(request.getPathInfo()).indexOf("/create_group") >= 0) {
            new CrudHandler().handleCreate(request, response);
        } else if (Objects.toString(request.getPathInfo()).indexOf("/update_group") >= 0) {
            new CrudHandler().handleUpdate(request, response);
        } else if (Objects.toString(request.getPathInfo()).indexOf("/delete_group") >= 0) {
            new CrudHandler().handleDelete(request, response);
        } else {
            throw new ServletException("Unrecognized request");
        }
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        checkAccessControl();

        if (Objects.toString(request.getPathInfo()).indexOf("/members") >= 0) {
            new ListMembersHandler().handle(request, response);
        } else if (Objects.toString(request.getPathInfo()).indexOf("/check_group") >= 0) {
            new CheckGroupHandler().handle(request, response);
        } else {
            new IndexHandler().handle(request, response);
        }
    }


    private void checkAccessControl() throws ServletException {
        String siteId = ToolManager.getCurrentPlacement().getContext();

        if (!SecurityService.unlock("site.upd", "/site/" + siteId)) {
            log.error("Access denied to GrouperSync management tool for user " + SessionManager.getCurrentSessionUserId());
            throw new ServletException("Access denied");
        }
    }
}
