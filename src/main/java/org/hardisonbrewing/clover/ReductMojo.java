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
import generated.Construct;
import generated.Coverage;
import generated.FileMetrics;
import generated.Line;
import generated.Project;

import java.io.File;
import java.io.FileNotFoundException;
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
    private static final String THREAD_COUNT = "threads";

    private String cloverXmlPath;
    private File cloverXmlFile;

    private String svnUsername;
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

        svnUsername = getProperty( "svnUsername" );
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

            // no files, skip it
            List<generated.File> files = _package.getFile();
            if ( files == null || files.isEmpty() ) {
                continue;
            }

            _packages.add( _package );
            _files.put( _package, new LinkedList<generated.File>( files ) );

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

        recalulate( project );

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

        Properties properties = info( filePath );
        long revision = Long.parseLong( properties.getProperty( "Revision" ) );

        FileMetrics fileMetrics = file.getMetrics();
        int originalElements = fileMetrics.getElements();

        reset( fileMetrics );

        if ( revision < cutoffRevision ) {
            String name = getFQN( _package, file );
            System.out.println( "Removed all " + originalElements + " elements from " + name + " @ r" + revision );
            return;
        }

        List<Long> revisions = blame( filePath );

        int elements = 0;

        for (Line line : file.getLine()) {
            int lineNumber = line.getNum();
            if ( cutoffRevision < revisions.get( lineNumber - 1 ) ) {
                add( fileMetrics, line );
            }
            else {
                Construct type = line.getType();
                elements += type == Construct.COND ? 2 : 1;
            }
        }

        if ( elements > 0 ) {
            String name = getFQN( _package, file );
            System.out.println( "Removed " + elements + " elements from " + name + " @ r" + revision );
        }
    }

    private String getFQN( generated.Package _package, generated.File file ) {

        String packageName = _package.getName();
        String name = file.getName();
        if ( packageName != null && packageName.length() > 0 ) {
            name = packageName + "." + name;
        }
        return name;
    }

    private void reset( ClassMetrics metrics ) {

        metrics.setStatements( 0 );
        metrics.setConditionals( 0 );
        metrics.setMethods( 0 );
        metrics.setElements( 0 );

        metrics.setCoveredstatements( 0 );
        metrics.setCoveredconditionals( 0 );
        metrics.setCoveredmethods( 0 );
        metrics.setCoveredelements( 0 );
    }

    private void add( ClassMetrics parent, Line line ) {

        int elements = 0;
        int coveredElements = 0;

        switch (line.getType()) {
            case STMT: {
                elements = 1;
                coveredElements = line.getCount();
                parent.setStatements( parent.getStatements() + elements );
                parent.setCoveredstatements( parent.getCoveredstatements() + coveredElements );
                break;
            }
            case COND: {
                elements = 2;
                coveredElements = line.getTruecount() + line.getFalsecount();
                parent.setConditionals( parent.getConditionals() + elements );
                parent.setCoveredconditionals( parent.getCoveredconditionals() + coveredElements );
                break;
            }
            case METHOD: {
                elements = 1;
                coveredElements = line.getCount();
                parent.setMethods( parent.getMethods() + elements );
                parent.setCoveredmethods( parent.getCoveredmethods() + coveredElements );
                break;
            }
        }

        parent.setElements( parent.getElements() + elements );
        parent.setCoveredelements( parent.getCoveredelements() + coveredElements );
    }

    private Properties info( String filePath ) throws Exception {

        List<String> cmd = new LinkedList<String>();
        cmd.add( "svn" );
        cmd.add( "info" );
        if ( svnUsername != null ) {
            cmd.add( "--username=" + svnUsername );
        }
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
        if ( svnUsername != null ) {
            cmd.add( "--username=" + svnUsername );
        }
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
        if ( svnUsername != null ) {
            cmd.add( "--username=" + svnUsername );
        }
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

    private void recalulate( Object parent ) {

        ClassMetrics parentMetrics = getMetrics( parent );

        int statements = 0;
        int conditionals = 0;
        int methods = 0;
        int elements = 0;

        int coveredStatements = 0;
        int coveredConditionals = 0;
        int coveredMethods = 0;
        int coveredElements = 0;

        for (Object child : getChildren( parent )) {

            if ( !( child instanceof generated.File ) ) {
                recalulate( child );
            }

            ClassMetrics childMetrics = getMetrics( child );

            statements += childMetrics.getStatements();
            conditionals += childMetrics.getConditionals();
            methods += childMetrics.getMethods();
            elements += childMetrics.getElements();

            coveredStatements += childMetrics.getCoveredstatements();
            coveredConditionals += childMetrics.getCoveredconditionals();
            coveredMethods += childMetrics.getCoveredmethods();
            coveredElements += childMetrics.getCoveredelements();
        }

        parentMetrics.setStatements( statements );
        parentMetrics.setConditionals( conditionals );
        parentMetrics.setMethods( methods );
        parentMetrics.setElements( elements );

        parentMetrics.setCoveredstatements( coveredStatements );
        parentMetrics.setCoveredconditionals( coveredConditionals );
        parentMetrics.setCoveredmethods( coveredMethods );
        parentMetrics.setCoveredelements( coveredElements );
    }

    private List<?> getChildren( Object object ) {

        if ( object instanceof Project ) {
            Project project = (Project) object;
            return project.getPackage();
        }
        else if ( object instanceof generated.Package ) {
            generated.Package _package = (generated.Package) object;
            return _package.getFile();
        }
        throw new IllegalStateException();
    }

    private ClassMetrics getMetrics( Object object ) {

        if ( object instanceof Project ) {
            Project project = (Project) object;
            return project.getMetrics();
        }
        else if ( object instanceof generated.Package ) {
            generated.Package _package = (generated.Package) object;
            return _package.getMetrics();
        }
        else if ( object instanceof generated.File ) {
            generated.File file = (generated.File) object;
            return file.getMetrics();
        }
        throw new IllegalStateException();
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
