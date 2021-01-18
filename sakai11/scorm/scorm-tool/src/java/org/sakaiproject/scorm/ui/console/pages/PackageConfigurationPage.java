/**********************************************************************************
 * $URL:  $
 * $Id:  $
 ***********************************************************************************
 *
 * Copyright (c) 2007 The Sakai Foundation.
 * 
 * Licensed under the Educational Community License, Version 1.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 *      http://www.opensource.org/licenses/ecl1.php
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 **********************************************************************************/
package org.sakaiproject.scorm.ui.console.pages;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.adl.validator.contentpackage.LaunchData;
import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.ResourceReference;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.extensions.yui.calendar.DateTimeField;
import org.apache.wicket.feedback.FeedbackMessage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.sakaiproject.scorm.dao.api.ContentPackageManifestDao;
import org.sakaiproject.scorm.model.api.ContentPackage;
import org.sakaiproject.scorm.model.api.ContentPackageManifest;
import org.sakaiproject.scorm.service.api.LearningManagementSystem;
import org.sakaiproject.scorm.service.api.ScormApplicationService;
import org.sakaiproject.scorm.service.api.ScormContentService;
import org.sakaiproject.service.gradebook.shared.GradebookExternalAssessmentService;
import org.sakaiproject.tool.api.Placement;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.wicket.markup.html.form.CancelButton;
import org.sakaiproject.wicket.model.DecoratedPropertyModel;
import org.sakaiproject.wicket.model.SimpleDateFormatPropertyModel;

public class PackageConfigurationPage extends ConsoleBasePage {

	static class AssessmentSetup implements Serializable{

		private static final long serialVersionUID = 1L;

		LaunchData launchData;

		Double numberOffPoints = 100d;

		boolean synchronizeSCOWithGradebook;
		
		public AssessmentSetup() {
			super();
		}

		public AssessmentSetup(LaunchData launchData) {
			super();
			this.launchData = launchData;
		}

		public String getItemIdentifier() {
			return launchData.getItemIdentifier();
		}
		public String getItemTitle() {
			return launchData.getItemTitle();
		}

		public LaunchData getLaunchData() {
			return launchData;
		}

		public Double getNumberOffPoints() {
			return numberOffPoints;
		}

		public boolean issynchronizeSCOWithGradebook() {
			return synchronizeSCOWithGradebook;
		}

		public void setLaunchData(LaunchData launchData) {
			this.launchData = launchData;
		}

		public void setNumberOffPoints(Double numberOffPoints) {
			this.numberOffPoints = numberOffPoints;
		}

		public void setsynchronizeSCOWithGradebook(boolean synchronizeSCOWithGradebook) {
			this.synchronizeSCOWithGradebook = synchronizeSCOWithGradebook;
		}

	}

	public class DisplayNamePropertyModel extends DecoratedPropertyModel implements Serializable{

		private static final long serialVersionUID = 1L;

		public DisplayNamePropertyModel(Object modelObject, String expression) {
			super(modelObject, expression);
		}

		@Override
		public Object convertObject(Object object) {
			String userId = String.valueOf(object);

			return lms.getLearnerName(userId);
		}

	}

	static class GradebookSetup implements Serializable{
		private static final long serialVersionUID = 1L;
		
		boolean isGradebookDefined;
		
		ContentPackage contentPackage;
		
		public ContentPackage getContentPackage() {
			return contentPackage;
		}
		public void setContentPackage(ContentPackage contentPackage) {
			this.contentPackage = contentPackage;
		}

		ContentPackageManifest contentPackageManifest;

		List<AssessmentSetup> assessments = new ArrayList<PackageConfigurationPage.AssessmentSetup>();

		public List<AssessmentSetup> getAssessments() {
			return assessments;
		}
		public String getContentPackageId() {
			return "" + contentPackage.getContentPackageId();
		}

		public ContentPackageManifest getContentPackageManifest() {
			return contentPackageManifest;
		}

		public boolean isGradebookDefined() {
			return isGradebookDefined;
		}

		public void setContentPackageManifest(ContentPackageManifest contentPackageManifest) {
			this.contentPackageManifest = contentPackageManifest;
			assessments.clear();
			@SuppressWarnings("unchecked")
			List<LaunchData> launchDatas = contentPackageManifest.getLaunchData();
			for (LaunchData launchData : launchDatas) {
				String scormType = launchData.getSCORMType();
				if ("sco".equalsIgnoreCase(scormType)) {
					AssessmentSetup assessment = buildAssessmentSetup(launchData);
					assessments.add(assessment);
				}
			}
		}
		protected AssessmentSetup buildAssessmentSetup(LaunchData launchData) {
			AssessmentSetup assessment = new AssessmentSetup(launchData);
			return assessment;
		}

