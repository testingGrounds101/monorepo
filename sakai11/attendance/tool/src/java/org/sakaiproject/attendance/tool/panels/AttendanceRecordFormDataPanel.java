/*
 *  Copyright (c) 2017, University of Dayton
 *
 *  Licensed under the Educational Community License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *              http://opensource.org/licenses/ecl2
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.sakaiproject.attendance.tool.panels;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormSubmitBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.model.*;
import org.sakaiproject.attendance.model.AttendanceRecord;
import org.sakaiproject.attendance.model.AttendanceSite;
import org.sakaiproject.attendance.model.AttendanceStatus;
import org.sakaiproject.attendance.model.Status;
import org.sakaiproject.attendance.tool.dataproviders.AttendanceStatusProvider;
import org.sakaiproject.component.cover.ServerConfigurationService;

import java.util.ArrayList;
import java.util.List;

/**
 * AttendanceRecordFormDataPanel is a panel used to display the data contained within an AttendanceRecord
 *
 * @author Leonardo Canessa [lcanessa1 (at) udayton (dot) edu]
 * @author David Bauer [dbauer1 (at) udayton (dot) edu]
 */
public class AttendanceRecordFormDataPanel extends BasePanel {
    private static final    long                        serialVersionUID = 1L;
    private                 IModel<AttendanceRecord>    recordIModel;
    private                 boolean                     restricted ;
    private                 boolean                     showCommentsToStudents;
    // private                 List<Component>             ajaxTargets = new ArrayList<Component>();
    private                 String                      returnPage;
    private                 Status                      oldStatus;

    private                 WebMarkupContainer          commentContainer;

    public AttendanceRecordFormDataPanel(String id, AttendanceSite attendanceSite, AttendanceStatusProvider attendanceStatusProvider, IModel<AttendanceRecord> aR,  String rP, FeedbackPanel fP) {
        super(id, aR);
        this.recordIModel = aR;
        this.oldStatus = aR.getObject().getStatus();
        this.showCommentsToStudents = recordIModel.getObject().getAttendanceEvent().getAttendanceSite().getShowCommentsToStudents();
        this.restricted = this.role != null && this.role.equals("Student");
        this.returnPage = rP;
        enable(fP);
        // this.ajaxTargets.add(this.pageFeedbackPanel);

        add(createRecordInputForm(attendanceSite, attendanceStatusProvider));
    }

    private WebMarkupContainer createRecordInputForm(AttendanceSite attendanceSite, AttendanceStatusProvider attendanceStatusProvider) {
        WebMarkupContainer recordForm = new WebMarkupContainer("attendanceRecord");

        createStatusRadio(recordForm, attendanceSite, attendanceStatusProvider);
        createCommentBox(recordForm);

        boolean noRecordBool = ((AttendanceRecord) this.recordIModel.getObject()).getStatus().equals(Status.UNKNOWN) && restricted;
        recordForm.setVisibilityAllowed(!noRecordBool);

        WebMarkupContainer noRecordContainer = new WebMarkupContainer("no-record");
        noRecordContainer.setVisibilityAllowed(noRecordBool);
        add(noRecordContainer);

        return recordForm;
    }

    private void createStatusRadio(final WebMarkupContainer rF, final AttendanceSite attendanceSite, AttendanceStatusProvider attendanceStatusProvider) {
        DataView<AttendanceStatus> attendanceStatusRadios = new DataView<AttendanceStatus>("status-radios", attendanceStatusProvider) {
            @Override
            protected void populateItem(Item<AttendanceStatus> item) {
                final Status itemStatus = item.getModelObject().getStatus();
                Radio statusRadio = new Radio<Status>("record-status", new Model<Status>(itemStatus));
                item.add(statusRadio);
                statusRadio.add(new AttributeModifier("data-status", itemStatus.toString()));
                item.add(new AttributeAppender("class", " " + itemStatus.toString().toLowerCase()));
                if (itemStatus.equals(AttendanceRecordFormDataPanel.this.recordIModel.getObject().getStatus())) {
                    item.add(new AttributeAppender("class", " active"));
                }
                statusRadio.setLabel(Model.of(getStatusString(itemStatus)));
                statusRadio.setEnabled(!attendanceSite.getIsSyncing());
                statusRadio.add(new AttributeModifier("data-recordid", AttendanceRecordFormDataPanel.this.recordIModel.getObject().getId()));
                item.add(new SimpleFormComponentLabel("record-status-name", statusRadio));
            }
        };

        RadioGroup group = new RadioGroup<Status>("attendance-record-status-group", new PropertyModel<Status>(this.recordIModel,"status"));
        group.setOutputMarkupPlaceholderTag(true);
        group.setRenderBodyOnly(false);
        group.add(attendanceStatusRadios);
        group.setEnabled(!this.restricted);

        rF.add(group);
    }

    private void createCommentBox(final WebMarkupContainer rF) {

        commentContainer = new WebMarkupContainer("comment-container");
        commentContainer.add(new AttributeModifier("data-recordid", this.recordIModel.getObject().getId()));
        commentContainer.setOutputMarkupId(true);

        WebMarkupContainer commentToggle = new WebMarkupContainer("commentToggle");
        commentToggle.setOutputMarkupId(true);

        boolean hasComment = recordIModel.getObject().getComment() != null && !recordIModel.getObject().getComment().equals("");

        if (hasComment) {
            commentToggle.add(new AttributeAppender("class", " has-comment"));
        }
        commentContainer.add(commentToggle);

        if(restricted) {
            commentContainer.setVisible(showCommentsToStudents);
            if (!hasComment) {
                commentToggle.setVisible(false);
            }
        }

        rF.add(commentContainer);
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        final String version = ServerConfigurationService.getString("portal.cdn.version", "");
        response.render(JavaScriptHeaderItem.forUrl(String.format("javascript/attendanceRecordForm.js?version=%s", version)));
        response.render(OnDomReadyHeaderItem.forScript("attendance.recordFormSetup();"));
    }
}
