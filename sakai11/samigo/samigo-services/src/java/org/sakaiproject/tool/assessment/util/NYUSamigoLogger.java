package org.sakaiproject.tool.assessment.util;

import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.tool.assessment.data.dao.grading.ItemGradingData;
import org.sakaiproject.tool.assessment.data.ifc.assessment.AnswerIfc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import org.sakaiproject.tool.assessment.data.dao.assessment.PublishedAnswer;

public class NYUSamigoLogger {

    public static void logGetAnswerScore(ItemGradingData data, AnswerIfc answer, Map publishedAnswerHash) {
        if (!"true".equals(HotReloadConfigurationService.getString("nyu.samigo.loggger.getanswerscore", "false"))) {
            return;
        }

        try {
            // get back what we think is the answer.isCorrect
            SqlService sqlService = (SqlService) org.sakaiproject.component.cover.ComponentManager.get("org.sakaiproject.db.api.SqlService"); 
            Connection db = sqlService.borrowConnection();

            Boolean isCorrectFromDB = null;

            try {
                PreparedStatement ps = db.prepareStatement("select iscorrect " +
                        "from SAM_PUBLISHEDANSWER_T " +
                        "where answerid = ?");
                ps.setLong(1, answer.getId());

                ResultSet rs = ps.executeQuery();
                try {
                    if (rs.next()) {
                        isCorrectFromDB = Boolean.valueOf(rs.getInt(1) == 1); 
                    }
                } finally {
                    rs.close();
                }
            } finally {
                sqlService.returnConnection(db);
            }

            if (isCorrectFromDB == null) {
                // No answer entry
            } else {
                if (answer.getIsCorrect().booleanValue() == isCorrectFromDB.booleanValue()) {
                    // OK!
                } else {
                    logSomethingUseful(data, answer, publishedAnswerHash);
                }
            }
        } catch (Exception e) {
            System.err.println("Exception from NYUSamigoLogger: " + e);
            e.printStackTrace();
        }
    }

    private static void logSomethingUseful(ItemGradingData data, AnswerIfc answer, Map publishedAnswerHash) {
        StringBuilder sb = new StringBuilder();

        sb.append("ItemGradingData.assessmentGradingId: " + data.getAssessmentGradingId() + "\n");
        sb.append("ItemGradingData.agentId: " + data.getAgentId() + "\n");
        sb.append("ItemGradingData.publishedItemId: " + data.getPublishedItemId() + "\n");
        sb.append("ItemGradingData.publishedAnswerId: " + data.getPublishedAnswerId() + "\n");
        sb.append("ItemGradingData.autoScore: " + data.getAutoScore() + "\n");
        sb.append("\n");

        sb.append("Answer.id: " + answer.getId() + "\n");
        sb.append("Answer.sequence: " + answer.getSequence() + "\n");
        sb.append("Answer.isCorrect: " + answer.getIsCorrect() + "\n");
        sb.append("Answer.score: " + answer.getScore() + "\n");
        sb.append("Answer.discount: " + answer.getDiscount() + "\n");
        sb.append("Answer.partialCredit: " + answer.getPartialCredit() + "\n");
        sb.append("\n");

        for (Object entryObject : publishedAnswerHash.entrySet()) {
            Map.Entry<Object,Object> entry = (Map.Entry<Object,Object>)entryObject;
            String key = String.format("PublishedAnswer_%s", entry.getKey().toString());
            PublishedAnswer pa = (PublishedAnswer) entry.getValue();

            sb.append(key + ":\n");

            sb.append("  " + key + ".id: " + pa.getId() + "\n");
            sb.append("  " + key + ".sequence: " + pa.getSequence() + "\n");
            sb.append("  " + key + ".score: " + pa.getScore() + "\n");
            sb.append("  " + key + ".discount: " + pa.getDiscount() + "\n");
            sb.append("  " + key + ".partialCredit: " + pa.getPartialCredit() + "\n");
            sb.append("  " + key + ".isCorrect: " + pa.getIsCorrect() + "\n");
            sb.append("\n");
        }

        System.err.println("WEIRDNESS HAS OCCURRED!\n" + sb.toString());
    }
}
