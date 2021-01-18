#!/bin/bash

# We can ultimately remove this script if we never find we need it...
echo "$0 seems no longer needed.  Exiting"
exit


set -e

build_dir="$1"
tomcat="$2"

if [ "$build_dir" = "" ] || [ "$tomcat" = "" ]; then
  echo "Usage: $0 <CLE source directory> <Tomcat deploy directory>"
  exit
fi

if [ ! -d "$build_dir" ] || [ ! -f "$build_dir/pom.xml" ]; then
    echo "Build directory '$build_dir' doesn't look right.  Aborting."
    exit
fi

if [ ! -d "$tomcat" ] || [ ! -f "$tomcat/conf/server.xml" ]; then
    echo "Tomcat directory '$tomcat' doesn't look right.  Aborting."
    exit
fi


if [ -e "$tomcat/shared/lib/sakai-import-bb6-11.2.jar" ]; then
    echo "Moving blackboard jar into components/import-pack/WEB-INF/lib"
    mv "$tomcat/shared/lib/sakai-import-bb6-11.2.jar" "$tomcat/components/import-pack/WEB-INF/lib"
fi

if [ -e "$tomcat/shared/lib/sakai-import-bb9-nyu-11.2.jar" ]; then
    echo "Moving blackboard 9 jar into components/import-pack/WEB-INF/lib"
    mv "$tomcat/shared/lib/sakai-import-bb9-nyu-11.2.jar" "$tomcat/components/import-pack/WEB-INF/lib"
fi



# echo "Installing Samigo jar to import-pack component directory"
# cp "$build_dir/samigo/samigo-archive/sam-handlers/target/samigo-import-11.2.jar" \
#     "$tomcat/components/import-pack/WEB-INF/lib/"

echo "Moving sakai-anouncement-import [sic] jar"
mv "$tomcat/components/sakai-anouncement-import/WEB-INF/lib/sakai-import-announcement-11.2.jar" \
    "$tomcat/components/import-pack/WEB-INF/lib/"


echo "Merging components/sakai-anouncement-import/WEB-INF/components.xml with components/import-pack/WEB-INF/components.xml"

# As of Sakai 11.2 the components.xml seems empty...
known_good="9ac547f6c720ea6a39d36e1fa5972f0add2aeada"

hash=$(sha1sum "$tomcat/components/sakai-anouncement-import/WEB-INF/components.xml" | cut -d' ' -f1)

if [ "$hash" != "$known_good" ]; then
  echo "ERROR: components/sakai-anouncement-import/WEB-INF/components.xml seems to have changed since the '$0' script was written.  Please verify the XML embedded in '$0' and make sure it still matches what's in components.xml."
  exit
fi

sed -i 's|</beans>|<bean id="org.sakaiproject.importer.impl.handlers.AnnouncementHandler"\n                   class="org.sakaiproject.importer.impl.handlers.AnnouncementHandler"\n                   >\n           <property name="announcementService">\n                   <ref bean="org.sakaiproject.announcement.api.AnnouncementService" /> \n           </property>\n        </bean>\n        <bean id="org.sakaiproject.importer.impl.handlers.SamigoAssessmentHandler"\n                   class="org.sakaiproject.importer.impl.handlers.SamigoAssessmentHandler"\n                   >\n        </bean>\n        \n        <bean id="org.sakaiproject.importer.impl.handlers.SamigoPoolHandler"\n                   class="org.sakaiproject.importer.impl.handlers.SamigoPoolHandler"\n                   >\n        </bean>\n     </beans>|' "$tomcat/components/import-pack/WEB-INF/components.xml"

echo "Removing '$tomcat/components/sakai-anouncement-import'"
rm -rf "$tomcat/components/sakai-anouncement-import"
