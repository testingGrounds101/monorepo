package edu.nyu.classes.providers;

import com.unboundid.ldap.sdk.migrate.ldapjdk.LDAPException;
import org.sakaiproject.unboundid.LdapUserData;
import org.sakaiproject.unboundid.UnboundidDirectoryProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.db.cover.SqlService;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserFactory;
import org.sakaiproject.user.api.User;
import org.sakaiproject.component.cover.ServerConfigurationService;

public class NYUDirectoryProvider extends UnboundidDirectoryProvider
{
    // In test mode, we'll always hit LDAP for user information and log warnings
    // if we get any sort of mismatch.  Intended to be used to check data
    // integrity prior to going live.  It's also VERY SLOW :)
    private boolean testMode = false;

    private static Log LOG = LogFactory.getLog(NYUDirectoryProvider.class);

    private int MAX_DB_ENTRIES = 256;
    private NYUUserTypeMapper userTypeMapper;


    public void init()
    {
        super.init();

        userTypeMapper = (NYUUserTypeMapper) ComponentManager.get(NYUUserTypeMapper.class.getName());

        if (userTypeMapper == null) {
            throw new RuntimeException("Couldn't get NYU user type mapper.  Can't initialize!");
        }

        if (ServerConfigurationService.getBoolean("nyudirectoryprovider.test-mode", false)) {
            testMode = true;
        }
    }


    @Override
    public boolean findUserByEmail(UserEdit edit, String email)
    {
        String netID = NYULdapAttributeMapper.getNetIdForEmail(email);

        if (netID != null) {
            edit.setEid(StringUtils.trimToNull(netID.toLowerCase()));
            return getUser(edit);
        }

        return super.findUserByEmail(edit, email);
    }


    @Override
    public Collection findUsersByEmail(String email, UserFactory factory)
    {
        String netID = NYULdapAttributeMapper.getNetIdForEmail(email);

        if (netID != null) {
            UserEdit edit = factory.newUser(StringUtils.trimToNull(netID.toLowerCase()));
            if (getUser(edit)) {
                List<User> users = new ArrayList<User>();
                users.add(edit);
                return users;
            }
        }

        return super.findUsersByEmail(email, factory);
    }


    @Override
    public boolean getUser(UserEdit edit) {
        if (edit.getEid() == null || "null".equals(edit.getEid())) {
            // This seems to happen when users aren't logged in, or when they
            // log out.  We'll never have a NetID of "null", so save ourselves
            // the lookup.
            return false;
        }

        Collection<UserEdit> arg = new ArrayList<UserEdit>(1);
        arg.add(edit);

        return getUsersInternal(arg);
    }


    @Override
    public void getUsers(Collection<UserEdit> users) {
        this.getUsersInternal(users);
    }


    public boolean getUsersInternal(Collection<UserEdit> usersCollection) {
        long startTime = System.currentTimeMillis();
        long usersSize = usersCollection.size();

        int missedUserCount = 0;
        List<UserEdit> users = coerceToList(usersCollection);
        Map<String, UserEdit> usersByEid = groupUsersByEid(usersCollection);

        Connection connection = null;

        try {
            connection = SqlService.borrowConnection();

            for (int offset = 0; offset < users.size(); offset += MAX_DB_ENTRIES) {
                int upper = Math.min(users.size(), offset + MAX_DB_ENTRIES);

                List<UserEdit> page = users.subList(offset, upper);

                fetchPageOfUsers(connection, page, usersByEid);
            }
        } catch (SQLException e) {
            LOG.error("Failure while fetching users from NYU Directory", e);
        } finally {
            SqlService.returnConnection(connection);
        }

        LOG.info(String.format("[%s] Time taken for first attempt lookup of %d users (total: %d)",
                Thread.currentThread().toString(),
                usersSize,
                (System.currentTimeMillis() - startTime),
                missedUserCount));


        // If we failed to fetch any of our users from the database, they'll be
        // left behind in our usersByEid map.  Fall back to trying LDAP.
        Collection<UserEdit> missedUsers = usersByEid.values();
        for (UserEdit missedUser : missedUsers) {
            LOG.warn("User could not be found in NYU_T_USERS: " + missedUser.getEid() + ".  Falling back to LDAP for this user");

            if (isSensibleEid(missedUser.getEid()) && super.getUser(missedUser)) {
                LOG.warn("Found user " + missedUser.getEid() + " in LDAP");
            } else {
                LOG.warn("Could not find user " + missedUser.getEid() + " in LDAP");
                missedUserCount++;

                // NOTE: We suspect this actually doesn't work because the
                // equals implementation on UserEdit fails to return true on
                // any object whose ID isn't set.  This means objects aren't
                // even equal to themselves in some cases.  Wat?
                usersCollection.remove(missedUser);
            }
        }

        LOG.info(String.format("[%s] Looked up %d users (total: %d).  Missed users: %d",
                Thread.currentThread().toString(),
                usersSize,
                (System.currentTimeMillis() - startTime),
                missedUserCount));


        if (testMode) {
            validateResult(usersCollection);
        }


        return missedUserCount == 0;
    }


    // An optimization for commonly incorrect EIDs that aren't worth bugging LDAP for
    private static final int UUID_LENGTH = 36;

