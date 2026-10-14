#!/bin/sh
# config/, plugins/ and data/ resolve against the working directory, so start from the distribution's root
cd "$(dirname "$0")/.." || exit 1
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" $JAVA_OPTS -jar staffix-admin.jar "$@"
