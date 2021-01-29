# JFR activator

The purpose of this project is to create a mechanism
to automatically trigger creation of JFR reports once there is a reasonable
expectation that the JVM is **heading into trouble** (such as full garbage collection pauses or even worse).

Long term we expect to have a highly configurable mechanism to define what it means to be heading into trouble for a particular context.

# Organization

This is pretty straightforward maven multi-module project, with the following:

 * jfragentlib/    creates the agentlib jar (so that running "java -agenlib:jfragentlib.jar ..." is the main way to use this project
 * fbjfrdemo/      demo project, also to be used for debugging during 
 
# How to try the demo

Assume VERSION is the environment variable pointing to the current version of the project (initially 0.0.1), you can get it from pom.xml or with the following unstable command line, so please check

```bash
	VERSION=$(mvn org.apache.maven.plugins:maven-help-plugin:3.1.0:evaluate -Dexpression=project.version |grep -v '^\[')
```

First build the projectfrom the top level dir

```bash
	mvn package
```

Then from command line:

```bash
	 java -javaagent:./fbjfragentlib/target/fbjfragentlib-${VERSION}.jar=demo -jar ./fbjfrdemo/target/fbjfrdemo-${VERSION}.jar
```
