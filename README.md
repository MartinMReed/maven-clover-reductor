# Usage
A [Maven](http://maven.apache.org/download.html) plugin to remove Clover.xml line entries based on revision date from SVN.

After running Clover or other tool to convert to Clover.xml format, run the `reduct` goal passing in the coverage file and cutoff date.

```bash
mvn org.hardisonbrewing:maven-clover-reductor:1.0.1-SNAPSHOT:reduct \
-Dclover=target/site/clover/clover.xml \
-DworkingCopy=. \
-DcutoffDate=2013-01-01
```

You can change the number to threads used to run `svn:blame` by adding `-Dthreads=<thread count>`.  
To set the SVN username add `-DsvnUsername=<username>`.

# Build or Download
To build this you need to use [Maven](http://maven.apache.org/download.html) with the [hbc-maven-core](https://github.com/hardisonbrewing/hbc-maven-core) project. Alternatively you can pull the latest version of hbc-maven-core from [http://repo.hardisonbrewing.org](http://repo.hardisonbrewing.org) (see repository settings below).

# Pulling the latest version from Nexus
To pull the latest version of the plugin you will need to update your [remote repository](http://maven.apache.org/guides/introduction/introduction-to-repositories.html) settings under your `.m2/settings.xml`.

```xml
<repositories>
  <repository>
		<id>hardisonbrewing-releases</id>
		<name>hardisonbrewing-releases</name>
		<url>http://repo.hardisonbrewing.org/content/repositories/releases/</url>
	</repository>
	<repository>
		<id>hardisonbrewing-snapshots</id>
		<name>hardisonbrewing-snapshots</name>
		<url>http://repo.hardisonbrewing.org/content/repositories/snapshots/</url>
	</repository>
</repositories>
```

To download this plugin without building it manually, you can add the following remote plugin repository:

```xml
<pluginRepositories>
	<pluginRepository>
		<id>hardisonbrewing-releases</id>
		<name>hardisonbrewing-releases</name>
		<url>http://repo.hardisonbrewing.org/content/repositories/releases/</url>
	</pluginRepository>
	<pluginRepository>
		<id>hardisonbrewing-snapshots</id>
		<name>hardisonbrewing-snapshots</name>
		<url>http://repo.hardisonbrewing.org/content/repositories/snapshots/</url>
	</pluginRepository>
</pluginRepositories>
```

Continuous Integration: [Bamboo Status](http://bamboo.hardisonbrewing.org/browse/MVN-CLVR)

# License
GNU Lesser General Public License, Version 3.0.
