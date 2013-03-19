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

import java.util.Properties;

import org.codehaus.plexus.util.cli.StreamConsumer;

public class InfoStreamConsumer implements StreamConsumer {

    private final Properties properties;

    public InfoStreamConsumer(Properties properties) {

        this.properties = properties;
    }

    @Override
    public void consumeLine( String line ) {

        line = line.trim();

        int indexOf = line.indexOf( ':' );
        if ( indexOf == -1 ) {
            return;
        }

        String key = line.substring( 0, indexOf ).trim();
        String value = line.substring( indexOf + 1 ).trim();
        properties.put( key, value );
    }
}
