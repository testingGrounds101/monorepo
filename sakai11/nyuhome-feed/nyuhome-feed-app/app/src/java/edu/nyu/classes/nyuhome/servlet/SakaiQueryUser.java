package edu.nyu.classes.nyuhome.servlet;

import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.site.api.SiteService.SelectionType;
import org.sakaiproject.site.api.SiteService.SortType;

import edu.nyu.classes.nyuhome.api.QueryUser;

import java.util.List;
import java.util.ArrayList;

public class SakaiQueryUser implements QueryUser {
    private static int MAX_SITES = 10;

    private User user;

    public SakaiQueryUser(String netid) throws UserNotDefinedException {
        user = UserDirectoryService.getUserByEid(netid);
    }


    public String getId() {
        return user.getId();
    }


    // TODO: cache this result
    public List<String> listSites() {
        List<Site> sites = SiteService.getSites(SelectionType.ACCESS,
                                                null, null, null,
                                                SortType.CREATED_ON_DESC,
                                                null);
        List<String> result = new ArrayList<String>();

        for (Site site : sites) {
            if (result.size() >= MAX_SITES) {
                break;
            }

            result.add(site.getId());
        }

        return result;
    }
}