    private boolean isSensibleEid(String eid) {
        if (eid == null) {
            return false;
        } else if (eid.indexOf("@") >= 0) {
            return false;
        } else if ((eid.length() == UUID_LENGTH) && eid.indexOf("-") >= 0) {
            return false;
        }

        return true;
    }

    // Query the database for the users in `pageOfUsers` and load their information into the UserEdit objects.
    private void fetchPageOfUsers(Connection connection, List<UserEdit> pageOfUsers, Map<String, UserEdit> usersByEid) throws SQLException {
        if (pageOfUsers.isEmpty()) {
            return;
        }

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            if (pageOfUsers.size() > 1) {
                ps = connection.prepareStatement("SELECT netid, lname, mname, fname, email FROM nyu_t_users where netid in (" + placeholdersFor(pageOfUsers) + ")");
            } else {
                ps = connection.prepareStatement("SELECT netid, lname, mname, fname, email FROM nyu_t_users where netid = ?");
            }

            for (int i = 0; i < pageOfUsers.size(); i++) {
                UserEdit user = pageOfUsers.get(i);
                ps.setString(i + 1, user.getEid());
            }

            rs = ps.executeQuery();

            while (rs.next()) {
                String eid = rs.getString("netid");

                if (eid == null) {
                    LOG.warn("Eid should not have been NULL for user!");
                }

                UserEdit user = usersByEid.get(eid.toLowerCase());

                if (user == null) {
                    LOG.warn("Unexpected null while looking up user: " + eid);
                    if (usersByEid.containsKey(eid.toLowerCase())) {
                        LOG.warn("Key was present but entry was null: " + eid);
                    } else {
                        LOG.warn("Key was absent: " + eid);
                    }

                    continue;
                }

                user.setEmail(rs.getString("email"));
                user.setFirstName(formatFirstName(rs.getString("fname"), rs.getString("mname")));
                user.setLastName(rs.getString("lname"));

                user.setType(userTypeMapper.getTypeForNetId(eid));

                usersByEid.remove(eid);
            }

        } finally {
            if (rs != null) { rs.close(); }
            if (ps != null) { ps.close(); }
        }
    }


    private String formatFirstName(String firstName, String middleNameOrNull) {
        if (middleNameOrNull == null || "".equals(middleNameOrNull.trim())) {
            return firstName;
        } else {
            return firstName + " " + middleNameOrNull.trim();
        }
    }


    private <E> String placeholdersFor(List<E> list) {
        StringBuilder placeholders = new StringBuilder();
        for (E elt : list) {
            if (placeholders.length() > 0) {
                placeholders.append(", ");
            }

            placeholders.append("?");
        }

        return placeholders.toString();
    }


    private Map<String, UserEdit> groupUsersByEid(Collection<UserEdit> users) {
        Map<String, UserEdit> result = new HashMap<String, UserEdit>(users.size());

        for (UserEdit user : users) {
            result.put(user.getEid().toLowerCase(), user);
        }

        return result;
    }


    private <E> List<E> coerceToList(Collection<E> collection) {
        if (collection instanceof List) {
            return (List<E>) collection;
        } else {
            return new ArrayList<E>(collection);
        }
    }


    private void shouldMatch(String name, Object val1, Object val2) {
        if (!Objects.equals(val1, val2)) {
            LOG.error("CHECK FAILED: USER PROPERTY MISMATCH FOR PROPERTY: " + name);
            LOG.error(Objects.toString(val1) + " did not match " + Objects.toString(val2));
        }
    }

    private void validateResult(Collection<UserEdit> resultFromDirectory) {
        // Find our users in LDAP too and make sure we're matching.

        for (UserEdit user : resultFromDirectory) {
            // Create our dummy version to hold the values we care about.  We
            // need to reuse the existing UserEdit for the LDAP because we can't
            // construct our own.
            DummyUserEdit fromDirectory = new DummyUserEdit(user);
            UserEdit fromLDAP = user;

            String eid = fromDirectory.getEid();
            if (super.getUser(fromLDAP)) {
                shouldMatch("(" + eid + ") " + "EID", fromDirectory.getEid(), fromLDAP.getEid());
                shouldMatch("(" + eid + ") " + "First name", fromDirectory.getFirstName(), fromLDAP.getFirstName());
                shouldMatch("(" + eid + ") " + "Last name", fromDirectory.getLastName(), fromLDAP.getLastName());
                shouldMatch("(" + eid + ") " + "Email", fromDirectory.getEmail(), fromLDAP.getEmail());
                shouldMatch("(" + eid + ") " + "Type", fromDirectory.getType(), fromLDAP.getType());
            } else {
                LOG.error("CHECK FAILED: Found user in directory but not LDAP: " + fromDirectory.getEid());
            }
        }
    }


    private class DummyUserEdit {

        private String firstName;
        private String lastName;
        private String eid;
        private String email;
        private String type;

        public DummyUserEdit(UserEdit user) {
            firstName = user.getFirstName();
            lastName = user.getLastName();
            eid = user.getEid();
            email = user.getEmail();
            type = user.getType();
        }

        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getEid() { return eid; }
        public String getEmail() { return email; }
        public String getType() { return type; }

    }

}
