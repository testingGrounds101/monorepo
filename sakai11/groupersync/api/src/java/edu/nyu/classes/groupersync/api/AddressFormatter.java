package edu.nyu.classes.groupersync.api;

public class AddressFormatter {

    public static String format(String grouperId) {
        return grouperId.replace(":", "-") + "@nyu.edu";
    }

    public static String formatUrl(String grouperId) {
        return "https://groups.google.com/a/nyu.edu/forum/#!forum/" + grouperId.replace(":", "-");
    }

    public static String formatTerm(String termEid) {
        int length = termEid.length();

        return (termEid.substring(0, 2) + termEid.substring(length - 2, length)).toUpperCase();
    }
}
