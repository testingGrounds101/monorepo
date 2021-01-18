package edu.nyu.classes.groupersync.tool;

import edu.nyu.classes.groupersync.api.GrouperSyncService;
import javax.servlet.ServletException;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import edu.nyu.classes.groupersync.api.GrouperSyncException;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.exception.IdUnusedException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class CheckGroupHandler extends BaseHandler {

    private static final Log log = LogFactory.getLog(CheckGroupHandler.class);

    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        response.setHeader("Content-Type", "text/plain");

        GrouperSyncService grouper = getGrouperSyncService();
        String groupId = request.getParameter("groupId");

        String siteId = ToolManager.getCurrentPlacement().getContext();
        try {
            Site site = SiteService.getSite(siteId);

            if (grouper.isGroupAvailable(groupId + buildRequiredSuffix(site))) {
                // Yep, available
                response.setStatus(200);
                return;
            }
        } catch (GrouperSyncException e) {
            log.error("Error from grouper service:" + e);
        } catch (IdUnusedException e) {
        }

        // No!  Bad!
        response.setStatus(409);
    }
}
