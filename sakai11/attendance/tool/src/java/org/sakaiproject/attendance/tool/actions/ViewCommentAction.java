package org.sakaiproject.attendance.tool.actions;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.visit.IVisit;
import org.apache.wicket.util.visit.IVisitor;
import org.sakaiproject.attendance.logic.AttendanceLogic;
import org.sakaiproject.attendance.model.AttendanceRecord;
import org.sakaiproject.attendance.tool.models.AttendanceModalWindow;
import org.sakaiproject.attendance.tool.pages.EventView;
import org.sakaiproject.attendance.tool.pages.StudentView;
import org.sakaiproject.attendance.tool.panels.AttendanceRecordCommentPanel;

import java.io.Serializable;

public class ViewCommentAction extends InjectableAction implements Action, Serializable {

    private static final long serialVersionUID = 1L;

    @SpringBean(name="org.sakaiproject.attendance.logic.AttendanceLogic")
    protected AttendanceLogic attendanceLogic;

    public ViewCommentAction() {
    }

    @Override
    public ActionResponse handleEvent(JsonNode params, AjaxRequestTarget target) {
        Long recordId = params.get("recordid").asLong();
        String toggleComponentId = params.get("toggleid").textValue();

        AttendanceRecord attendanceRecord = attendanceLogic.getAttendanceRecord(recordId);

        AttendanceModalWindow window;
        if (target.getPage() instanceof EventView) {
            window = ((EventView) target.getPage()).getShowCommentWindow();
        } else if (target.getPage() instanceof StudentView) {
            window = ((StudentView) target.getPage()).getShowCommentWindow();
        } else {
            throw new RuntimeException("showCommentWindow must be supported on page for comments to show");
        }

        WebMarkupContainer toggle = target.getPage().visitChildren(WebMarkupContainer.class, new IVisitor<WebMarkupContainer, WebMarkupContainer>() {
            @Override
            public void component(final WebMarkupContainer component, final IVisit<WebMarkupContainer> visit) {
                if (toggleComponentId.equals(component.getMarkupId())) {
                    visit.stop(component);
                }
            }
        });

        window.setComponentToReturnFocusTo(toggle);

        Component commentPanel = new AttendanceRecordCommentPanel(window.getContentId(), Model.of(attendanceRecord), window, toggle)
            .setOutputMarkupPlaceholderTag(true)
            .setOutputMarkupId(true);

        window.setContent(commentPanel);
        window.show(target);

        return new EmptyOkResponse();
    }
}
