package edu.nyu.classes.nyuhome.feeds;

import edu.nyu.classes.nyuhome.api.DataFeed;

import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.time.api.Time;


abstract class SakaiToolFeed implements DataFeed {

    protected String buildUrl(String siteId, String toolId) {
        try {
            Site site = SiteService.getSite(siteId);
            ToolConfiguration toolConfiguration = site.getToolForCommonId(toolId);

            if (toolConfiguration == null) {
                // If the tool wasn't found in the site, we'll just link to the site itself.
                return ("/portal/site/" + siteId);
            } else {
                return ("/portal/directtool/" + toolConfiguration.getId());
            }
        } catch (IdUnusedException e) {
            return null;
        }
    }

}
