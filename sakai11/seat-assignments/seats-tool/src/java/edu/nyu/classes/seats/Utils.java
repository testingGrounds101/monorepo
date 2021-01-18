package edu.nyu.classes.seats;

public class Utils {
    public static String rosterToStemName(String rosterId) {
        return rosterId.replace("_", ":");
    }

    public static String stemNameToRosterId(String stemName) {
        return stemName.replace(":", "_");
    }
}
