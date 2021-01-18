// Convert the fields that were previously 1:1 into their typed input equivalents.

package org.sakaiproject.profile2.conversion;

import org.sakaiproject.db.cover.SqlService;
import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfileTypedListConverter {
    private static final Logger log = LoggerFactory.getLogger(ProfileTypedListConverter.class);

    public void runConversion() {
        log.info("Running conversion to map profile properties to typed lists");

        try {
            Connection db = SqlService.borrowConnection();
            boolean oldAutoCommit = db.getAutoCommit();
            db.setAutoCommit(false);

            try {
                convertPhoneNumbers(db);
                convertSocialMedia(db);
                db.commit();
            } finally {
                db.setAutoCommit(oldAutoCommit);
                SqlService.returnConnection(db);
            }
        } catch (SQLException e) {
            log.error("Failure during typed list conversion: {}", e);
            throw new RuntimeException(e);
        }
    }

    private static final String[] PHONE_NUMBER_UPDATES_MYSQL = new String[] {
        "insert into PROFILE_TYPED_VALUES_T (USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select agent_uuid, 'phoneNumbers', 'Home', null, HOME_PHONE from SAKAI_PERSON_T where HOME_PHONE is not null AND HOME_PHONE != ''",
        "insert into PROFILE_TYPED_VALUES_T (USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select agent_uuid, 'phoneNumbers', 'Work', null, TELEPHONE_NUMBER from SAKAI_PERSON_T where TELEPHONE_NUMBER is not null AND TELEPHONE_NUMBER != ''",
        "insert into PROFILE_TYPED_VALUES_T (USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select agent_uuid, 'phoneNumbers', 'Mobile', null, MOBILE from SAKAI_PERSON_T where MOBILE is not null AND MOBILE != ''",
        "insert into PROFILE_TYPED_VALUES_T (USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select agent_uuid, 'phoneNumbers', 'Other', 'Fax', FAX_NUMBER from SAKAI_PERSON_T where FAX_NUMBER is not null AND FAX_NUMBER != ''",
    };

    private static final String[] PHONE_NUMBER_UPDATES_ORACLE = new String[] {
        "insert into PROFILE_TYPED_VALUES_T (ID, USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select PROFILE_TYPED_VALUES_S.nextval, agent_uuid, 'phoneNumbers', 'Home', null, HOME_PHONE from SAKAI_PERSON_T where HOME_PHONE is not null AND HOME_PHONE != ''",
        "insert into PROFILE_TYPED_VALUES_T (ID, USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select PROFILE_TYPED_VALUES_S.nextval, agent_uuid, 'phoneNumbers', 'Work', null, TELEPHONE_NUMBER from SAKAI_PERSON_T where TELEPHONE_NUMBER is not null AND TELEPHONE_NUMBER != ''",
        "insert into PROFILE_TYPED_VALUES_T (ID, USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select PROFILE_TYPED_VALUES_S.nextval, agent_uuid, 'phoneNumbers', 'Mobile', null, MOBILE from SAKAI_PERSON_T where MOBILE is not null AND MOBILE != ''",
        "insert into PROFILE_TYPED_VALUES_T (ID, USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select PROFILE_TYPED_VALUES_S.nextval, agent_uuid, 'phoneNumbers', 'Other', 'Fax', FAX_NUMBER from SAKAI_PERSON_T where FAX_NUMBER is not null AND FAX_NUMBER != ''",
    };

    private void convertPhoneNumbers(Connection db) throws SQLException {
        String[] updates = SqlService.getVendor().equals("oracle") ? PHONE_NUMBER_UPDATES_ORACLE : PHONE_NUMBER_UPDATES_MYSQL;

        for (String sql : updates) {
            PreparedStatement ps = db.prepareStatement(sql);
            ps.executeUpdate();
            ps.close();
        }

        PreparedStatement ps = db.prepareStatement("update SAKAI_PERSON_T set HOME_PHONE = null, TELEPHONE_NUMBER = null, MOBILE = null, FAX_NUMBER = null");
        ps.executeUpdate();
        ps.close();
    }

    private static final String[] SOCIAL_MEDIA_UPDATES_MYSQL = new String[] {
        "insert into PROFILE_TYPED_VALUES_T (USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select user_uuid, 'socialMedia', 'Facebook', null, FACEBOOK_URL from PROFILE_SOCIAL_INFO_T where FACEBOOK_URL is not null AND FACEBOOK_URL != ''",
        "insert into PROFILE_TYPED_VALUES_T (USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select user_uuid, 'socialMedia', 'LinkedIn', null, LINKEDIN_URL from PROFILE_SOCIAL_INFO_T where LINKEDIN_URL is not null AND LINKEDIN_URL != ''",
        "insert into PROFILE_TYPED_VALUES_T (USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select user_uuid, 'socialMedia', 'MySpace', null, MYSPACE_URL from PROFILE_SOCIAL_INFO_T where MYSPACE_URL is not null AND MYSPACE_URL != ''",
        "insert into PROFILE_TYPED_VALUES_T (USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select user_uuid, 'socialMedia', 'Skype', null, SKYPE_USERNAME from PROFILE_SOCIAL_INFO_T where SKYPE_USERNAME is not null AND SKYPE_USERNAME != ''",
        "insert into PROFILE_TYPED_VALUES_T (USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select user_uuid, 'socialMedia', 'Twitter', null, TWITTER_URL from PROFILE_SOCIAL_INFO_T where TWITTER_URL is not null AND TWITTER_URL != ''",
    };

    private static final String[] SOCIAL_MEDIA_UPDATES_ORACLE = new String[] {
        "insert into PROFILE_TYPED_VALUES_T (ID, USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select PROFILE_TYPED_VALUES_S.nextval, user_uuid, 'socialMedia', 'Facebook', null, FACEBOOK_URL from PROFILE_SOCIAL_INFO_T where FACEBOOK_URL is not null AND FACEBOOK_URL != ''",
        "insert into PROFILE_TYPED_VALUES_T (ID, USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select PROFILE_TYPED_VALUES_S.nextval, user_uuid, 'socialMedia', 'LinkedIn', null, LINKEDIN_URL from PROFILE_SOCIAL_INFO_T where LINKEDIN_URL is not null AND LINKEDIN_URL != ''",
        "insert into PROFILE_TYPED_VALUES_T (ID, USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select PROFILE_TYPED_VALUES_S.nextval, user_uuid, 'socialMedia', 'MySpace', null, MYSPACE_URL from PROFILE_SOCIAL_INFO_T where MYSPACE_URL is not null AND MYSPACE_URL != ''",
        "insert into PROFILE_TYPED_VALUES_T (ID, USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select PROFILE_TYPED_VALUES_S.nextval, user_uuid, 'socialMedia', 'Skype', null, SKYPE_USERNAME from PROFILE_SOCIAL_INFO_T where SKYPE_USERNAME is not null AND SKYPE_USERNAME != ''",
        "insert into PROFILE_TYPED_VALUES_T (ID, USER_UUID, VALUE_GROUP, TYPE, TYPE_QUALIFIER, VALUE) select PROFILE_TYPED_VALUES_S.nextval, user_uuid, 'socialMedia', 'Twitter', null, TWITTER_URL from PROFILE_SOCIAL_INFO_T where TWITTER_URL is not null AND TWITTER_URL != ''",
    };

    private void convertSocialMedia(Connection db) throws SQLException {
        String[] updates = SqlService.getVendor().equals("oracle") ? SOCIAL_MEDIA_UPDATES_ORACLE : SOCIAL_MEDIA_UPDATES_MYSQL;

        for (String sql : updates) {
            PreparedStatement ps = db.prepareStatement(sql);
            ps.executeUpdate();
            ps.close();
        }

        PreparedStatement ps = db.prepareStatement("update PROFILE_SOCIAL_INFO_T set FACEBOOK_URL = null, LINKEDIN_URL = null, MYSPACE_URL = null, SKYPE_USERNAME = null, TWITTER_URL = null");
        ps.executeUpdate();
        ps.close();
    }
}