		public void setGradebookDefined(boolean isGradebookDefined) {
			this.isGradebookDefined = isGradebookDefined;
		}
	}

	public class TryChoiceRenderer extends ChoiceRenderer implements Serializable{
		private static final long serialVersionUID = 1L;

		public TryChoiceRenderer() {
			super();
		}

		@Override
		public Object getDisplayValue(Object object) {
			Integer n = (Integer) object;

			if (n.intValue() == -1)
				return unlimitedMessage;

			return object;
		}

	}
	
	public class GradeStoreChoiceRenderer extends ChoiceRenderer implements Serializable{
		@Override
		public Object getDisplayValue(Object object) {
			Integer n = (Integer) object;
			return gradeStoreMessage[n];
		}
	}

	private static final long serialVersionUID = 1L;
	private static ResourceReference PAGE_ICON = new ResourceReference(PackageConfigurationPage.class, "res/table_edit.png");
	@SpringBean
	LearningManagementSystem lms;

	@SpringBean(name="org.sakaiproject.scorm.service.api.ScormContentService")
	ScormContentService contentService;
	
	@SpringBean(name = "org.sakaiproject.service.gradebook.GradebookExternalAssessmentService")
	GradebookExternalAssessmentService gradebookExternalAssessmentService;

	@SpringBean(name = "org.sakaiproject.scorm.dao.api.ContentPackageManifestDao")
	ContentPackageManifestDao contentPackageManifestDao;
	
	@SpringBean(name = "org.sakaiproject.scorm.service.api.ScormApplicationService")
	ScormApplicationService scormApplicationService;
	
	private String unlimitedMessage;
	
	private Integer origScoreGradeOption = null;
	private String[] gradeStoreMessage;

