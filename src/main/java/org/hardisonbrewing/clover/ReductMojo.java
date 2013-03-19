/**
 * Copyright (c) 2013 Martin M Reed
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.hardisonbrewing.clover;

import generated.ClassMetrics;
import generated.Coverage;
import generated.FileMetrics;
import generated.Line;
import generated.PackageMetrics;
import generated.Project;
import generated.ProjectMetrics;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.hardisonbrewing.jaxb.JAXB;

/**
 * @goal reduct
 * @phase reduct
 * @requiresProject false
 */
public final class ReductMojo extends AbstractMojo {

    private static final int DEFAULT_THREAD_COUNT = 15;

    private static final String CLOVER = "clover";
    private static final String WORKING_COPY = "workingCopy";
    private static final String CUTOFF_DATE = "cutoffDate";
    private static final String THREAD_COUNT = "threadCount";

    private static final BigInteger ZERO = BigInteger.valueOf( 0 );
    private static final BigInteger ONE = BigInteger.valueOf( 1 );

    private String cloverXmlPath;
    private File cloverXmlFile;

    private String workingCopyPath;
    private String cutoffDate;
    private int threadCount = DEFAULT_THREAD_COUNT;

    private File targetDirectory;

    private long cutoffRevision;

    /**
     * @parameter expression="${project}"
     * @required
     */
    private MavenProject mavenProject;

    /**
     * @parameter expression="${session}"
     * @readonly
     * @required
     */
    private MavenSession mavenSession;

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {

        if ( !mavenProject.isExecutionRoot() ) {
            return;
        }

        long start = System.currentTimeMillis();

        try {
            _execute();
        }
        catch (Exception e) {
            throw new IllegalStateException( e );
        }
        finally {
            long end = System.currentTimeMillis();
            System.out.println( "Executed in " + ( ( end - start ) / 1000.0 ) + "s" );
        }
    }

    private void _execute() throws Exception {

        initCloverXmlPath();
        initWorkingCopyPath();
        initCutoffDate();
        initThreadCount();

        System.out.println( "Using coverage report from: " + cloverXmlFile.getPath() );

        targetDirectory = new File( "target", "clover-reductor" );
        targetDirectory.mkdirs();

        FileUtils.copyFile( cloverXmlFile, new File( targetDirectory, "clover-original.xml" ) );

        Coverage coverage = JAXB.unmarshal( cloverXmlFile, Coverage.class );
        Project project = coverage.getProject();
        System.out.println( "Running Reductor: " + project.getName() );

        List<generated.Package> packages = project.getPackage();
        if ( packages == null || packages.isEmpty() ) {
            System.out.println( "No packages found." );
            return;
        }

        cutoffRevision = findCutoffRevision( workingCopyPath );
        System.out.println( "Cutoff Revision: " + cutoffRevision );

        List<generated.Package> _packages = new LinkedList<generated.Package>();
        Map<generated.Package, List<generated.File>> _files = new HashMap<generated.Package, List<generated.File>>();

        int fileCount = 0;

        for (generated.Package _package : project.getPackage()) {

            PackageMetrics packageMetrics = _package.getMetrics();

            // no coverage, skip it
            int coveredElements = packageMetrics.getCoveredelements().intValue();
            if ( coveredElements == 0 ) {
                continue;
            }

            // no files, skip it
            List<generated.File> files = _package.getFile();
            if ( files == null || files.isEmpty() ) {
                continue;
            }

            _packages.add( _package );

            List<generated.File> __files = new LinkedList<generated.File>();

            // only add files with coverage
            for (generated.File file : files) {
                FileMetrics fileMetrics = file.getMetrics();
                if ( fileMetrics.getCoveredelements().intValue() > 0 ) {
                    __files.add( file );
                }
            }

            _files.put( _package, __files );

            fileCount += files.size();
        }

        if ( fileCount == 0 ) {
            System.out.println( "No files found." );
            return;
        }

        BlameThread[] threads = new BlameThread[Math.min( fileCount, threadCount )];
        System.out.println( "Starting " + threads.length + " threads for " + fileCount + " files." );

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new BlameThread( project, _packages, _files );
            new Thread( threads[i] ).start();
        }

