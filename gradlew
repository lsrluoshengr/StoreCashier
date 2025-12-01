#!/usr/bin/env sh

#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
#
#  Gradle startup script for POSIX environments
#
##############################################################################

# Set local scope for the variables with windows NT shell
if [ -n "$OS" ] && [ -z "$OS_UNIX" ]; then
    echo "This script is for POSIX environments only. Please use gradlew.bat on Windows."
    exit 1
fi

set -e

# Initialize variables that are defined via system properties
# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn() {
    echo "$*"
}

die() {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "$(uname)" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MSYS* | MINGW* | MINGW32* | MSYS_NT* )
    msys=true
    ;;
  NONSTOP* )
    nonstop=true
    ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ]; then
    if [ -x "$JAVA_HOME/jre/sh/java" ]; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ]; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD=java
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Increase the maximum file descriptors if we can.
if [ "$cygwin" = "false" ] && [ "$darwin" = "false" ] && [ "$nonstop" = "false" ]; then
    MAX_FD_LIMIT=$(ulimit -H -n)
    if [ "$MAX_FD" = "maximum" ] || [ "$MAX_FD" = "max" ]; then
        MAX_FD="$MAX_FD_LIMIT"
    fi
    ulimit -n "$MAX_FD" || warn "Could not set maximum file descriptor limit: $MAX_FD"
fi

# For Darwin, add options to specify how the application appears in the dock
if [ "$darwin" = "true" ]; then
    GRADLE_OPTS="$GRADLE_OPTS \"-Xdock:name=Gradle\" \"-Xdock:icon=$APP_HOME/media/gradle.icns\""
fi

# For Cygwin or MSYS, switch paths to Windows format before running java
if [ "$cygwin" = "true" ] || [ "$msys" = "true" ]; then
    APP_HOME=$(cygpath -C ANSI -w "$APP_HOME")
    CLASSPATH=$(cygpath -C ANSI -w "$CLASSPATH")
    JAVACMD=$(cygpath -C ANSI -w "$JAVACMD")

    # Now convert the arguments to Windows format if they contain spaces
    for arg in "$@"; do
        if [ "$arg" != "${arg// /}" ]; then
            arg=\"$arg\"
        fi
        WIN_ARGS="$WIN_ARGS $arg"
    done
    eval set -- "$WIN_ARGS"
fi

# Split up the JVM_OPTS and GRADLE_OPTS values into an array of arguments to pass to the JVM
# Allow the user to override the defaults
# Append the default JVM options
ALL_JVM_OPTS="$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS"

# Pass all arguments to the Java command
exec "$JAVACMD" $ALL_JVM_OPTS "-Dorg.gradle.appname=$(basename "$0")" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
