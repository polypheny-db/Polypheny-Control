/*
 * Copyright 2017-2021 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.control.control;


import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.Arrays;
import java.util.LinkedList;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class PolyphenyDbProcessBuilder {

    private File workingDir;
    private File javaExecutable;
    private String[] javaOptions;
    private File pdbmsJarFile;
    private String classPath;
    private String mainClass;
    private String[] arguments;
    private File logFile;
    private boolean logFileAppend;
    private File errFile;
    private boolean errFileAppend;


    private PolyphenyDbProcessBuilder() {
    }


    static PolyphenyDbProcessBuilder builder() {
        return new PolyphenyDbProcessBuilder();
    }


    PolyphenyDbProcessBuilder withWorkingDir( final File workingDir ) {
        this.workingDir = workingDir;
        return this;
    }


    PolyphenyDbProcessBuilder withJavaExecutable( final File javaExecutable ) {
        this.javaExecutable = javaExecutable;
        return this;
    }


    PolyphenyDbProcessBuilder withJavaOptions( final String... javaOptions ) {
        this.javaOptions = javaOptions;
        return this;
    }


    PolyphenyDbProcessBuilder withPolyphenyDbmsJar( final File polyphenyDbmsJar ) {
        this.pdbmsJarFile = polyphenyDbmsJar;
        return this;
    }


    PolyphenyDbProcessBuilder withClassPath( final String... classPath ) {
        this.classPath = String.join( File.pathSeparator, classPath );
        return this;
    }


    PolyphenyDbProcessBuilder withMainClass( final String mainClass ) {
        this.mainClass = mainClass;
        return this;
    }


    PolyphenyDbProcessBuilder withArguments( final String... arguments ) {
        this.arguments = arguments;
        return this;
    }


    PolyphenyDbProcessBuilder withLogFile( final File logFile, final boolean append ) {
        this.logFile = logFile;
        this.logFileAppend = append;
        return this;
    }


    PolyphenyDbProcessBuilder withErrFile( final File errFile, final boolean append ) {
        this.errFile = errFile;
        this.errFileAppend = append;
        return this;
    }


    PolyphenyDbProcess start() throws IOException {

        if ( javaExecutable.exists() == false ) {
            throw new RuntimeException( "The java executable seems not to exist... How did you start this application?!" );
        }

        if ( workingDir.exists() == false ) {
            if ( workingDir.mkdirs() == false ) {
                throw new RuntimeException( "Could not create the folders for " + workingDir.getAbsolutePath() );
            }
        }

        final LinkedList<String> command = new LinkedList<>();

        command.addFirst( javaExecutable.getAbsolutePath() );
        command.addAll( Arrays.asList( javaOptions ) );
        if ( pdbmsJarFile != null && pdbmsJarFile.exists() ) {
            command.add( pdbmsJarFile.getAbsolutePath() );
        } else {
            command.add( "-classpath" );
            // The following OS specific stuff is maybe not needed. However, we should check spaces in the classpath which may require the following code...:
            /*if ( SystemUtils.IS_OS_WINDOWS ) {
                command.add( "\"" + classPath + "\"" );
            } else {
                command.add( classPath.replaceAll( " ", "\\ " ) );
            }*/
            command.add( classPath );
            command.add( mainClass );
        }
        if ( arguments.length > 0 ) {
            command.addAll( Arrays.asList( arguments ) );
        }

        log.info( "> Executing \"{}\"", String.join( " ", command ) );

        ProcessBuilder builder = new ProcessBuilder();
        if ( logFile == null ) {
            builder.redirectOutput( Redirect.PIPE );
        } else if ( logFileAppend ) {
            builder.redirectOutput( Redirect.appendTo( logFile ) );
        } else {
            builder.redirectOutput( Redirect.to( logFile ) );
        }
        if ( errFile == null ) {
            builder.redirectError( Redirect.PIPE );
        } else if ( errFileAppend ) {
            builder.redirectError( Redirect.appendTo( errFile ) );
        } else {
            builder.redirectError( Redirect.to( errFile ) );
        }
        builder.command( command );
        builder.directory( workingDir );

        return PolyphenyDbProcess.createFromProcess( builder.start() );
    }
}
