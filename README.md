HOCON Jackson data format
=========================
Implementation of a [Jackson](https://github.com/FasterXML/jackson) parser for parsing the HOCON data format.

What the heck is HOCON?
-------------------------
HOCON stands for Human-Optimized Config Object Notation and is made by [Typesafe](https://github.com/lightbend/config).

In short, HOCON combines JSON, and Properties files into a single format. On most cases, JSON, and Properties formats are valid HOCON and it can be mixed and matched at will.   
Check out the [HOCON docs](https://github.com/lightbend/config/blob/main/HOCON.md) for more detail on the format.

Why this project?
------------------------
This project lets you use HOCON to configure any application that uses Jackson to parse its configuration files.

## Why this Fork?
My projects heavily rely on Jackson, and its annotation and polymorphism features. Migrating to other libraries/parsers is not feasible.  
But the original project did not see any updates in 6 years and lacked quite a few features.  

This fork adds and improves features:  
* Update to Jackson 2.13
* Update to JDK 8
* Adds HoconGenerator to directly generate Hocon
* Improved Numerically Index Objects using custom deserializers

How to Use
------------
### Install
Clone this project and run `mvn install`  

After that add the following dependency to your projects' pom:
```xml
  <dependency>
    <groupId>com.wolfyscript</groupId>
    <artifactId>jackson-dataformat-hocon</artifactId>
    <version>2.0-SNAPSHOT</version>
  </dependency>
```
*I am looking into publishing this project to the central repository soon.*

<details><summary>Old Project</summary>

Add the following fragment to your project pom to include HOCON data format:
```xml
  <dependency>
    <groupId>org.honton.chas.hocon</groupId>
    <artifactId>jackson-dataformat-hocon</artifactId>
    <version>1.1.1</version>
  </dependency>
```
</details>

### Use
Create the HoconMapper with the following constructor:
```java
  HoconMapper mapper = new HoconMapper();
```
To customize the generator features use:
```java
  HoconMapper mapper = new HoconMapper(new HoconFactory());
```

Some Caveats
------------
There is support for HOCON include statements if the URL or File version of ObjectMapper is used.  (Unfortunately, the Jackson InputDecorator will be ignored).
```java
  Configuration c = mapper.readValue(new URL("http://example.com/path/test.conf"), Configuration.class);
```
or
```java
  Configuration c = mapper.readValue(new File(filepath), Configuration.class);
```

There is support for Jackson InputDecorator if the InputStream or Reader version of ObjectMapper is used.  (Unfortunately, the HOCON statements include will be ignored).
```java
  Configuration c = mapper.readValue(new FileInputStream("http://example.com/path/test.conf"), Configuration.class);
```
or
```java
  Configuration c = mapper.readValue(new InputStreamReader(is), Configuration.class);
```

[![Build Status](https://travis-ci.org/tburch/dropwizard-extras.png?branch=master)](https://travis-ci.org/tburch/dropwizard-extras)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.jasonclawson/jackson-dataformat-hocon/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.jasonclawson/jackson-dataformat-hocon/)
[![Javadoc](https://javadoc-emblem.rhcloud.com/doc/com.jasonclawson/jackson-dataformat-hocon/badge.svg)](http://www.javadoc.io/doc/com.jasonclawson/jackson-dataformat-hocon)
