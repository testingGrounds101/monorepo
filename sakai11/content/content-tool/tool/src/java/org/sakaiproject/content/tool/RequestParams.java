package org.sakaiproject.content.tool;

import javax.servlet.http.HttpServletRequest;

public class RequestParams {

    private HttpServletRequest request = null;

    public RequestParams(HttpServletRequest request) {
        this.request = request;
    }

    public String getString(String param, String defaultValue) {
        String result = request.getParameter(param);

        if (result == null || "".equals(result)) {
            return defaultValue;
        } else {
            return result;
        }
    }
}
