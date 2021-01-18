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

package org.sakaiproject.attendance.tool.pages;


import org.apache.wicket.datetime.StyleDateConverter;
import org.apache.wicket.datetime.markup.html.basic.DateLabel;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.ResourceModel;
import org.sakaiproject.attendance.model.AttendanceEvent;
import org.sakaiproject.attendance.model.AttendanceRecord;
import org.sakaiproject.attendance.model.AttendanceSite;
import org.sakaiproject.attendance.tool.actions.SetAttendanceStatusAction;
import org.sakaiproject.attendance.tool.dataproviders.AttendanceRecordProvider;
import org.sakaiproject.attendance.tool.dataproviders.AttendanceStatusProvider;
import org.sakaiproject.attendance.tool.actions.ViewCommentAction;
import org.sakaiproject.attendance.tool.component.AttendanceTable;
import org.sakaiproject.attendance.tool.models.AttendanceModalWindow;
import org.sakaiproject.attendance.tool.panels.AttendanceGradePanel;
import org.sakaiproject.attendance.tool.panels.AttendanceRecordFormDataPanel;
import org.sakaiproject.attendance.tool.panels.AttendanceRecordFormHeaderPanel;
import org.sakaiproject.attendance.tool.panels.StatisticsPanel;
import java.util.Arrays;
import org.sakaiproject.time.cover.TimeService;
import java.util.TimeZone;

/**
 * StudentView is the view of a single user (a student)'s AttendanceRecords
 *
 * @author Leonardo Canessa [lcanessa1 (at) udayton (dot) edu]
 * @author David Bauer [dbauer1 (at) udayton (dot) edu]
 */
public class StudentView extends BasePage {
    private static final    long        serialVersionUID    = 1L;
    private                 String      studentId;
    private                 Long        previousEventId;
    private                 boolean     isStudent           = false;
    private                 String      returnPage          = "";

    private AttendanceModalWindow showCommentWindow;

    public StudentView() {
        this.studentId = sakaiProxy.getCurrentUserId();

        init();
    }

    public StudentView(String id, String fromPage) {
        this.studentId = id;
        this.returnPage = fromPage;

        init();
    }

    public StudentView(String id, Long eventId, String fromPage) {
        this.studentId = id;
        this.previousEventId = eventId;
        this.returnPage = fromPage;

        init();
    }

    // Make refresh behave itself.
    @Override
    public void onBeforeRender() {
        super.onBeforeRender();
        if (hasBeenRendered()) {
            setResponsePage(new StudentView(this.studentId,
                                            this.previousEventId,
                                            this.returnPage));
        }
    }

    private void init() {
        if(this.role != null && this.role.equals("Student")){
            this.isStudent = true;
            hideNavigationLink(this.homepageLink);
            hideNavigationLink(this.studentOverviewLink);
            hideNavigationLink(this.settingsLink);
            hideNavigationLink(this.gradingLink);
            hideNavigationLink(this.exportLink);
        }

        add(new Label("student-view-hidden","") {
            @Override
            public boolean isVisible() {
                return isStudent;
            }
        });

        add(createHeader());
        add(createGrade());
        add(createStatistics());
        add(createStudentViewHeader());
        add(createTable());

        showCommentWindow = new AttendanceModalWindow("showCommentWindow");
        add(showCommentWindow);
    }

    private WebMarkupContainer createHeader() {
        WebMarkupContainer header = new WebMarkupContainer("header") {
            @Override
            public boolean isVisible() {
                return !isStudent;
            }
        };
        header.setOutputMarkupPlaceholderTag(true);

        Link<Void> closeLink = new Link<Void>("close-link") {
            @Override
            public void onClick() {
                if(returnPage.equals(BasePage.STUDENT_OVERVIEW_PAGE)) {
                    setResponsePage(new StudentOverview());
                } else {
                    setResponsePage(new Overview());
                }
            }
        };

        if(returnPage.equals(BasePage.STUDENT_OVERVIEW_PAGE)){
            closeLink.add(new Label("close-link-text", new ResourceModel("attendance.event.view.link.close.student.overview")));
        } else {
            closeLink.add(new Label("close-link-text", new ResourceModel("attendance.event.view.link.close.overview")));
        }

        header.add(closeLink);

        WebMarkupContainer event = new WebMarkupContainer("event") {
            @Override
            public boolean isVisible() {
                return !returnPage.equals(BasePage.STUDENT_OVERVIEW_PAGE);
            }
        };

        Link<Void> eventLink = new Link<Void>("event-link") {
            @Override
            public void onClick() {
                setResponsePage(new EventView(previousEventId, returnPage));
            }
        };

        if(!isStudent && previousEventId != null) {
            eventLink.add(new Label("event-link-text", attendanceLogic.getAttendanceEvent(previousEventId).getName()));
        } else {
            eventLink.add(new Label("event-link-text", ""));
        }
        event.add(eventLink);


        header.add(event);

        Label studentName = new Label("student-name", sakaiProxy.getUserSortName(this.studentId) + " (" + sakaiProxy.getUserDisplayId(this.studentId) + ")");
        header.add(studentName);

        return header;
    }

