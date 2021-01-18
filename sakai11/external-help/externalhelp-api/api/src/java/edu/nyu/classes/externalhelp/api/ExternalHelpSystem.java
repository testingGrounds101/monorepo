package edu.nyu.classes.externalhelp.api;

public interface ExternalHelpSystem {
    public boolean isActive();
    public ExternalHelp getHelp(String toolId, String userRole);
    public ExternalHelp getNews(String toolId, String userRole);
    public ExternalHelp getMainHelp();
}
