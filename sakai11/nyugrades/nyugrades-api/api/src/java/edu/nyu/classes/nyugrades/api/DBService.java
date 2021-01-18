package edu.nyu.classes.nyugrades.api;

import java.util.List;

public interface DBService
{
    public int executeUpdate(String sql, Object... args);
    public List<Object[]> executeQuery(String sql, Object... args);
    public boolean isOracle();
    public void writeAuditLog(GradeSet grades, String sectionEid) throws AuditLogException;
}
