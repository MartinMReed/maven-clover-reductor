/**
 * Copyright (c) 2010-2011 Martin M Reed
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
package org.hardisonbrewing.jaxb;

import generated.ObjectFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.codehaus.plexus.util.IOUtil;

public class JAXB {

    private static Hashtable<Class<?>, JAXBContext> jaxbContexts = new Hashtable<Class<?>, JAXBContext>();

    protected JAXB() {

        // do nothing
    }

    public static <T> T unmarshal( String xml, Class<T> clazz ) throws JAXBException {

        return unmarshal( xml.getBytes(), clazz );
    }

    public static <T> T unmarshal( byte[] bytes, Class<T> clazz ) throws JAXBException {

        return unmarshal( new ByteArrayInputStream( bytes ), clazz );
    }

    public static <T> T unmarshal( File file, Class<T> clazz ) throws JAXBException {

        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream( file );
            return unmarshal( inputStream, clazz );
        }
        catch (Exception e) {
            throw new JAXBException( e );
        }
        finally {
            IOUtil.close( inputStream );
        }
    }

    public static <T> T unmarshal( InputStream inputStream, Class<T> clazz ) throws JAXBException {

        try {
            Unmarshaller unmarshaller = getUnmarshaller( clazz );
            Object object = unmarshaller.unmarshal( inputStream );
            if ( object instanceof JAXBElement ) {
                JAXBElement<T> jaxbElement = (JAXBElement<T>) object;
                return (T) jaxbElement.getValue();
            }
            return (T) object;
        }
        catch (JAXBException e) {
            throw e;
        }
    }

    public static String marshal( Object object ) throws JAXBException {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        marshal( outputStream, object );
        return outputStream.toString();
    }

    public static void marshal( File file, Object object ) throws JAXBException {

        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream( file );
            marshal( outputStream, object );
        }
        catch (Exception e) {
            throw new JAXBException( e );
        }
        finally {
            IOUtil.close( outputStream );
        }
    }

    public static void marshal( OutputStream outputStream, Object object ) throws JAXBException {

        try {
            Marshaller marshaller = getMarshaller( object.getClass() );
            marshaller.setProperty( "jaxb.formatted.output", Boolean.TRUE );
            marshaller.marshal( object, outputStream );
        }
        catch (JAXBException e) {
            throw e;
        }
    }

    public static JAXBContext getJAXBContext( Class<?> clazz ) throws JAXBException {

        JAXBContext jaxbContext = jaxbContexts.get( clazz );
        if ( jaxbContext != null ) {
            return jaxbContext;
        }
        jaxbContext = JAXBContext.newInstance( ObjectFactory.class );
        jaxbContexts.put( clazz, jaxbContext );
        return jaxbContext;
    }

    public static Unmarshaller getUnmarshaller( Class<?> clazz ) throws JAXBException {

        return getJAXBContext( clazz ).createUnmarshaller();
    }

    public static Marshaller getMarshaller( Class<?> clazz ) throws JAXBException {

        return getJAXBContext( clazz ).createMarshaller();
    }
}
