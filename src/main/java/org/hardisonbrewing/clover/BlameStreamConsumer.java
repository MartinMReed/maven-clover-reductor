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
        revisions.add( Long.parseLong( line ) );
    }
}
