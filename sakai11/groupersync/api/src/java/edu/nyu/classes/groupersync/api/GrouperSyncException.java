package edu.nyu.classes.groupersync.api;

public class GrouperSyncException extends Exception {

    public GrouperSyncException(String msg) {
        super(msg);
    }

    public GrouperSyncException(String msg, Throwable t) {
        super(msg, t);
    }

}
