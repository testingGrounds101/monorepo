#!/bin/bash

export PATH=/home/sakai/jdk-current/bin/:$PATH

cd "`dirname "$0"`"

java -Djava.security.egd=file:///dev/urandom -cp 'lib/*' org.jruby.Main upgrade.rb ${1+"$@"}
