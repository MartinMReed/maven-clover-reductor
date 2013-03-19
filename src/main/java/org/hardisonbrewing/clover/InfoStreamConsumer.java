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