        for (BlameThread thread : threads) {
            thread.waitUntilFinished();
        }

        File cloverXmlReducedFile = new File( cloverXmlFile.getParent(), "clover-reduced.xml" );
        System.out.println( "Saving new coverage report to: " + cloverXmlReducedFile.getPath() );
        JAXB.marshal( cloverXmlReducedFile, coverage );
    }

    private String getProperty( String key ) {

        String value = mavenSession.getExecutionProperties().getProperty( key );
        if ( value == null ) {
            value = mavenProject.getProperties().getProperty( key );
        }
        if ( value == null ) {
            return null;
        }
        boolean startsWith = value.startsWith( "\"" );
        boolean endsWith = value.endsWith( "\"" );
        if ( startsWith || endsWith ) {
            int length = value.length();
            int start = startsWith ? 1 : 0;
            int end = endsWith ? length - 1 : length;
            value = value.substring( start, end );
        }
        return value;
    }

    private void initCloverXmlPath() throws Exception {

        cloverXmlPath = getProperty( CLOVER );
        if ( cloverXmlPath == null || cloverXmlPath.length() == 0 ) {
            System.err.println( "Required property `" + CLOVER + "` missing. Use -D" + CLOVER + "=<path to xml>" );
            throw new IllegalArgumentException();
        }

        cloverXmlFile = new File( cloverXmlPath );
        if ( !cloverXmlFile.exists() ) {
            throw new FileNotFoundException( cloverXmlFile.getPath() );
        }
    }

    private void initWorkingCopyPath() throws Exception {

        workingCopyPath = getProperty( WORKING_COPY );
        if ( workingCopyPath == null || workingCopyPath.length() == 0 ) {
            System.err.println( "Required property `" + WORKING_COPY + "` missing. Use -D" + WORKING_COPY + "=<path to working copy>" );
            throw new IllegalArgumentException();
        }

        if ( !new File( workingCopyPath ).exists() ) {
            throw new FileNotFoundException( workingCopyPath );
        }

        if ( !new File( workingCopyPath, ".svn" ).exists() ) {
            System.err.println( "Directory is not a working copy: " + workingCopyPath );
            throw new IllegalArgumentException();
        }
    }

    private void initCutoffDate() throws Exception {

        cutoffDate = getProperty( CUTOFF_DATE );
        if ( cutoffDate == null || cutoffDate.length() == 0 ) {
            System.err.println( "Required property `" + CUTOFF_DATE + "` missing. Use -D" + CUTOFF_DATE + "=<timestamp>" );
            throw new IllegalArgumentException();
        }
    }

    private void initThreadCount() throws Exception {

        String threadCountStr = getProperty( THREAD_COUNT );
        if ( threadCountStr != null && threadCountStr.length() > 0 ) {
            try {
                threadCount = Integer.parseInt( threadCountStr );
            }
            catch (NumberFormatException e) {
                System.err.println( "Illegal thread count specified: -D" + THREAD_COUNT + "=" + threadCountStr + ". Must be an integer." );
                throw new IllegalArgumentException();
            }
        }
    }

    private void inspectFile( Project project, generated.Package _package, generated.File file ) throws Exception {

        String filePath = file.getPath();
        if ( !new File( filePath ).exists() ) {
            throw new FileNotFoundException( filePath );
        }

        ProjectMetrics projectMetrics = project.getMetrics();
        PackageMetrics packageMetrics = _package.getMetrics();
        FileMetrics fileMetrics = file.getMetrics();

        Properties properties = info( filePath );
        long revision = Long.parseLong( properties.getProperty( "Revision" ) );

        if ( revision < cutoffRevision ) {

            int elements = fileMetrics.getCoveredelements().intValue();

            synchronized (_package) {
                reduce( projectMetrics, packageMetrics, fileMetrics );
            }

            String name = getFQN( _package, file );
            System.out.println( "Removed all " + elements + " elements from " + name + " @ r" + revision );
            return;
        }

        List<Long> revisions = blame( filePath );

        int elements = 0;

        for (Line line : file.getLine()) {
            int lineNumber = line.getNum().intValue();
            if ( revisions.get( lineNumber - 1 ) < cutoffRevision && lineCount( line ) > 0 ) {
                synchronized (_package) {
                    reduce( projectMetrics, line );
                    reduce( packageMetrics, line );
                }
                elements++;
            }
        }

        if ( elements > 0 ) {
            String name = getFQN( _package, file );
            System.out.println( "Removed " + elements + " elements from " + name + " @ r" + revision );
        }
    }

    private int lineCount( Line line ) {

        switch (line.getType()) {
            case COND:
                return line.getTruecount().intValue() + line.getFalsecount().intValue();
            case STMT:
            case METHOD:
                return line.getCount().intValue();
        }

        return 0;
    }

    private String getFQN( generated.Package _package, generated.File file ) {

        String packageName = _package.getName();
        String name = file.getName();
        if ( packageName != null && packageName.length() > 0 ) {
            name = packageName + "." + name;
        }
        return name;
    }

    private void reduce( ProjectMetrics projectMetrics, PackageMetrics packageMetrics, FileMetrics fileMetrics ) {

        reduce( projectMetrics, fileMetrics );

        packageMetrics.setClasses( packageMetrics.getClasses().subtract( fileMetrics.getClasses() ) );
        packageMetrics.setFiles( packageMetrics.getFiles().subtract( ONE ) );
        packageMetrics.setComplexity( packageMetrics.getComplexity().subtract( fileMetrics.getComplexity() ) );
        packageMetrics.setLoc( packageMetrics.getLoc().subtract( fileMetrics.getLoc() ) );
        packageMetrics.setNcloc( packageMetrics.getNcloc().subtract( fileMetrics.getNcloc() ) );

        reduce( packageMetrics, fileMetrics );

        fileMetrics.setCoveredstatements( ZERO );
        fileMetrics.setCoveredconditionals( ZERO );
        fileMetrics.setCoveredmethods( ZERO );
        fileMetrics.setCoveredelements( ZERO );
    }

    private void reduce( ClassMetrics packageMetrics, Line line ) {

        switch (line.getType()) {
            case STMT:
                packageMetrics.setCoveredstatements( packageMetrics.getCoveredstatements().subtract( ONE ) );
                break;
            case COND:
                packageMetrics.setCoveredconditionals( packageMetrics.getCoveredconditionals().subtract( ONE ) );
                break;
            case METHOD:
                packageMetrics.setCoveredmethods( packageMetrics.getCoveredmethods().subtract( ONE ) );
                break;
        }

        packageMetrics.setCoveredelements( packageMetrics.getCoveredelements().subtract( ONE ) );
    }

    private void reduce( ClassMetrics packageMetrics, ClassMetrics fileMetrics ) {

        packageMetrics.setStatements( packageMetrics.getStatements().subtract( fileMetrics.getStatements() ) );
        packageMetrics.setConditionals( packageMetrics.getConditionals().subtract( fileMetrics.getConditionals() ) );
        packageMetrics.setMethods( packageMetrics.getMethods().subtract( fileMetrics.getMethods() ) );
        packageMetrics.setElements( packageMetrics.getElements().subtract( fileMetrics.getElements() ) );

        packageMetrics.setCoveredstatements( packageMetrics.getCoveredstatements().subtract( fileMetrics.getCoveredstatements() ) );
        packageMetrics.setCoveredconditionals( packageMetrics.getCoveredconditionals().subtract( fileMetrics.getCoveredconditionals() ) );
        packageMetrics.setCoveredmethods( packageMetrics.getCoveredmethods().subtract( fileMetrics.getCoveredmethods() ) );
        packageMetrics.setCoveredelements( packageMetrics.getCoveredelements().subtract( fileMetrics.getCoveredelements() ) );
    }

    private Properties info( String filePath ) throws Exception {

        List<String> cmd = new LinkedList<String>();
        cmd.add( "svn" );
        cmd.add( "info" );
        cmd.add( filePath );

        Properties properties = new Properties();
        StreamConsumer streamConsumer = new InfoStreamConsumer( properties );
        CommandLineUtils.executeCommandLine( build( cmd ), streamConsumer, streamConsumer );
        return properties;
    }

    private List<Long> blame( String filePath ) throws Exception {

        List<String> cmd = new LinkedList<String>();
        cmd.add( "svn" );
        cmd.add( "blame" );
        cmd.add( filePath );

        List<Long> revisions = new LinkedList<Long>();
        StreamConsumer streamConsumer = new BlameStreamConsumer( revisions );
        CommandLineUtils.executeCommandLine( build( cmd ), streamConsumer, streamConsumer );
        return revisions;
    }

    private long findCutoffRevision( String workingCopy ) throws Exception {

        Properties properties = info( workingCopy );

        List<String> cmd = new LinkedList<String>();
        cmd.add( "svn" );
        cmd.add( "checkout" );
        cmd.add( "-r" );
        cmd.add( "{" + cutoffDate + "}" );
        cmd.add( "--depth" );
        cmd.add( "empty" );
        cmd.add( properties.getProperty( "Repository Root" ) );
        cmd.add( targetDirectory.getPath() );

        RevisionStreamConsumer streamConsumer = new RevisionStreamConsumer();
        CommandLineUtils.executeCommandLine( build( cmd ), streamConsumer, streamConsumer );
        return streamConsumer.getRevision();
    }

    private Commandline build( List<String> cmd ) throws CommandLineException {

        Commandline commandLine = new Commandline();
        commandLine.setExecutable( cmd.get( 0 ) );

        for (int i = 1; i < cmd.size(); i++) {
            commandLine.createArg().setValue( cmd.get( i ) );
        }

        return commandLine;
    }

    private final class BlameThread implements Runnable {

        private final Object lock = new Object();

        private final Project project;
        private final List<generated.Package> _packages;
        private final Map<generated.Package, List<generated.File>> _files;

        private boolean finished;

        public BlameThread(Project project, List<generated.Package> _packages, Map<generated.Package, List<generated.File>> _files) {

            this.project = project;
            this._packages = _packages;
            this._files = _files;
        }

        @Override
        public void run() {

            try {

                while (true) {

                    generated.Package _package = null;
                    generated.File file = null;

                    synchronized (_packages) {

                        if ( _packages.isEmpty() ) {
                            break;
                        }

                        _package = _packages.get( 0 );

                        synchronized (_package) {

                            List<generated.File> files = _files.get( _package );
                            if ( files == null ) {
                                continue;
                            }

                            file = files.remove( 0 );

                            if ( files.isEmpty() ) {
                                _packages.remove( 0 );
                                _files.remove( _package );
                            }
                        }
                    }

                    try {
                        inspectFile( project, _package, file );
                    }
                    catch (Exception e) {
                        System.err.println( "Unable to inspect file: " + file.getName() );
                        e.printStackTrace();
                    }
                }
            }
            finally {

                finished = true;

                synchronized (lock) {
                    lock.notify();
                }
            }
        }

        private void waitUntilFinished() {

            if ( finished ) {
                return;
            }

            synchronized (lock) {
                while (!finished) {
                    try {
                        lock.wait();
                    }
                    catch (InterruptedException e) {
                        // do nothing
                    }
                }
            }
        }
    }
}
