package org.sakaiproject.attendance.tool.actions;

import org.apache.wicket.ajax.AjaxRequestTarget;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.sakaiproject.attendance.logic.AttendanceLogic;
import org.sakaiproject.attendance.logic.SakaiProxy;
import org.sakaiproject.attendance.model.AttendanceRecord;
import org.sakaiproject.attendance.model.Status;
import org.sakaiproject.attendance.tool.pages.BasePage;
import org.sakaiproject.attendance.tool.pages.EventView;
import org.sakaiproject.attendance.tool.panels.StatisticsPanel;

import java.io.Serializable;

public class SetAttendanceStatusAction extends InjectableAction implements Action, Serializable {

    private static final long serialVersionUID = 1L;

    @SpringBean(name="org.sakaiproject.attendance.logic.SakaiProxy")
    protected SakaiProxy sakaiProxy;

    @SpringBean(name="org.sakaiproject.attendance.logic.AttendanceLogic")
    protected AttendanceLogic attendanceLogic;

    public SetAttendanceStatusAction() {
    }

    @Override
    public ActionResponse handleEvent(JsonNode params, AjaxRequestTarget target) {
        Long recordId = params.get("recordid").asLong();

        AttendanceRecord attendanceRecord = attendanceLogic.getAttendanceRecord(recordId);

        Status status = Status.valueOf(params.get("status").textValue());

        Status oldStatus = attendanceRecord.getStatus();
        attendanceRecord.setStatus(status);

        // FIXME handle when site isSyncing?

        boolean result = attendanceLogic.updateAttendanceRecord(attendanceRecord, oldStatus);

        String[] resultMsgVars = new String[]{
            sakaiProxy.getUserSortName(attendanceRecord.getUserID()),
            attendanceRecord.getAttendanceEvent().getName(),
            getStatusString(attendanceRecord.getStatus())
        };

        StringResourceModel temp;
        if(result){
         temp = new StringResourceModel("attendance.record.save.success", null, resultMsgVars);
            target.getPage().getSession().info(temp.getString());
        } else {
         temp = new StringResourceModel("attendance.record.save.failure", null, resultMsgVars);
            target.getPage().getSession().error(temp.getString());
        }

        target.add(((BasePage)target.getPage()).getFeedbackPanel());
        target.addChildren(target.getPage(), StatisticsPanel.class);

        return new EmptyOkResponse();
    }


    private String getStatusString(Status s) {
        if(s == null) {
            return new ResourceModel("attendance.status.unknown").getObject();
        }
        switch (s)
        {
            case UNKNOWN: return new ResourceModel("attendance.status.unknown").getObject();
            case PRESENT: return new ResourceModel("attendance.status.present").getObject();
            case EXCUSED_ABSENCE: return new ResourceModel("attendance.status.excused").getObject();
            case UNEXCUSED_ABSENCE: return new ResourceModel("attendance.status.absent").getObject();
            case LATE: return new ResourceModel("attendance.status.late").getObject();
            case LEFT_EARLY: return new ResourceModel("attendance.status.left.early").getObject();
            default: return new ResourceModel("attendance.status.unknown").getObject();
        }
    }
}
