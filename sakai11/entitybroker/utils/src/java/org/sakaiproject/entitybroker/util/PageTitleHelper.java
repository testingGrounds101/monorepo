package org.sakaiproject.entitybroker.util;

import java.util.Arrays;

public class PageTitleHelper {

    private static String[] prefixedTools = new String[] {
        "Calendar",
        "Resources",
        "Announcements"
    };

    public static String prefixTitle(String siteId, String title) {
        if (siteId.startsWith("~") && (!siteId.equals("~"))) {
            if (Arrays.asList(prefixedTools).contains(title)) {
                return "My " + title;
            }
        }

        return title;
    }
}
