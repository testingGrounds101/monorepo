package org.sakaiproject.attendance.tool.actions;

class EmptyOkResponse implements ActionResponse {
    public EmptyOkResponse() {
    }

    @Override
    public String getStatus() {
        return "OK";
    }

    @Override
    public String toJson() {
        return "{}";
    }
}