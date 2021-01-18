package edu.nyu.classes.nyugrades.api;

public interface NYUGradesSessionService
{
    public void expireSessions();
    public boolean checkSession(String sessionId);
    public String createSession(String username);
    public void deleteSession(String sessionId);
}
