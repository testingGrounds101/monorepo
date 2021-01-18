package org.sakaiproject.attendance.tool.models;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom ModalWindow that adds behaviours specific to our tool
 */
public class AttendanceModalWindow extends ModalWindow {

	private static final long serialVersionUID = 1L;

	private Component componentToReturnFocusTo;
	private List<WindowClosedCallback> closeCallbacks;

	public AttendanceModalWindow(final String id) {
		super(id);

		this.closeCallbacks = new ArrayList<>();
	}

	@Override
	public void onInitialize() {
		super.onInitialize();

		setMaskType(MaskType.TRANSPARENT);
		setResizable(false);
		setUseInitialHeight(false);

		setDefaultWindowClosedCallback();

		setWindowClosedCallback(new WindowClosedCallback() {
			private static final long serialVersionUID = 1L;

			@Override
			public void onClose(final AjaxRequestTarget target) {
				AttendanceModalWindow.this.closeCallbacks.forEach((callback) -> {
					callback.onClose(target);
				});
			}
		});
	}

	@Override
	protected CharSequence getShowJavaScript() {
		StringBuilder extraJavascript = new StringBuilder();

		// focus the first input field in the content pane
		extraJavascript.append(String.format("setTimeout(function() {$('#%s').find(':input:first:visible, textarea:first:visible').focus();});",
			getContent().getMarkupId()));

		return super.getShowJavaScript().toString() + extraJavascript.toString();
	}

	@Override
	public ModalWindow setContent(final Component component) {
		component.setOutputMarkupId(true);

		return super.setContent(component);
	}

	/**
	 * Set the component to return focus to upon closing the window. The component MUST have it's output markup id set, by calling
	 * setOutputMarkupId(true).
	 *
	 * @param component
	 */
	public void setComponentToReturnFocusTo(final Component component) {
		this.componentToReturnFocusTo = component;
	}

	public void addWindowClosedCallback(final WindowClosedCallback callback) {
		this.closeCallbacks.add(callback);
	}

	public void clearWindowClosedCallbacks() {
		this.closeCallbacks = new ArrayList<>();
		setDefaultWindowClosedCallback();
	}

	private void setDefaultWindowClosedCallback() {
		addWindowClosedCallback(new WindowClosedCallback() {
			private static final long serialVersionUID = 1L;

			@Override
			public void onClose(final AjaxRequestTarget target) {
				// Disable all buttons with in the modal in case it takes a moment to close
				target.appendJavaScript(
					String.format("$('#%s :input').prop('disabled', true);",
						AttendanceModalWindow.this.getContent().getMarkupId()));

				// Return focus to defined component
				if (AttendanceModalWindow.this.componentToReturnFocusTo != null) {
					target.appendJavaScript(String.format("setTimeout(function() {$('#%s').focus();});",
						AttendanceModalWindow.this.componentToReturnFocusTo.getMarkupId()));
				}
			}
		});
	}
}