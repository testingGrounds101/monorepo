package edu.nyu.classes.externalhelp.impl;

import edu.nyu.classes.externalhelp.api.ExternalHelpSystem;
import edu.nyu.classes.externalhelp.api.ExternalHelp;

import org.sakaiproject.component.cover.ServerConfigurationService;
import java.util.Map;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

public class ExternalHelpSystemImpl implements ExternalHelpSystem {

    private static Log LOG = LogFactory.getLog(ExternalHelpSystemImpl.class);
    private static String MAIN_HELP_TOOL_ID = "__help__";


    private class HelpSet {
        private Map<String, ExternalHelp> help;
        private Map<String, ExternalHelp> news;

        public HelpSet(String userRole) {
            help = readHelpEntries("externalHelp.help." + userRole);
            news = readHelpEntries("externalHelp.news." + userRole);
        }


        public ExternalHelp getHelp(String toolId) { return help.get(toolId); }
        public ExternalHelp getNews(String toolId) { return news.get(toolId); }


        private Map<String, ExternalHelp> readHelpEntries(String basename) {
            int count = ServerConfigurationService.getInt(basename + ".count", 0);

            Map<String, ExternalHelp> result = new HashMap<String, ExternalHelp>(count);

            for (int i = 1; i <= count; i++) {
                try {
                    String tool = ServerConfigurationService.getString(String.format("%s.%d.tool", basename, i));
                    String serviceLinkUrl = ServerConfigurationService.getString(String.format("%s.%d.url", basename, i));
                    String label = ServerConfigurationService.getString(String.format("%s.%d.label", basename, i));

                    String url = ServerConfigurationService.getPortalUrl() + String.format("/show-help?key=%s.%d&url=%s",
                                                                                           basename, i,
                                                                                           URLEncoder.encode(serviceLinkUrl, "UTF-8"));

                    result.put(tool, new ExternalHelpImpl(tool, url, label));
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }

            return result;
        }


        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append("\n");
            sb.append("Help entries:\n");
            for (ExternalHelp entry : help.values()) {
                sb.append("  <" + entry.toString() + ">\n");
            }

            sb.append("News entries:\n");
            for (ExternalHelp entry : news.values()) {
                sb.append("  <" + entry.toString() + ">\n");
            }

            sb.append("\n");

            return sb.toString();
        }
    }


    private boolean enabled;
    private Map<String, HelpSet> roleHelpSets;

    public void init() {
        enabled = ServerConfigurationService.getBoolean("externalHelp.enabled", false);

        if (enabled) {
            LOG.info("External help system enabled");
        } else {
            LOG.info("External help system disabled");
            return;
        }

        roleHelpSets = new HashMap<String, HelpSet>();
        String[] userRoles = ServerConfigurationService.getStrings("externalHelp.user-roles");

        if (userRoles == null) {
            return;
        }

        for (String userRole : userRoles) {
            HelpSet roleHelpSet = new HelpSet(userRole);
            roleHelpSets.put(userRole, roleHelpSet);

            LOG.info(String.format("Added help set for role '%s': %s", userRole, roleHelpSet));
        }
    }

    public void destroy() {
        // Do nothing!
    }


    public boolean isActive() {
        return enabled;
    }

    public ExternalHelp getHelp(String toolId, String userRole) {
        if (!roleHelpSets.containsKey(userRole)) {
            return null;
        }

        return roleHelpSets.get(userRole).getHelp(toolId);
    }


    public ExternalHelp getNews(String toolId, String userRole) {
        if (!roleHelpSets.containsKey(userRole)) {
            return null;
        }

        return roleHelpSets.get(userRole).getNews(toolId);
    }


    public ExternalHelp getMainHelp() { return getHelp(MAIN_HELP_TOOL_ID, "global"); }
}
