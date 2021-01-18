#!/bin/bash

if [ "$1" = "" ] || [ "$2" = "" ]; then
    echo "Usage: $0 <oauth user> <oauth secret>"
    echo
    echo "Example:"
    echo
    echo "$0 '1083734715644-0jis85t9e97qioni7c81to3hec1ihn12.apps.googleusercontent.com' 'SECRETSTRING123'"
    echo

    exit
fi

cd "`dirname $0`"
(
    cd impl
    mvn package assembly:single
)

trap "rm -f impl/target/attendance-impl-*-with-dependencies.jar" EXIT

myjar=`ls impl/target/attendance-impl-*-with-dependencies.jar 2>/dev/null`

if [ "$myjar" = "" ]; then
    echo "Couldn't find attendance-impl-with-dependencies jar.  Need to build?"
    exit
fi

echo $myjar

java -cp "$myjar" org.sakaiproject.attendance.oauth.Authorize "$1" "$2" attendance.credentials

echo
echo "Wrote to attendance.credentials"
