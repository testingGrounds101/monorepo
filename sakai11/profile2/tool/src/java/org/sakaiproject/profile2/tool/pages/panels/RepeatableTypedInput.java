package org.sakaiproject.profile2.tool.pages.panels;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.Model;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.form.Form;

import org.sakaiproject.profile2.model.TypeInputEntry;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.markup.ComponentTag;

import java.io.Serializable;

public class RepeatableTypedInput extends Panel {
    private List<Type> types;
    private ListView<TypeInputEntry> view;
    private Form form;

    public RepeatableTypedInput(final String id,
                                final Model<ArrayList<TypeInputEntry>> model,
                                final String valuePlaceholder,
                                final String typeQualifierPlaceholder,
                                final List<Type> types) {
        super(id, model);

        this.types = types;

        form = new Form("form") {
            protected void onSubmit() {
                List<TypeInputEntry> entries = (List<TypeInputEntry>) RepeatableTypedInput.this.getDefaultModelObject();

                List<TypeInputEntry> toRemove = new ArrayList<>();

                for (TypeInputEntry entry : entries) {
                    if (entry.getValue() == null) {
                        toRemove.add(entry);
                    }
                }

                for (TypeInputEntry removeme : toRemove) {
                    entries.remove(removeme);
                }
            }
        };

        view = new ListView<TypeInputEntry>("item", (List<TypeInputEntry>)this.getDefaultModelObject()) {
            public void populateItem(final ListItem<TypeInputEntry> item) {
                item.setOutputMarkupId(true);
                final TypeInputEntry entry = (TypeInputEntry)item.getDefaultModelObject();

                final DropDownChoice<String> choices = new DropDownChoice<String>("choices", new PropertyModel(entry, "type"), typeNames());
                final Component qualifierField = new TextField<String>("typeQualifier", new PropertyModel(entry, "typeQualifier")) {
                    @Override
                    protected void onComponentTag(final ComponentTag tag){
                        super.onComponentTag(tag);
                        tag.put("placeholder", typeQualifierPlaceholder);
                    }
                }.setOutputMarkupId(true);

                final Component valueField = new TextField<String>("value", new PropertyModel(entry, "value")) {
                    @Override
                    protected void onComponentTag(final ComponentTag tag){
                        super.onComponentTag(tag);
                        tag.put("placeholder", valuePlaceholder);
                    }
                }.setOutputMarkupId(true);

                choices.add(new AjaxFormComponentUpdatingBehavior("onchange") {
                    protected void onUpdate(AjaxRequestTarget target) {
                        updateInputState(choices, qualifierField, valueField);
                        target.add(item);
                    }
                });

                choices.setNullValid(false);
                choices.setOutputMarkupId(true);

                item.add(choices);
                item.add(qualifierField);
                item.add(valueField);

                if (choices.getModelObject() == null) {
                    choices.setModelObject(typeNames().get(0));
                }

                updateInputState(choices, qualifierField, valueField);

                AjaxButton removeItem = new AjaxButton("removeItem", form) {
                    @Override
                    public void onSubmit(AjaxRequestTarget target, Form form) {
                        List<TypeInputEntry> entries = ((List<TypeInputEntry>)RepeatableTypedInput.this.getDefaultModelObject());

                        ((List<TypeInputEntry>)RepeatableTypedInput.this.getDefaultModelObject()).remove(entry);
                        target.add(RepeatableTypedInput.this);
                    }
                };

                removeItem.setOutputMarkupId(true);
                item.add(removeItem);
            }
        };

        view.setReuseItems(false);
        view.setOutputMarkupId(true);

        AjaxButton addAnother = new AjaxButton("addAnother", form) {
            @Override
            public void onSubmit(AjaxRequestTarget target, Form form) {
                RepeatableTypedInput.this.addEntry(null, "", "");
                target.add(RepeatableTypedInput.this);
            }
        };

        form.add(addAnother);

        form.add(view);
        this.add(form);
        this.setOutputMarkupId(true);
    }

    public void addEntry(String type, String typeQualifier, String value) {
        List<TypeInputEntry> entries = (List<TypeInputEntry>)this.getDefaultModelObject();
        entries.add(new TypeInputEntry(type, typeQualifier, value));
    }

    public static class Type implements Serializable {
        private static final long serialVersionUID = 1L;

        public String name;
        public boolean qualified;

        public Type(String name) {
            this.name = name;
        }

        public Type qualified(boolean value) {
            this.qualified = value;
            return this;
        }
    }

    public List<TypeInputEntry> entries() {
        return (List<TypeInputEntry>) this.getDefaultModelObject();
    }

    private List<String> typeNames() {
        List<String> result = new ArrayList<>();

        for (Type type : types) {
            result.add(type.name);
        }

        return result;
    }

    private Type typeForName(String name) {
        for (Type type : types) {
            if (name.equals(type.name)) {
                return type;
            }
        }

        throw new RuntimeException("No type found for name: " + name);
    }

    private void updateInputState(Component choices, Component qualifierField, Component valueField) {
        Type selected = typeForName((String)choices.getDefaultModelObject());

        if (selected.qualified) {
            qualifierField.setVisible(true);
        } else {
            qualifierField.setVisible(false);
        }
    }
}
