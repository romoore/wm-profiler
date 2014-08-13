Owl Platform World Model Profiler
=================================

Version 1.0.0-BETA

Last updated August 12, 2014

Project Website: <https://github.com/romoore/wm-profiler>

Copyright (C) 2014 Robert Moore

This application is free software according to the terms and conditions of
the GNU General Purpose License, version 2.0 (or higher at your discretion).
You should have received a copy of the GNU General Purpose License v2.0 along
with this software as the file LICENSE.  If not, you may download a copy from
<http://www.gnu.org/licenses/gpl-2.0.txt>.

## About ##
Owl Platform World Model Profiler is a non-interactive tool used to assist 
in performing performance evaluations on a World Model.

## Compiling ##
This tool is intended to be compiled using the Apache Maven project
management tool.  The project is currently compatible with Apache Maven
version 3, which can be downloaded for free at <http://maven.apache.org/>.
To build the static JAR file output, the following command should be run
from the project root (where the pom.xml file is located):

    mvn clean install -U

If everything compiles correctly, then near the end of the Maven output,
this line should appear:

    [INFO] BUILD SUCCESS

In this case, the JAR file will be located in the ``target'' subdirectory.
If not, please visit the project website listed at the top of this
document for support.

## Running ##
TODO

## Notes ##
This utility makes use of [Jan Goyvaerts]' regular expression/Java code for
extracting quoted strings from a String variable.  The [Original Post] was on
StackOverflow.

[Original Post]: http://stackoverflow.com/questions/366202/regex-for-splitting-a-string-using-space-when-not-surrounded-by-single-or-double
[Jan Goyvaerts]: http://stackoverflow.com/users/33358/jan-goyvaerts
