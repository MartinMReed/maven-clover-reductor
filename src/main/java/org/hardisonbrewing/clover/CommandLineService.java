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
