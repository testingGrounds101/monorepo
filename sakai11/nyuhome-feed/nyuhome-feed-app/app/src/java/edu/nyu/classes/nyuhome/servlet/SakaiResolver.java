package edu.nyu.classes.nyuhome.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.component.cover.ServerConfigurationService;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;

import edu.nyu.classes.nyuhome.api.Resolver;

public class SakaiResolver implements Resolver {
    private Set<String> users;
    private Set<String> sites;

    private UserDirectoryService userDirectoryService;
    private SiteService siteService;

    private static final Logger LOG = LoggerFactory.getLogger(SakaiResolver.class);


    public SakaiResolver(UserDirectoryService uds, SiteService ss) {
        userDirectoryService = uds;
        siteService = ss;

        this.users = new HashSet<String>();
        this.sites = new HashSet<String>();
    }

    public void addUser(String userId) {
        users.add(userId);
    }


    public void addSite(String siteId) {
        sites.add(siteId);
    }


    public Map<String, Map<String, String>> toMap() {
        Map<String, Map<String, String>> result = new HashMap<String, Map<String, String>>();

        Map<String, String> resolvedUsers = new HashMap<String, String>();
        for (User user : userDirectoryService.getUsers(this.users)) {
            resolvedUsers.put(user.getId(), user.getDisplayName());
        }

        Map<String, String> resolvedSites = new HashMap<String, String>();
        for (String siteId : this.sites) {
            try {
                Site site = siteService.getSite(siteId);
                resolvedSites.put(siteId, site.getTitle());
            } catch (IdUnusedException e) {
                LOG.warn("ID not found: " + siteId, e);
            }
        }

        Map<String, String> properties = new HashMap<String, String>();

        String url = ServerConfigurationService.getString("serverUrl");

        if (url != null && !url.endsWith("/")) {
            url += "/";
        }

        properties.put("classes_url", url);

        result.put("properties", properties);
        result.put("users", resolvedUsers);
        result.put("sites", resolvedSites);

        return result;
    }
}
