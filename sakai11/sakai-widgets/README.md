# Widgets for Sakai

A collection of widgets that can be added to Sakai sites and workspaces to provide useful functionality.

## Current widgets

#### My connections
Shows a grid of your connections.

#### Site members
Shows the instructors, TAs and site members for a site.

#### My Calendar
When placed in a site will show the events for a site.
When in a user's my workspace, will show a rollup of all events in all sites.

#### Site Information
A better Site Information Display. Pulls the data from Site Info and displays it in an unobtrusive, (soon to be) collapsible view.

## Dependencies
Currently requires Sakai 12, or backport these features to 11.x:
* https://jira.sakaiproject.org/browse/SAK-31206
* ~~https://jira.sakaiproject.org/browse/SAK-31220~~ (already resolved in 11)
* ~~https://jira.sakaiproject.org/browse/SAK-30728~~ (already resolved in 11)
* ~~https://jira.sakaiproject.org/browse/SAK-30729~~ (already resolved in 11)
* https://jira.sakaiproject.org/browse/SAK-31247
* https://jira.sakaiproject.org/browse/SAK-31352
* ~~https://jira.sakaiproject.org/browse/SAK-31284~~ (already resolved in 11)
* https://jira.sakaiproject.org/browse/SAK-31321 (optional)

## Installation
Download the code either via cloning or grabbing the zip then in the top level directory, run: `mvn clean install sakai:deploy -Dmaven.tomcat.home=/path/to/your/tomcat`.

## Available configuration

````
# Set the maximum number of users to show in each section within the Site Members widget
widget.sitemembers.maxusers=30

# Set the maximum number of connections to show in the My Connections widget
widget.myconnections.maxusers=30

````

## Inline rendering
All of these widgets are capable of rendering themselves inline in a multi tool page layout in Sakai.


