package edu.nyu.classes.groupersync.tool;

import java.io.IOException;
import edu.nyu.classes.groupersync.api.GrouperSyncException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.tool.cover.ToolManager;
import javax.servlet.ServletException;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import edu.nyu.classes.groupersync.api.GrouperSyncService;
import org.sakaiproject.tool.cover.SessionManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.site.api.Site;
import edu.nyu.classes.groupersync.api.GroupInfo;
import java.util.regex.Pattern;


public class CrudHandler extends BaseHandler {

    private static final Log log = LogFactory.getLog(CrudHandler.class);

    public void handleCreate(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        try {
            if (!checkCSRFToken(request.getParameterMap())) {
                throw new ServletException("CSRF token check failed");
            }

            GrouperSyncService grouper = getGrouperSyncService();
            String siteId = ToolManager.getCurrentPlacement().getContext();

            Site site = SiteService.getSite(siteId);

            String requiredSuffix = buildRequiredSuffix(site);

            String groupId = getRequestGroupId(request, requiredSuffix);
            String description = getRequestDescription(request);
            String sakaiGroupId = request.getParameter("sakaiGroupId");

            if (groupId == null || "".equals(groupId.trim())) {
                throw new ServletException("Invalid group provided!");
            }

            sakaiGroupId = findMatchingGroupId(siteId, sakaiGroupId);
            String suffixedGroupId = groupId + requiredSuffix;

            grouper.markGroupForSync(suffixedGroupId,
                    new GrouperIdFormatter().format(suffixedGroupId),
                    sakaiGroupId,
                    description);

            response.sendRedirect(determineBaseURL().toString() + "?info=group_created");

        } catch (IdUnusedException e) {
            throw new ServletException("Failed to find site", e);
        } catch (GrouperSyncException e) {
            log.error("Error when creating group", e);
            response.sendRedirect(determineBaseURL().toString() + "?error=group_in_use");
        }
    }


    public void handleUpdate(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        try {
            if (!checkCSRFToken(request.getParameterMap())) {
                throw new ServletException("CSRF token check failed");
            }

            GrouperSyncService grouper = getGrouperSyncService();
            String siteId = ToolManager.getCurrentPlacement().getContext();

            String sakaiGroupId = request.getParameter("sakaiGroupId");
            String description = getRequestDescription(request);

            GroupInfo info = grouper.getGroupInfo(findMatchingGroupId(siteId, sakaiGroupId));

            grouper.updateDescription(info.getGrouperId(), description);

            response.sendRedirect(determineBaseURL().toString() + "?success=group_updated");
        } catch (IdUnusedException e) {
            throw new ServletException("Failed to find site", e);
        } catch (GrouperSyncException e) {
            response.sendRedirect(determineBaseURL().toString() + "?error=updated_failed");
        }
    }


    public void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        try {
            if (!checkCSRFToken(request.getParameterMap())) {
                throw new ServletException("CSRF token check failed");
            }

            GrouperSyncService grouper = getGrouperSyncService();
            String siteId = ToolManager.getCurrentPlacement().getContext();

            String sakaiGroupId = request.getParameter("sakaiGroupId");

            GroupInfo info = grouper.getGroupInfo(findMatchingGroupId(siteId, sakaiGroupId));

            grouper.deleteGroup(info.getGrouperId());

            response.sendRedirect(determineBaseURL().toString() + "?success=group_deleted");
        } catch (IdUnusedException e) {
            throw new ServletException("Failed to find site", e);
        } catch (GrouperSyncException e) {
            response.sendRedirect(determineBaseURL().toString() + "?error=delete_failed");
        }
    }


    private String findMatchingGroupId(String siteId, String sakaiGroupId) throws ServletException, IdUnusedException {
        if (siteId.equals(sakaiGroupId)) {
            // Creating the 'all' users link.
            return siteId;
        } else {
            Site site = SiteService.getSite(siteId);
            Group group = site.getGroup(sakaiGroupId);

            if (group == null) {
                throw new ServletException("Group not found");
            }

            return group.getId();
        }
    }


    private boolean checkCSRFToken(Map<String, String[]> params) {
        Object sessionToken = SessionManager.getCurrentSession().getAttribute("sakai.csrf.token");

        String[] fromParams = params.get("sakai_csrf_token");

        if (fromParams == null || fromParams.length != 1) {
            return false;
        }

        if (sessionToken == null || !sessionToken.equals(fromParams[0])) {
            return false;
        }

        return true;
    }


    private String getRequestDescription(HttpServletRequest request) throws ServletException {
	String description = request.getParameter("description");

	if (description.length() > Configuration.getMaxDescriptionLength()) {
	    throw new ServletException("Description too long!");
	}

	Pattern invalidRegexp = Pattern.compile("[" + Configuration.getDescriptionExcludedCharacters() + "]");

	if (invalidRegexp.matcher(description).find()) {
	    throw new ServletException("Invalid group name");
	}

	return description;
    }

    private String getRequestGroupId(HttpServletRequest request, String suffix) throws ServletException {
	String groupId = request.getParameter("groupId");

	if ((groupId.length() + suffix.length()) > Configuration.getMaxAddressLength()) {
	    throw new ServletException("Address too long!");
	}

	Pattern invalidRegexp = Pattern.compile("[^" + Configuration.getAddressAllowedCharacters() + Configuration.getWhitespaceReplacementCharacter() + "]");

	if (invalidRegexp.matcher(groupId).find()) {
	    throw new ServletException("Invalid address");
	}

	return groupId;
    }

}
