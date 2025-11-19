
@echo off

set INSTALL=C:\Users\weath\install

java -classpath "%INSTALL%\classes;%INSTALL%\resources;%INSTALL%\jars\tornadofx-1.7.17.jar;%INSTALL%\jars\kotlin-stdlib-jdk8-1.3.72.jar;%INSTALL%\jars\kotlin-reflect-1.3.0.jar;%INSTALL%\jars\javax.json-1.1.2.jar;%INSTALL%\jars\kotlin-stdlib-jdk7-1.3.72.jar;%INSTALL%\jars\kotlin-stdlib-1.3.72.jar;%INSTALL%\jars\javax.json-api-1.1.2.jar;%INSTALL%\jars\kotlin-stdlib-common-1.3.72.jar;%INSTALL%\jars\annotations-13.0.jar" ChordsAppKt %*
