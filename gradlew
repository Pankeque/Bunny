#!/bin/sh

APP_NAME="Gradle"
GRADLE_VERSION="8.4"
DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPS="gradle/wrapper/gradle-wrapper.properties"

die() { echo "$*" >&2; exit 1; }

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

if ! command -v unzip >/dev/null 2>&1 ; then
    die "Unzip is required to extract the Gradle distribution."
fi

if ! "$JAVACMD" -version >/dev/null 2>&1 ; then
    die "Java is required to run Gradle. Install JDK 17+ and set JAVA_HOME."
fi

exec "$JAVACMD" -cp "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
