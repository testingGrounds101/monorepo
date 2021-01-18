#!/bin/bash

set -e

tomcat="$1"
environment="$2"

if [ "$tomcat" = "" ] || [ ! -d "$tomcat" ] || [ "$environment" = "" ]; then
    echo "Usage: $0 </path/to/apache-tomcat> <EnvironmentIdentifier>"
    echo
    echo "Identified must be alphanumeric with no spaces."
    echo

    exit
fi

temp="`mktemp -d`"
mkdir -p "$temp/repack"
cp "$tomcat/webapps/nyugrades-ws.war" "$temp/nyugrades-ws-orig.war"

(
    cd "$temp/repack"
    jar xf ../nyugrades-ws-orig.war

    cp -a "NYUGrades.jws" "NYUGrades${environment}.jws"
    sed -i "s|/\*%ENVIRONMENT%\*/|$environment|g" "NYUGrades${environment}.jws"

    jar cfm ../nyugrades-ws.war META-INF/MANIFEST.MF .
)

cp "$temp/nyugrades-ws.war" "$tomcat/webapps/nyugrades-ws.war"
rm -f "$temp/nyugrades-ws-orig.war" "$temp/nyugrades-ws.war"
rm -rf "$temp/repack"
rmdir "$temp"


