package org.sakaiproject.attendance.tool.component;

import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.sakaiproject.attendance.tool.actions.Action;
import org.sakaiproject.attendance.tool.actions.ActionResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AttendanceTable extends WebMarkupContainer {

    private Map<String, Action> listeners = new HashMap<String, Action>();

    public AttendanceTable(String id) {
        super(id);

        setOutputMarkupId(true);
        setOutputMarkupPlaceholderTag(true);

        add(new AjaxEventBehavior("attendance.action") {
            @Override
            protected void updateAjaxAttributes(AjaxRequestAttributes attributes) {
                super.updateAjaxAttributes(attributes);
                attributes.setMethod(AjaxRequestAttributes.Method.POST);
                attributes.getDynamicExtraParameters().add("return [{\"name\": \"ajaxParams\", \"value\": JSON.stringify(attrs.event.extraData)}]");
            }

            @Override
            protected void onEvent(AjaxRequestTarget target) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode params = mapper.readTree(getRequest().getRequestParameters().getParameterValue("ajaxParams").toString());

                    ActionResponse response = handleEvent(params.get("action").asText(), params, target);

                    target.appendJavaScript(String.format("attendance.ajaxComplete(%d, '%s', %s);",
                        params.get("_requestId").intValue(), response.getStatus(), response.toJson()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public void addEventListener(String event, Action listener) {
        listeners.put(event, listener);
    }

    public ActionResponse handleEvent(String event, JsonNode params, AjaxRequestTarget target) {
        if (!listeners.containsKey(event)) {
            throw new RuntimeException("Missing AJAX handler");
        }

        return listeners.get(event).handleEvent(params, target);
    }
}
