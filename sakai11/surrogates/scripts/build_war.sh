#!/bin/bash

echo "======================================================================="
echo "Building the surrogates tool"
echo "======================================================================="

cd "`dirname $0`/../"

jruby_url="http://jruby.org.s3.amazonaws.com/downloads/1.7.0/jruby-complete-1.7.0.jar"
sha1sum="fd8905cbc2c4860af5e28a172a941c25d7ea575e"

#wget -c "$jruby_url"

sha1sum "`basename $jruby_url`" | grep "fd8905cbc2c4860af5e28a172a941c25d7ea575e" &>/dev/null

if [ "$?" = "0" ]; then
    echo "JRuby SHA1 looks good"
else
    echo "SHA1 check failed for JRuby!  Bailing out!"
    exit
fi

export GEM_HOME=$PWD/local_gems
java -cp '*' org.jruby.Main --1.9 -S gem install bundler --version '1.17'
java -cp '*' org.jruby.Main --1.9 -S gem install --version '0.9.2.2' rake
java -cp '*' org.jruby.Main --1.9 $GEM_HOME/bin/bundle install

java -cp '*' org.jruby.Main --1.9 $GEM_HOME/bin/warble war

echo "======================================================================="
