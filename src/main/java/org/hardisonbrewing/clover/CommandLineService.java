/**
 * Copyright (c) 2013 Martin M Reed
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hardisonbrewing.clover;

import java.util.List;

import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

public class CommandLineService {

    protected CommandLineService() {

        // do nothing
    }

    public static final Commandline build( List<String> cmd ) throws CommandLineException {

        Commandline commandLine = new Commandline();
        commandLine.setExecutable( cmd.get( 0 ) );

        for (int i = 1; i < cmd.size(); i++) {
            commandLine.createArg().setValue( cmd.get( i ) );
        }

        return commandLine;
    }

    public static final int execute( List<String> cmd, StreamConsumer systemOut, StreamConsumer systemErr ) throws CommandLineException {

        return execute( build( cmd ), systemOut, systemErr );
    }

    public static final int execute( Commandline commandLine, StreamConsumer systemOut, StreamConsumer systemErr ) throws CommandLineException {

        return CommandLineUtils.executeCommandLine( commandLine, systemOut, systemErr );
    }
}
