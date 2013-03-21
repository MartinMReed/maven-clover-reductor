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

    private File cloverReportFile;

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
        initCloverFilePath();
        initWorkingCopyPath();
        initCutoffDate();
        initThreadCount();

        System.out.println( "Using coverage report from: " + cloverReportFile.getPath() );

        targetDirectory = new File( "target", "clover-reductor" );
        targetDirectory.mkdirs();

        FileUtils.copyFile( cloverReportFile, new File( targetDirectory, "clover-original.xml" ) );

        Coverage coverage = JAXB.unmarshal( cloverReportFile, Coverage.class );
        Project project = coverage.getProject();
        System.out.println( "Running Reductor: " + project.getName() );

        List<generated.Package> packages = project.getPackage();
        if ( packages == null || packages.isEmpty() ) {
            System.out.println( "No packages found." );
            return;
        }

        cutoffRevision = findCutoffRevision( workingCopyPath );
        System.out.println( "Cutoff Revision: " + cutoffRevision );

        List<generated.Package> packagesWithFiles = new LinkedList<generated.Package>();
        Map<generated.Package, List<generated.File>> packageFiles = new HashMap<generated.Package, List<generated.File>>();

        int fileCount = 0;

        for (generated.Package _package : packages) {

            // no files, skip it
            List<generated.File> files = _package.getFile();
            if ( files == null || files.isEmpty() ) {
                continue;
            }

            packagesWithFiles.add( _package );
            packageFiles.put( _package, new LinkedList<generated.File>( files ) );

            fileCount += files.size();
        }

        if ( fileCount == 0 ) {
            System.out.println( "No files found." );
            return;
        }

        BlameThread[] threads = new BlameThread[Math.min( fileCount, threadCount )];

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new BlameThread( project, packagesWithFiles, packageFiles );
            new Thread( threads[i] ).start();
        }

        for (BlameThread thread : threads) {
            thread.waitUntilFinished();
        }

        recalulate( project );

        File cloverReportReducedFile = reducedFile( cloverReportFile );
        System.out.println( "Saving new coverage report to: " + cloverReportReducedFile.getPath() );
        JAXB.marshal( cloverReportReducedFile, coverage );
    }

    private File reducedFile( File file ) {

        String name = file.getName();
        String extension = FileUtils.extension( name );
        name = name.substring( 0, name.length() - ( extension.length() + 1 ) );
        name = name + "-reduced." + extension;
        return new File( file.getParent(), name );
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

    private void initCloverFilePath() throws Exception {

        String cloverReportPath = getProperty( CLOVER );
        if ( cloverReportPath == null || cloverReportPath.length() == 0 ) {
            System.err.println( "Required property `" + CLOVER + "` missing. Use -D" + CLOVER + "=<path to xml>" );
            throw new IllegalArgumentException();
        }

        cloverReportFile = new File( cloverReportPath );
        if ( !cloverReportFile.exists() ) {
            throw new FileNotFoundException( cloverReportFile.getPath() );
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

    private void inspectFile( generated.File file ) throws Exception {

        String filePath = file.getPath();
        if ( !new File( filePath ).exists() ) {
            throw new FileNotFoundException( filePath );
        }

        List<Line> lines = file.getLine();
        FileMetrics fileMetrics = file.getMetrics();

        file.getClazz().clear(); ////////////////////////////////////
        reset( fileMetrics );

        Properties properties = info( filePath );
        long revision = Long.parseLong( properties.getProperty( "Revision" ) );

        if ( revision >= cutoffRevision ) {
            inspectLines( file );
        }
        else {
            lines.clear(); /////////////////////////////////
        }
    }

    private void inspectLines( generated.File file ) throws Exception {

        List<Line> lines = file.getLine();
        FileMetrics fileMetrics = file.getMetrics();
        List<Long> revisions = blame( file.getPath() );

        for (int i = lines.size() - 1; i >= 0; i--) {
            Line line = lines.get( i );
            int lineNumber = line.getNum();
            if ( cutoffRevision < revisions.get( lineNumber - 1 ) ) {
                add( fileMetrics, line );
            }
            else {
                lines.remove( i ); ///////////////////////
            }
        }
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
            case COND: {
                elements = 2;
                int trueCount = Math.min( 1, line.getTruecount() );
                int falseCount = Math.min( 1, line.getFalsecount() );
                coveredElements = trueCount + falseCount;
                break;
            }
            case STMT:
            case METHOD: {
                elements = 1;
                coveredElements = Math.min( 1, line.getCount() );
                break;
            }
        }

        switch (line.getType()) {
            case STMT: {
                parent.setStatements( parent.getStatements() + elements );
                parent.setCoveredstatements( parent.getCoveredstatements() + coveredElements );
                break;
            }
            case COND: {
                parent.setConditionals( parent.getConditionals() + elements );
                parent.setCoveredconditionals( parent.getCoveredconditionals() + coveredElements );
                break;
            }
            case METHOD: {
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

        int files = 0;
        int classes = 0;

        for (Object child : getChildren( parent )) {

            ClassMetrics childMetrics;

            if ( child instanceof generated.File ) {

                generated.File file = (generated.File) child;
                FileMetrics fileMetrics = file.getMetrics();
                childMetrics = fileMetrics;

                files++;

                int _classes = file.getClazz().size();
                fileMetrics.setClasses( _classes );
                classes += _classes;
            }
            else {
                childMetrics = getMetrics( child );
                recalulate( child );
            }

            if ( childMetrics instanceof PackageMetrics ) {
                PackageMetrics packageMetrics = (PackageMetrics) childMetrics;
                files += packageMetrics.getFiles();
            }

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

        if ( parentMetrics instanceof FileMetrics ) {
            FileMetrics fileMetrics = (FileMetrics) parentMetrics;
            fileMetrics.setClasses( classes );
        }

        if ( parentMetrics instanceof PackageMetrics ) {
            PackageMetrics packageMetrics = (PackageMetrics) parentMetrics;
            packageMetrics.setFiles( files );
        }
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

                        List<generated.File> files = _files.get( _package );
                        file = files.remove( 0 );

                        if ( files.isEmpty() ) {
                            _packages.remove( 0 );
                            _files.remove( _package );
                        }
                    }

                    try {
                        inspectFile( file );
                    }
                    catch (Exception e) {
                        System.err.println( "Unable to inspect file: " + file.getName() );
                        e.printStackTrace();
                    }

                    synchronized (_packages) {
                        if ( file.getLine().isEmpty() ) {
                            _package.getFile().remove( file ); ////////////////////////
                        }
                        if ( !_packages.contains( _package ) && _package.getFile().isEmpty() ) {
                            project.getPackage().remove( _package ); ////////////////////////
                        }
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
