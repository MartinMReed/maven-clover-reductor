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

## Build or Download
Dependency Projects: [hbc-maven-core](https://github.com/hardisonbrewing/hbc-maven-core)  
Available in Nexus: [http://repo.hardisonbrewing.org](http://repo.hardisonbrewing.org)  
Continuous Integration: [Bamboo Status](http://bamboo.hardisonbrewing.org/browse/MVN-CLVR)

# License
GNU Lesser General Public License, Version 3.0.
