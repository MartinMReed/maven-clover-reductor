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

import org.codehaus.plexus.util.cli.StreamConsumer;

public class BlameStreamConsumer implements StreamConsumer {

    private final List<Long> revisions;

    public BlameStreamConsumer(List<Long> revisions) {

        this.revisions = revisions;
    }

    @Override
    public void consumeLine( String line ) {

        line = line.trim();
        String original = line;

        int indexOf = line.indexOf( ' ' );
        if ( indexOf == -1 ) {
            System.err.println( "Unable to parse line: [" + original + "]" );
            throw new IllegalStateException();
        }

        line = line.substring( 0, indexOf ).trim();

        long revision;

        try {
            revision = Long.parseLong( line );
        }
        catch (NumberFormatException e) {
            System.err.println( "Unable to parse line: [" + original + "]" );
            throw e;
        }

        revisions.add( revision );
    }
}
