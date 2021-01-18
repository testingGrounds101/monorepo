HEDEX Implementation
--------------------

Hedex installs into Sakai and starts gathering data on user logins, assignment
submissions, and course visits. This data is made available on a set of RESTful
endpoints, authenticated with a particular user based on the requesting agent.

Installation
------------

Copy this code into your Sakai installation, alongside the other Sakai modules
such as announcement. Change to the hedex directory and compile with maven:

mvn clean install sakai:deploy

Once built, restart your Tomcat. The code will automatically create the tables
it needs, HDX\_SESSION\_DURATION, HDX\_COURSE\_VISITS, and
HDX\_ASSIGNMENT\_SUBMISSIONS, and then start to populate them as users use the
system.

Note: There are both master and 12.x branches.  The one we are using in
production will be 12.x.

Configuration
-------------

HEDEX returns JSON containing a tenant id. This is initially set to UNSPECIFIED
and if you want it set to something other than that use:

hedex.tenantId=OurSakaiTenantId

If you need to, you can disable the digester altogether. This might be useful in
a cluster.

hedex.digester.enabled = false (default: true)

HEDEX uses a site property, 'hedex-agent' to identify a site as being associated
with a particular analytics consumer. For example, Noodle Partners sites will
be marked with the site property 'hedex-agent=noodle'. The digester uses a
scheduled executor to update maps of both the marked sites and their members.
You can configure the interval for this site scan with:

hedex.site.update.interval = 20

The unit is minutes and it defaults to 30

Retrieving Data
---------------

When retrieving data, a specific Sakai user must be used as the login. This
must be either exactly the reqesting agent, 'noodle' for instance, or the agent 
appended with the string '-hedex-user'. The agent should also be passed as the
ReqestingAgent parameter. Without these two things matching, the request will
be rejected.

Firstly, have a look at scripts/pulldataexamples.py. To pull the HEDEX data,
your script first needs to login and get a session string. Every call henceforth
passes that session string. The calls will fail otherwise. Data is returned to the 
spec at: http://petstore.swagger.io/?url=http://employees.idatainc.com/~bparish/NoodleBus/swagger_NoodleBus_Retention_API_v4.yaml

NOTE: assignAvgAttmpt in the returned JSON for Get_Retention_Engagement_Assignments is calculated as the average of the
grades applied to the submission. It's not based on the number of times the student submits. In other words, it's not
really based on number of attempts, but on gradings by instructors.

To run the script, you'll need python3.x installed, and the SOAP client 'zeep'
installed. You can use pip for that: pip install zeep.