    private WebMarkupContainer createGrade() {
        WebMarkupContainer grade = new WebMarkupContainer("grade") {
            @Override
            public boolean isVisible() {
                Boolean isGradeShown = attendanceLogic.getCurrentAttendanceSite().getIsGradeShown();
                return (isGradeShown == null ? false : isGradeShown) || !isStudent;
            }
        };

        AttendanceGradePanel attendanceGrade = new AttendanceGradePanel("attendance-grade", attendanceLogic.getAttendanceGrade(this.studentId), feedbackPanel) {
            @Override
            public boolean isEnabled() {
                return !isStudent;
            }
        };

        grade.add(attendanceGrade);

        return grade;
    }

    private StatisticsPanel createStatistics() {
        return new StatisticsPanel("statistics", returnPage, studentId, previousEventId);
    }

    private WebMarkupContainer createStudentViewHeader() {
        WebMarkupContainer studentView = new WebMarkupContainer("student-view") {
            @Override
            public boolean isVisible() {
                return isStudent;
            }
        };

        studentView.add(new Label("student-name", sakaiProxy.getUserSortName(this.studentId) + " (" + sakaiProxy.getUserDisplayId(this.studentId) + ")"));

        return studentView;
    }

    private WebMarkupContainer createTable(){
        WebMarkupContainer studentViewData = new WebMarkupContainer("student-view-data");

        if(!isStudent) {
            studentViewData.add(new Label("take-attendance-header", getString("attendance.student.view.take.attendance")));
        } else {
            studentViewData.add(new Label("take-attendance-header", getString("attendance.student.view.attendance")));
        }

        AttendanceTable table = new AttendanceTable("takeAttendanceTable");
        table.addEventListener("viewComment", new ViewCommentAction());

        if (!isStudent) {
            table.addEventListener("setStatus", new SetAttendanceStatusAction());
        }

        table.add(new AttendanceRecordFormHeaderPanel("header"));
        table.add(new Label("event-name-header", new ResourceModel("attendance.record.form.header.event")));
        table.add(new Label("event-date-header", new ResourceModel("attendance.record.form.header.date")));
        table.add(createData());

        studentViewData.add(table);

        return studentViewData;
    }

    private DataView<AttendanceRecord> createData(){
        final AttendanceSite attendanceSite = attendanceLogic.getCurrentAttendanceSite();

        // Ensure we don't have any missing DB rows
        for (AttendanceEvent aE : attendanceLogic.getAttendanceEventsForSite(attendanceSite)) {
            if (aE.getRecords().stream().noneMatch(record -> record.getUserID().equals(studentId))) {
                attendanceLogic.updateMissingRecordsForEvent(aE, attendanceSite.getDefaultStatus(), Arrays.asList(new String[] { studentId }));
            }
        }

        final AttendanceStatusProvider attendanceStatusProvider = new AttendanceStatusProvider(attendanceSite, AttendanceStatusProvider.ACTIVE);
        DataView<AttendanceRecord> dataView = new DataView<AttendanceRecord>("records", new AttendanceRecordProvider(this.studentId)) {
            @Override
            protected void populateItem(final Item<AttendanceRecord> item) {
                Link<Void> eventLink = new Link<Void>("event-link") {
                    private static final long serialVersionUID = 1L;
                    public void onClick() {
                        setResponsePage(new EventView(item.getModelObject().getAttendanceEvent(), returnPage));
                    }
                };
                eventLink.add(new Label("record-name", item.getModelObject().getAttendanceEvent().getName()));
                if(isStudent) {
                    disableLink(eventLink);
                }
                item.add(eventLink);
                item.add(new DateLabel("event-date", Model.of(item.getModelObject().getAttendanceEvent().getStartDateTime()), new StyleDateConverter("MM", true) {
                    private static final long serialVersionUID = 1L;
                    @Override
                    protected TimeZone getClientTimeZone() {
                        return TimeService.getLocalTimeZone();
                    }
                }));
                item.add(new AttendanceRecordFormDataPanel("record", attendanceSite, attendanceStatusProvider, item.getModel(), returnPage, feedbackPanel));
            }
        };

        return dataView;
    }

    public AttendanceModalWindow getShowCommentWindow() {
        return this.showCommentWindow;
    }
}
