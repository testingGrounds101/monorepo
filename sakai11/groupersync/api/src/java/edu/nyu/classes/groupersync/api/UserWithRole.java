package edu.nyu.classes.groupersync.api;

public class UserWithRole {
    private final String username;
    private final String role;

    public final static String MANAGER = "manager";
    public final static String VIEWER = "viewer";

    public UserWithRole(String username, String role) {
        this.username = username;
        this.role = normalizeRole(role);
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public String toString() {
        return String.format("#<%s [%s]>", username, role);
    }

    private String hashKey() {
        return username + "_" + role;
    }

    public int hashCode() {
        return hashKey().hashCode();
    }

    public boolean equals(Object other) {
        if (!(other instanceof UserWithRole)) {
            return false;
        }

        return ((UserWithRole) other).hashKey().equals(hashKey());
    }

    private String normalizeRole(String role) {
        if (MANAGER.equals(role) || VIEWER.equals(role)) {
            // Already fine!
            return role;
        }

        if (role == null) {
            return VIEWER;
        }

        if (role.toLowerCase().equals("instructor") || role.toLowerCase().equals("i") ||
                role.toLowerCase().equals("course site admin") || role.toLowerCase().equals("a") ||
                role.toLowerCase().equals("maintain")) {
            // startsWith here because the CM API uses "I" and Sakai uses "Instructor".
            return MANAGER;
        } else {
            return VIEWER;
        }
    }

    public boolean isMorePowerfulThan(UserWithRole other) {
        return MANAGER.equals(role) && !MANAGER.equals(other.getRole());
    }
}
