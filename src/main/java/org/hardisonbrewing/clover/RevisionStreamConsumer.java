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

import org.codehaus.plexus.util.cli.StreamConsumer;

public class RevisionStreamConsumer implements StreamConsumer {

    private long revision;

    @Override
    public void consumeLine( String line ) {

        line = line.trim();

        int lastIndexOf = line.lastIndexOf( ' ' );
        if ( lastIndexOf == -1 ) {
            return;
        }

        line = line.substring( lastIndexOf + 1, line.length() - 1 ).trim();
        revision = Long.parseLong( line );
    }

    public long getRevision() {

        return revision;
    }
}