	public PackageConfigurationPage(PageParameters params) {
	    super(params);
		long contentPackageId = params.getLong("contentPackageId");

		final ContentPackage contentPackage = contentService.getContentPackage(contentPackageId);
		final GradebookSetup gradebookSetup = getAssessmentSetup(contentPackage);

		final Class pageSubmit;
        final Class pageCancel;
        
        origScoreGradeOption = contentPackage.getStoreGradeOption();

        // @NOTE this is a hack that allows us to change the destination we
        // are redirected to after form submission depending on where we come from
        // I'm sure there's a more reliable way to do this is Wicket but it's not trivial to figure it out.
        if((null != params) && (params.getBoolean("no-toolbar"))) {
            pageSubmit = DisplayDesignatedPackage.class;
            pageCancel = DisplayDesignatedPackage.class;
        } else {
            pageSubmit = PackageListPage.class;
            pageCancel = PackageListPage.class;
        }
		
		Form form = new Form("configurationForm") {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {
				//check that dates are in the correct order (e.g. due date not before open date, etc)
				if(contentPackage.getDueOn() != null && contentPackage.getDueOn().before(contentPackage.getReleaseOn())){
					//error, due date can't be before open date
					warn(new StringResourceModel("dueDateBeforeOpenDate", null).getObject());
					return;
				}
				//check acceptUntil date
				if(contentPackage.getAcceptUntil() != null){
					if(contentPackage.getDueOn() != null && contentPackage.getAcceptUntil().before(contentPackage.getDueOn())){
						//error, accept until date can't be before due date
						warn(new StringResourceModel("acceptUntilDateBeforeDueDate", null).getObject());
						return;	
					}
					if(contentPackage.getAcceptUntil().before(contentPackage.getReleaseOn())){
						//error, accept until date can't be before open date
						warn(new StringResourceModel("acceptUntilDateBeforeOpenDate", null).getObject());
						return;	
					}
				}
				
				if (gradebookSetup.isGradebookDefined()) {
					List<AssessmentSetup> assessments = gradebookSetup.getAssessments();
					int i = 1;
					for (AssessmentSetup assessmentSetup : assessments) {
						boolean on = assessmentSetup.issynchronizeSCOWithGradebook();
						String assessmentExternalId = getAssessmentExternalId(gradebookSetup, assessmentSetup);
						String context = getContext();
						boolean has = gradebookExternalAssessmentService.isExternalAssignmentDefined(context, assessmentExternalId);
						//if there is only one assessment, then save the name as the content package name
						String fixedTitle = assessments.size() > 1 ? contentPackage.getTitle() + ": " + new StringResourceModel("assessment", null).getObject() + " #" + i : contentPackage.getTitle();
						if (has && on) { 
							gradebookExternalAssessmentService.updateExternalAssessment(context, assessmentExternalId, null, fixedTitle, assessmentSetup.numberOffPoints, gradebookSetup.getContentPackage().getDueOn());
							if(!(origScoreGradeOption == null && contentPackage.getStoreGradeOption() == null)
								&& ((origScoreGradeOption != null && contentPackage.getStoreGradeOption() == null)
									|| (origScoreGradeOption == null && contentPackage.getStoreGradeOption() != null)
									|| !origScoreGradeOption.equals(contentPackage.getStoreGradeOption()))){
								//score grade option changed, update scores:
								scormApplicationService.syncAllGradesWithGradebook(context, assessmentExternalId, contentPackage);
							}
							} else if (!has && on) { 
								gradebookExternalAssessmentService.addExternalAssessment(context, assessmentExternalId, null, fixedTitle, assessmentSetup.numberOffPoints, gradebookSetup.getContentPackage().getDueOn(), "SCORM player");
								//include any existing scores to the gradebook:
								scormApplicationService.syncAllGradesWithGradebook(context, assessmentExternalId, contentPackage);
							} else if (has && !on) { 
								gradebookExternalAssessmentService.removeExternalAssessment(context, assessmentExternalId);
							}
							i++;
					}
				}
				contentService.updateContentPackage(contentPackage);
				setResponsePage(pageSubmit);
			}

			protected String getItemTitle(AssessmentSetup assessmentSetup, String context) {
				String fixedTitle = assessmentSetup.getItemTitle();
				int count = 1;
				while (gradebookExternalAssessmentService.isAssignmentDefined(context, fixedTitle)) {
					fixedTitle = assessmentSetup.getItemTitle() + " (" + count++ + ")";
				}
				return fixedTitle;
			}
		};

		List<Integer> tryList = new LinkedList<Integer>();

		tryList.add(Integer.valueOf(-1));
		for (int i = 1; i <= 10; i++) {
			tryList.add(Integer.valueOf(i));
		}

		this.unlimitedMessage = getLocalizer().getString("unlimited", this);

		TextField nameField = new TextField("packageName", new PropertyModel(contentPackage, "title"));
		nameField.setRequired(true);
		form.add(nameField);
		DateTimeField releaseOnDTF = new DateTimeField("releaseOnDTF", new PropertyModel(contentPackage, "releaseOn"));
		releaseOnDTF.setRequired(true);
		form.add(releaseOnDTF);
		DateTimeField dueOnDTF = new DateTimeField("dueOnDTF", new PropertyModel(contentPackage, "dueOn"));
		dueOnDTF.setRequired(true);
		form.add(dueOnDTF);
		DateTimeField acceptUntilDTF = new DateTimeField("acceptUntilDTF", new PropertyModel(contentPackage, "acceptUntil"));
		acceptUntilDTF.setRequired(true);
		form.add(acceptUntilDTF);
		form.add(new DropDownChoice("numberOfTries", new PropertyModel(contentPackage, "numberOfTries"), tryList, new TryChoiceRenderer()){
			@Override
			public boolean isVisible() {
				return false;
			}
		});
		form.add(new Label("createdBy", new DisplayNamePropertyModel(contentPackage, "createdBy")));
		form.add(new Label("createdOn", new SimpleDateFormatPropertyModel(contentPackage, "createdOn")));
		form.add(new Label("modifiedBy", new DisplayNamePropertyModel(contentPackage, "modifiedBy")));
		form.add(new Label("modifiedOn", new SimpleDateFormatPropertyModel(contentPackage, "modifiedOn")));

		ListView scos;
		final List<Integer> recordGradeOptions = Arrays.asList(ContentPackage.STORE_MOST_RECENT_GRADE, ContentPackage.STORE_HIGHEST_GRADE);
		gradeStoreMessage = new String[2];
		gradeStoreMessage[0] = getLocalizer().getString("storeGradeOption." + ContentPackage.STORE_MOST_RECENT_GRADE, this);
		gradeStoreMessage[1] = getLocalizer().getString("storeGradeOption." + ContentPackage.STORE_HIGHEST_GRADE, this);
		form.add(scos = new ListView("scos", gradebookSetup.getAssessments()) {

			/**
			 * serialVersionUID
			 */
			private static final long serialVersionUID = 965550162166385688L;

			@Override
			protected void populateItem(final ListItem item) {
				Label label = new Label("itemTitle", new PropertyModel(item.getModelObject(), "itemTitle"));
				item.add(label);
//				TextField numberOffPoints = new TextField("numberOffPoints", new PropertyModel(item.getModelObject(), "numberOffPoints"));
//				item.add(numberOffPoints);
				//add drop down choice options:
				final DropDownChoice gradeStoreChoice=new DropDownChoice("storeGradeOption", new PropertyModel(contentPackage, "storeGradeOption"), recordGradeOptions, new GradeStoreChoiceRenderer()){
					@Override
					public boolean isEnabled() {
						return ((AssessmentSetup) item.getModelObject()).issynchronizeSCOWithGradebook();
					}
				};
				gradeStoreChoice.setOutputMarkupPlaceholderTag(true);
				item.add(gradeStoreChoice);
				CheckBox synchronizeSCOWithGradebook = new CheckBox("synchronizeSCOWithGradebook", new PropertyModel(item.getModelObject(), "synchronizeSCOWithGradebook"));
				synchronizeSCOWithGradebook.add(new AjaxFormComponentUpdatingBehavior("onClick")
                {
                        protected void onUpdate(AjaxRequestTarget target)
                        {
                        	//repaint gradestorechoice for "isEnabled"
                        	target.addComponent(gradeStoreChoice);
                        }
                });
				item.add(synchronizeSCOWithGradebook);
			}
		});
		scos.setVisible(gradebookSetup.isGradebookDefined() && !gradebookSetup.getAssessments().isEmpty());

		form.add(new CancelButton("cancel", pageCancel));
		add(form);
		add(new FeedbackPanel("wicketFeedback"){
			@Override
            protected Component newMessageDisplayComponent(final String id, final FeedbackMessage message) {
                    final Component newMessageDisplayComponent = super.newMessageDisplayComponent(id, message);

                    if(message.getLevel() == FeedbackMessage.ERROR ||
                                    message.getLevel() == FeedbackMessage.DEBUG ||
                                    message.getLevel() == FeedbackMessage.FATAL ||
                                    message.getLevel() == FeedbackMessage.WARNING){
                            add(new SimpleAttributeModifier("class", "alertMessage"));
                    } else if(message.getLevel() == FeedbackMessage.INFO){
                            add(new SimpleAttributeModifier("class", "success"));
                    }

                    return newMessageDisplayComponent;
            }
		});
	}

