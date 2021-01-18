package edu.nyu.classes.seats;

import java.util.*;
import java.util.stream.*;

import edu.nyu.classes.seats.models.SeatGroup;
import org.sakaiproject.email.cover.EmailService;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.email.api.EmailAddress.RecipientType;
import org.sakaiproject.util.FormattedText;
import org.sakaiproject.email.api.EmailAddress;
import org.sakaiproject.email.api.EmailMessage;
import org.sakaiproject.site.api.Site;

public class Emails {

    private static Set<String> ROLES_TO_CC = new HashSet<>(Arrays.asList(new String[] { "Instructor", "Teaching Assistant", "Course Site Admin" }));

    private static EmailAddress DEFAULT_FROM = new EmailAddress("no-reply-nyuclasses@nyu.edu", "NYU Classes");

    private static List<EmailAddress> buildCCList(Site site) {
        Set<org.sakaiproject.authz.api.Member> members = site.getMembers();
        List<String> netIds = members.stream()
            .filter((m) -> ROLES_TO_CC.contains(m.getRole().getId()))
            .map((m) -> m.getUserEid())
            .collect(Collectors.toList());

        return UserDirectoryService.getUsersByEids(netIds)
            .stream()
            .filter((u) -> u.getEmail() != null)
            .map((u) -> new EmailAddress(u.getEmail()))
            .collect(Collectors.toList());
    }

    public static void sendUserAddedEmail(org.sakaiproject.user.api.User studentUser,
                                          SeatGroup group,
                                          Site site) throws Exception {
        EmailMessage msg = new EmailMessage();
        // Overriden by the email service anyway...
        msg.setFrom(DEFAULT_FROM);
        msg.setSubject(String.format("You've been added to a cohort for %s",
                                     site.getTitle()));

        String body = String.format("<p>Dear %s,</p>" +
                                    "<p>You've been added to %s for %s. Please contact your instructor for information on when you will be meeting in-person for your course.</p>" +
                                    "<p>Note: you will be required to record your seating assignment for the duration of the semester in the Seating Assignments tool in NYU Classes. " +
                                    "For more information, see the <a href=\"%s\">Seating Assignments knowledgebase article</a>.</p>",

                                    studentUser.getDisplayName(),
                                    group.name,
                                    site.getTitle(),
                                    "https://www.nyu.edu/servicelink/KB0018304"
                                    );

        msg.setBody(FormattedText.escapeHtmlFormattedText(body));
        msg.setContentType("text/html");
        msg.setCharacterSet("utf-8");
        msg.addHeader("Content-Transfer-Encoding", "quoted-printable");

        msg.setRecipients(RecipientType.TO, Arrays.asList(new EmailAddress(studentUser.getEmail())));
        msg.setRecipients(RecipientType.CC, buildCCList(site));

        EmailService.getInstance().send(msg);
    }

    public static void sendPlaintextEmail(List<org.sakaiproject.user.api.User> recipients,
                                          Site site,
                                          String subject,
                                          String plaintextBody) throws Exception {
        EmailMessage msg = new EmailMessage();
        // Overriden by the email service anyway...
        msg.setFrom(DEFAULT_FROM);
        msg.setSubject(subject);

        msg.setBody(plaintextBody);

        msg.setRecipients(RecipientType.BCC, recipients.stream().map((u) -> new EmailAddress(u.getEmail())).collect(Collectors.toList()));
        msg.setRecipients(RecipientType.CC, buildCCList(site));

        EmailService.getInstance().send(msg);

    }

}
