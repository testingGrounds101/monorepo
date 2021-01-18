package org.sakaiproject.component.cover;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
   NYU modification: provide support for reading sakai.properties at runtime

 */
public class HotReloadConfigurationService
{
    private static Log LOG = LogFactory.getLog(HotReloadConfigurationService.class);

    private static long MAX_STALE_MS = 60000;
    private static long lastCheckTime = 0;
    private static long mtimeSeenAtLastCheck = 0;

    private static Properties _properties;


    private static synchronized Properties refreshProperties() {
        long now = System.currentTimeMillis();

        if ((now - lastCheckTime) < MAX_STALE_MS) {
            // No refresh needed
            return _properties;
        }

        File sakaiProperties = new File(ServerConfigurationService.getSakaiHomePath() + "/sakai.properties");
        long currentLastModifiedTime = sakaiProperties.lastModified();

        if (mtimeSeenAtLastCheck == currentLastModifiedTime) {
            // No refresh needed
            lastCheckTime = now;
            return _properties;
        }

        LOG.info("Reloading properties file: " + sakaiProperties);

        FileInputStream input = null;

        try {
            input = new FileInputStream(sakaiProperties);
            Properties newProperties = new Properties();
            newProperties.load(input);

            _properties = newProperties;
            lastCheckTime = now;
            mtimeSeenAtLastCheck = currentLastModifiedTime;
        } catch (IOException e) {
            LOG.error("Exception during properties load: " + e);
            e.printStackTrace();

            if (input != null) {
                try {
                    input.close();
                } catch (IOException e2) {
                    LOG.error("Nested exception during close: " + e2);
                    e2.printStackTrace();
                }
            }
        }

        return _properties;
    }


    public static String getString(String value, String defaultValue) {
        Properties properties = refreshProperties();

        if (properties == null) {
            LOG.error("No properties were available.  Returning default value");
            return defaultValue;
        }

        String result = (String)properties.get(value);

        if (result == null) {
            return defaultValue;
        } else {
            return result;
        }
    }
}