	protected GradebookSetup getAssessmentSetup(ContentPackage contentPackage) {
		final GradebookSetup gradebookSetup = new GradebookSetup();
		String context = getContext();
		boolean isGradebookDefined = gradebookExternalAssessmentService.isGradebookDefined(context);
		gradebookSetup.setGradebookDefined(isGradebookDefined);
		gradebookSetup.setContentPackage(contentPackage);
		if (isGradebookDefined) {
			ContentPackageManifest contentPackageManifest = contentPackageManifestDao.load(contentPackage.getManifestId());
			gradebookSetup.setContentPackageManifest(contentPackageManifest);
			List<AssessmentSetup> assessments = gradebookSetup.getAssessments();
			for (AssessmentSetup as : assessments) {
				String assessmentExternalId = getAssessmentExternalId(gradebookSetup, as);
				boolean has = gradebookExternalAssessmentService.isExternalAssignmentDefined(getContext(), assessmentExternalId);
				as.setsynchronizeSCOWithGradebook(has);
				if (has) {
//					Assignment assignment = gradebookService.getAssignment(context, as.getItemTitle());
//					as.setNumberOffPoints(assignment.getPoints());
				}
			}
			// SeqActivityTree actTreePrototype = (SeqActivityTree)
			// contentPackageManifest.getActTreePrototype();
			// SeqActivity root = actTreePrototype.getRoot();
			// List children = root.getChildren(false);

		}
		return gradebookSetup;
	}

	protected String getContext() {
		Placement placement = ToolManager.getCurrentPlacement();
		String context = placement.getContext();
		return context;
	}

	@Override
	protected ResourceReference getPageIconReference() {
		return PAGE_ICON;
	}

	protected String getAssessmentExternalId(final GradebookSetup gradebook, AssessmentSetup assessment) {
		String assessmentExternalId = "" + gradebook.getContentPackageId() + ":" + assessment.getLaunchData().getItemIdentifier();
		return assessmentExternalId;
	}

}
