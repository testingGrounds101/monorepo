package edu.nyu.classes.seats;

import java.util.MissingResourceException;
import org.sakaiproject.util.ResourceLoader;

public class I18n {

    private ResourceLoader resourceLoader;

    public I18n(ClassLoader loader, String resourceBase) {
        resourceLoader = new ResourceLoader(resourceBase, loader);
    }

    public String t(String key) {
        String result = resourceLoader.getString(key);

        if (result == null) {
            throw new RuntimeException("Missing translation for key: " + key);
        }

        return result;
    }
}