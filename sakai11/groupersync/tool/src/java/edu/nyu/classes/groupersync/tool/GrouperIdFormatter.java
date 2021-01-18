package edu.nyu.classes.groupersync.tool;

public class GrouperIdFormatter {

    public String format(String groupId) {
        String[] bits = groupId.split(":");

        return "app:classes:" + bits[1] + ":" + bits[2] + ":" + bits[0];
    }

}
