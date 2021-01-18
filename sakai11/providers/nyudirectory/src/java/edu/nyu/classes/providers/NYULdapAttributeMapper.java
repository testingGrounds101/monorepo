/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006, 2007, 2008 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package edu.nyu.classes.providers;

import com.unboundid.ldap.sdk.migrate.ldapjdk.LDAPEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.component.cover.ServerConfigurationService;

import org.sakaiproject.unboundid.SimpleLdapAttributeMapper;
import org.sakaiproject.unboundid.LdapUserData;

import org.sakaiproject.unboundid.AttributeMappingConstants;

public class NYULdapAttributeMapper extends SimpleLdapAttributeMapper
{
    private static Log M_log = LogFactory.getLog(NYULdapAttributeMapper.class);


    private static String likeClause(String whereValue) {
        return whereValue.toLowerCase() + "%";
    }


    private static List<String> lookupUsersTable(String selectColumn, String whereColumn, String whereValue, boolean useLike) {

        List<String> result = new ArrayList<String>();

        try {
            SqlService sqlService = null;
            try {
                sqlService = (SqlService) org.sakaiproject.component.cover.ComponentManager.get("org.sakaiproject.db.api.SqlService");
            } catch (Throwable e) {
                M_log.warn("lookupUsersTable: can't get SQL service: " + e);
            }

            if (sqlService == null) {
                return result;
            }

            Connection db = sqlService.borrowConnection();

            try {
                String operator = useLike ? "LIKE" : "=";
                String value = useLike ? likeClause(whereValue) : whereValue.toLowerCase();

                PreparedStatement ps = db.prepareStatement("select " + selectColumn +
                        " from NYU_T_USERS " +
                        "where lower(" + whereColumn + ") " + operator + " ?");
                ps.setString(1, value);

                ResultSet rs = ps.executeQuery();
                try {
                    while (rs.next()) {
                        result.add(rs.getString(1));
                    }
                } finally {
                    rs.close();
                }
            } finally {
                sqlService.returnConnection(db);
            }
        } catch (SQLException e) {
            M_log.warn("lookupUsersTable: " + e);
        }

        return result;
    }



    private static String firstOrNull(List<String> coll) {
        if (coll.isEmpty()) {
            return null;
        }

        return coll.get(0);
    }


    public static String getNetIdForEmail(String email) {
        return firstOrNull(lookupUsersTable("netid", "email", email, false));
    }


    public static String getEmailForNetId(String netid) {
        return firstOrNull(lookupUsersTable("email", "netid", netid, false));
    }


    public static List<String> getMatchingNetIds(String criteria) {
        return lookupUsersTable("netid", "email", criteria, true);
    }


    public static boolean isOverrideActive() {
        return ServerConfigurationService.getBoolean("edu.nyu.classes.ldap.emailsFromDB", false);
    }


    /**
     * @inheritDoc
     */
    @Override
    public String getFindUserByCrossAttributeSearchFilter(String criteria) {
        String eidAttr = getAttributeMapping(AttributeMappingConstants.LOGIN_ATTR_MAPPING_KEY);
        String emailAttr = getAttributeMapping(AttributeMappingConstants.EMAIL_ATTR_MAPPING_KEY);
        String givenNameAttr = getAttributeMapping(AttributeMappingConstants.FIRST_NAME_ATTR_MAPPING_KEY);
        String lastNameAttr = getAttributeMapping(AttributeMappingConstants.LAST_NAME_ATTR_MAPPING_KEY);

        // Prefix searches just add noise to these.
        List<String> noPrefixFields = Arrays.asList(eidAttr, emailAttr);

        List<String> fields = Arrays.asList(eidAttr, emailAttr, givenNameAttr, lastNameAttr);
        List<String> tokens = Arrays.asList(criteria.split("[\" ,]+")).stream()
            .filter(s -> !s.isEmpty()).distinct()
            .map(s -> escapeSearchFilterTerm(s))
            .collect(Collectors.toList());

        // If any of our tokens looks like an email address, look it up and
        // include the netid on our search.
        List<String> removeEmails = new ArrayList<>();
        List<String> additionalNetIds = new ArrayList<>();
        for (String token : tokens) {
            if (token.indexOf("@") > 0) {
                String netid = getNetIdForEmail(token);

                if (netid != null) {
                    additionalNetIds.add(netid);
                    removeEmails.add(token);
                }
            }
        }

        tokens.addAll(additionalNetIds);
        tokens.removeAll(removeEmails);


        // We want to end up with:
        //
        // (field1=term1 OR field2=term1 OR field3=term1) AND
        // (field1=term2 OR field2=term2 OR field3=term2) AND
        // (field1=term3 OR field2=term3 OR field3=term3)

        List<String> subqueries = new ArrayList<>();
        for (String token : tokens) {
            // A search for `token` against all fields in our list.
            String allFieldsSubquery =
                String.format("(|%s)",
                              fields
                              .stream()
                              .map(field -> {
                                      if (noPrefixFields.contains(field)) {
                                          return String.format("(%s=%s)", field, token);
                                      } else {
                                          return String.format("(%s=%s*)", field, token);
                                      }
                                  })
                              .collect(Collectors.joining("")));

            subqueries.add(allFieldsSubquery);
        }

        // Our final search requires that every token appear *somewhere*
        return String.format("(&%s)", subqueries.stream().collect(Collectors.joining("")));
    }


    @Override
    public void mapLdapEntryOntoUserData(LDAPEntry ldapEntry, LdapUserData userData) {
        super.mapLdapEntryOntoUserData(ldapEntry, userData);

        try {
            boolean useDBOverride = ServerConfigurationService.getBoolean("edu.nyu.classes.ldap.emailsFromDB", false);

            if (isOverrideActive()) {
                String emailSuffix = ServerConfigurationService.getString("edu.nyu.classes.ldap.emailSuffix", "@nyu.edu");
                String email = getEmailForNetId(userData.getEid());

                // Override the user's email address with the value from the database
                if (email != null) {
                    userData.setEmail(email);
                }

                // If we still don't have an email address in spite of our best efforts, base it off the Net ID
                if (userData.getEmail() == null) {
                    String netid = userData.getEid();
                    if (netid != null) {
                        userData.setEmail(netid + emailSuffix);
                    }
                }
            }
        } catch (Throwable ex) {
            // If *anything* goes wrong just leave it.
        }
    }

}
