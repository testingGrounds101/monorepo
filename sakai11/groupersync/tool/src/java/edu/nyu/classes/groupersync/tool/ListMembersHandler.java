package edu.nyu.classes.groupersync.tool;

import java.io.IOException;
import javax.servlet.ServletException;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.user.api.User;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.sakaiproject.exception.IdUnusedException;
import java.util.List;
import java.util.HashMap;


public class ListMembersHandler extends BaseHandler {

    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        response.setHeader("Content-Type", "text/json");
        JSONArray result = new JSONArray();

        try {
            String siteId = ToolManager.getCurrentPlacement().getContext();
            Site site = SiteService.getSite(siteId);
            String sakaiGroupId = request.getParameter("sakaiGroupId");

            AuthzGroup target = null;

            if (siteId.equals(sakaiGroupId)) {
                target = site;
            } else {
                target = site.getGroup(sakaiGroupId);
            }

            if (target != null) {
                Map<String, JSONObject> usersByEid = new HashMap<String, JSONObject>();

                // One pass to gather up EIDs and find roles
                for (Member m : target.getMembers()) {
                    JSONObject obj = new JSONObject();
                    obj.put("eid", m.getUserEid());
                    obj.put("role", m.getRole().getId());

                    // This will be replaced once we do our lookup
                    obj.put("name", "");

                    usersByEid.put(m.getUserEid(), obj);
                    result.add(obj);
                }

                // and one pass to get everyone's names.
                List<User> users = UserDirectoryService.getUsersByEids(usersByEid.keySet());

                for (User user : users) {
                    JSONObject obj = usersByEid.get(user.getEid());
                    obj.put("name", user.getDisplayName());
                }
            }
        } catch (IdUnusedException e) {
            throw new ServletException("Couldn't find site", e);
        }

        response.getWriter().write(result.toString());
    }

}
