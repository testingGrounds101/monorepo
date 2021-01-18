package edu.nyu.classes.nyugrades.api;

public class AuditLogException extends Exception
{
    public AuditLogException(String message) {
        super(message);
    }

    public AuditLogException(String message, Throwable cause) {
        super(message, cause);
    }
}
