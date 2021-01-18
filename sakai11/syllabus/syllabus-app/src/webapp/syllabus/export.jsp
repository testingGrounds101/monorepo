<%@ taglib uri="http://java.sun.com/jsf/html" prefix="h" %>
<%@ taglib uri="http://java.sun.com/jsf/core" prefix="f" %>
<%@ taglib uri="http://myfaces.apache.org/tomahawk" prefix="t"%>
<%@ taglib uri="http://sakaiproject.org/jsf/sakai" prefix="sakai" %>
<% response.setContentType("text/html; charset=UTF-8"); %>
<f:view>
  <jsp:useBean id="msgs" class="org.sakaiproject.util.ResourceLoader" scope="session">
   <jsp:setProperty name="msgs" property="baseName" value="org.sakaiproject.tool.syllabus.bundle.Messages"/>
  </jsp:useBean>

  <sakai:view_container title="Export">
    <sakai:view_content>
      <script>includeLatestJQuery('export.jsp');</script>
      <h:form id="exportForm">
          <h:outputText value="#{SyllabusTool.alertMessage}" styleClass="alertMessage" rendered="#{SyllabusTool.alertMessage != null}" />
          <h:outputText value="#{SyllabusTool.successMessage}" styleClass="messageSuccess" rendered="#{SyllabusTool.successMessage != null}" /><f:verbatim>
          <h3>Enable automatic syllabus export to external systems</h3>
          <p>
            When enabled, the selected syllabus file will be automatically exported to external systems (for example,
            <a href="https://intranet.nyuad.nyu.edu/faculty-resources/academics/course-preparation-information/syllabi-and-course-requirements/" target="_blank">CAaR</a>
            and <a href="https://intranet.nyuad.nyu.edu/faculty-resources/academics/course-list-and-syllabi/" target="_blank">internal web course lists</a>).
          </p>
          <p>
            <div class="onoffswitch"></f:verbatim>
              <h:selectBooleanCheckbox value="#{SyllabusTool.exportEnabled}" id="exportEnabled" styleClass="onoffswitch-checkbox"/><f:verbatim>
              <label class="onoffswitch-label" for="exportForm:exportEnabled">
                <span class="onoffswitch-inner"></span>
                <span class="onoffswitch-switch"></span>
                <span class="sr-only">Toggle Enable Export</span>
              </label>
            </div>
          </p>
          <hr>
          <h4>Attachment Files</h4>
          <ul id="exportList">
          </f:verbatim>
          <h:outputText value="No attachments to export" rendered="#{SyllabusTool.exportPdfs.isEmpty()}"/>
          <t:dataList value="#{SyllabusTool.exportPdfs.keySet().toArray()}" var="entry">
            <t:dataList value="#{SyllabusTool.exportPdfs[entry]}" var="attachment">
              <f:verbatim><li>
                <label>
                  <input type="radio" name="selectedExportPdf" value="</f:verbatim><h:outputText value="#{attachment.syllabusAttachId}" /><f:verbatim>"> </f:verbatim>
                  <sakai:contentTypeMap fileType="#{attachment.type}" mapType="image" var="icon" pathPrefix="/library/image/"/>
                  <h:graphicImage id="icon" value="#{icon}" />
                  <h:outputText value=" " />
                  <h:outputLink value="#{attachment.url}" target="_blank">
                    <h:outputText value="#{attachment.name}" />
                  </h:outputLink><f:verbatim>
                </label>
              </li></f:verbatim>
            </t:dataList>
          </t:dataList>
          <f:verbatim>
          </ul>
        </f:verbatim>
        <h:inputHidden value="#{SyllabusTool.selectedExportAttachmentId}" id="selectedExportAttachmentId"/>
        <sakai:button_bar>
          <h:commandButton
            action="#{SyllabusTool.processSaveExportSettings}"
            styleClass="active"
            value="Save Changes" />
          <h:commandButton
            action="#{SyllabusTool.cancelSaveExportSettings}"
            value="Cancel" />
        </sakai:button_bar>
      </h:form>

      <script>
        function ExportForm() {
          this.$form = $('#exportForm');
          this.setupEnableCheckbox();
          this.setupAttachmentRadios();
        };

        ExportForm.prototype.setupEnableCheckbox = function() {
          var self = this;

          function enableDisableRadios() {
            var $checkbox = self.$form.find(':checkbox[id="exportForm:exportEnabled"]');
            if ($checkbox.is(':checked')) {
              // enable radios
              $('#exportList :radio').prop('disabled', false);
              $('#exportList label.disabled').removeClass('disabled');
            } else {
              // disable radios
              $('#exportList :radio').prop('disabled', true);
              $('#exportList label').addClass('disabled');
            }
          }

          var $checkbox = self.$form.find(':checkbox[id="exportForm:exportEnabled"]');
          $checkbox.on('click', function() {
            enableDisableRadios();
          });
          enableDisableRadios();
        };

        ExportForm.prototype.setupAttachmentRadios = function() {
          var $hidden = $(':hidden[id="exportForm:selectedExportAttachmentId"]');
          $('#exportList :radio').on('click', function() {
            var $radio = $(this);
            $hidden.val($radio.val()).trigger('changed');
          });

          if ($hidden.val() != "") {
            $('#exportList :radio[value="'+$hidden.val()+'"]').prop('checked', true);
          }
        };

        new ExportForm();
      </script>
      <style>
        #exportList {
          list-style: none;
        }
        #exportList label {
          font-weight: normal;
        }
      </style>
    </sakai:view_content>
  </sakai:view_container>
</f:view>
