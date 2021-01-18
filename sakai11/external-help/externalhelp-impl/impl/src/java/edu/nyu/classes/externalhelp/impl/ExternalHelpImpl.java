package edu.nyu.classes.externalhelp.impl;

import edu.nyu.classes.externalhelp.api.ExternalHelp;


public class ExternalHelpImpl implements ExternalHelp {
    private String tool;
    private String url;
    private String label;

    public ExternalHelpImpl(String tool, String url, String label) {
        this.tool = tool;
        this.url = url;
        this.label = label;
    }

    public String getTool() {
        return tool;
    }

    public String getUrl() {
        return url;
    }

    public String getLabel() {
        return label;
    }

    public String toString() {
        return String.format("\"%s\", \"%s\", \"%s\"", tool, label, url);
    }
}
