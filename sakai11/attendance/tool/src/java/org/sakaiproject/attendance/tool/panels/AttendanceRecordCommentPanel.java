package org.sakaiproject.attendance.tool.panels;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.StringResourceModel;
import org.sakaiproject.attendance.model.AttendanceRecord;
import org.sakaiproject.attendance.tool.models.AttendanceModalWindow;
import org.sakaiproject.attendance.tool.pages.BasePage;

public class AttendanceRecordCommentPanel extends BasePanel {

    private AttendanceModalWindow modalWindow;
    private Component toggle;
    private boolean restricted;

    public AttendanceRecordCommentPanel(String id, IModel<AttendanceRecord> aR, AttendanceModalWindow modalWindow, Component toggle) {
        super(id, aR);
        this.modalWindow = modalWindow;
        this.toggle = toggle;
        this.restricted = this.role != null && this.role.equals("Student");
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();

        final AttendanceRecord attendanceRecord = (AttendanceRecord) getDefaultModelObject();

        Form<AttendanceRecord> form = new Form<AttendanceRecord>("commentForm", new CompoundPropertyModel<>(attendanceRecord)) {
            @Override
            protected void onSubmit() {
                if (AttendanceRecordCommentPanel.this.restricted) {
                    throw new RuntimeException("Not allowed");
                }

                boolean result = attendanceLogic.updateAttendanceRecord(getModelObject(), getModelObject().getStatus());
                String userName = sakaiProxy.getUserSortName(getModelObject().getUserID());

                if(result){
                    getPage().getSession().info(
                        new StringResourceModel("attendance.record.comment.save.success", null, new String[] {userName}).getString());
                } else {
                    getPage().getSession().error(
                        new StringResourceModel("attendance.record.comment.save.failure", null, new String[] {userName}).getString());
                }
            }
        };

        add(form);

        TextArea<String> commentBox = new TextArea<String>("comment", new PropertyModel<String>(attendanceRecord, "comment"));
        form.add(commentBox);

        if (this.restricted) {
            form.setEnabled(false);
            commentBox.setEnabled(false);
            modalWindow.setTitle(getString("attendance.record.form.view.comment"));
        } else {
            String userName = sakaiProxy.getUserSortName(attendanceRecord.getUserID());
            modalWindow.setTitle(new StringResourceModel("attendance.record.form.edit.comment", null, new String[] {userName}));
        }

        form.add(new AjaxSubmitLink("save-comment") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                super.onSubmit(target, form);

                BasePage page = (BasePage) target.getPage();
                target.add(page.getFeedbackPanel());

                AttendanceRecord attendanceRecord = (AttendanceRecord)form.getModelObject();

                if (attendanceRecord.getComment() == null || attendanceRecord.getComment().isEmpty()) {
                    AttendanceRecordCommentPanel.this.toggle.add(new AttributeModifier("class", "commentToggle"));
                } else {
                    AttendanceRecordCommentPanel.this.toggle.add(new AttributeModifier("class", "commentToggle has-comment"));
                }

                target.add(AttendanceRecordCommentPanel.this.toggle);

                AttendanceRecordCommentPanel.this.modalWindow.close(target);
            }
        }.setVisible(!this.restricted));

        form.add(new AjaxSubmitLink("cancel") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                AttendanceRecordCommentPanel.this.modalWindow.close(target);
            }
        }.setDefaultFormProcessing(false).setVisible(!this.restricted));

        add(new AjaxLink("close") {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                AttendanceRecordCommentPanel.this.modalWindow.close(target);
            }
        }.setVisible(this.restricted));
    }
}
