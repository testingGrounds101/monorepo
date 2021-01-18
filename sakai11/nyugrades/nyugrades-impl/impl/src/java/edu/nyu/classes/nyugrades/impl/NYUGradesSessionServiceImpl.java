package edu.nyu.classes.nyugrades.impl;

import edu.nyu.classes.nyugrades.api.DBService;
import edu.nyu.classes.nyugrades.api.NYUGradesSessionService;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Set;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.tool.api.Session;


public class NYUGradesSessionServiceImpl implements NYUGradesSessionService
{
    int SESSION_TIMEOUT_SECONDS = 600;
    private DBService db;

    public void init()
    {
        db = (DBService) ComponentManager.get("edu.nyu.classes.nyugrades.api.DBService");
    }


    private String mintSessionId()
    {
        byte[] b = new byte[32];

        try {
            SecureRandom.getInstance("SHA1PRNG").nextBytes(b);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Couldn't generate a session ID", e);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            sb.append(String.format("%02x", b[i]));
        }

        return sb.toString();
    }


    private int now()
    {
        return (int) ((new java.util.Date()).getTime() / 1000);
    }



    public void expireSessions()
    {
        db.executeUpdate("DELETE FROM nyu_t_grades_ws_session WHERE last_used < ?",
                         now() - SESSION_TIMEOUT_SECONDS);
    }


    public boolean checkSession(String sessionId)
    {
        expireSessions();

        int updateCount = db.executeUpdate("UPDATE nyu_t_grades_ws_session SET last_used = ? WHERE sessionid = ?",
                                           now(),
                                           sessionId);

        return updateCount > 0;
    }


    public String createSession(String username)
    {
        String sessionId = mintSessionId();

        db.executeUpdate("INSERT INTO nyu_t_grades_ws_session (sessionid, username, last_used) values (?, ?, ?)",
                         sessionId,
                         username,
                         now());

        return sessionId;
    }


    public void deleteSession(String sessionId)
    {
        db.executeUpdate("DELETE FROM nyu_t_grades_ws_session WHERE sessionid = ?",
                         sessionId);
    }
}
