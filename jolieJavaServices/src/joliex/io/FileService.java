/***************************************************************************
 *   Copyright (C) by Fabrizio Montesi                                     *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Library General Public License as       *
 *   published by the Free Software Foundation; either version 2 of the    *
 *   License, or (at your option) any later version.                       *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU Library General Public     *
 *   License along with this program; if not, write to the                 *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 *                                                                         *
 *   For details about the authors of this software, see the AUTHORS file. *
 ***************************************************************************/

package joliex.io;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;

import java.util.regex.Pattern;
import javax.activation.FileTypeMap;
import jolie.net.CommMessage;
import jolie.runtime.ByteArray;
import jolie.runtime.FaultException;
import jolie.runtime.JavaService;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import jolie.runtime.embedding.RequestResponse;

public class FileService extends JavaService
{
	final private FileTypeMap fileTypeMap = FileTypeMap.getDefaultFileTypeMap();

	private static void readBase64IntoValue( File file, Value value )
		throws IOException
	{
		FileInputStream fis = new FileInputStream( file );
		byte[] buffer = new byte[ (int)file.length() ];
		fis.read( buffer );
		fis.close();
		sun.misc.BASE64Encoder encoder = new sun.misc.BASE64Encoder();
		value.setValue( encoder.encode( buffer ) );
	}
	
	private static void readBinaryIntoValue( File file, Value value )
		throws IOException
	{
		FileInputStream fis = new FileInputStream( file );
		byte[] buffer = new byte[ (int)file.length() ];
		fis.read( buffer );
		fis.close();
		value.setValue( new ByteArray( buffer ) );
	}
	
	private static void readTextIntoValue( File file, Value value )
		throws IOException
	{
		String separator = System.getProperty( "line.separator" );
		StringBuffer buffer = new StringBuffer();
		String line;
		BufferedReader reader = new BufferedReader( new FileReader( file ) );
		while( (line=reader.readLine()) != null ) {
			buffer.append( line );
			buffer.append( separator );
		}
		reader.close();
		value.setValue( buffer.toString() );
	}
	
	public Value readFile( Value request )
		throws FaultException
	{
		Value filenameValue = request.getFirstChild( "filename" );

		Value retValue = Value.create();
		String format = request.getFirstChild( "format" ).strValue();
		try {
			if ( "base64".equals( format ) ) {
				readBase64IntoValue( new File( filenameValue.strValue() ), retValue );
			} else if ( "binary".equals( format ) ) {
				readBinaryIntoValue( new File( filenameValue.strValue() ), retValue );
			} else {
				readTextIntoValue( new File( filenameValue.strValue() ), retValue );
			}			
		} catch( FileNotFoundException e ) {
			throw new FaultException( "FileNotFound" );
		} catch( IOException e ) {
			throw new FaultException( e );
		}
		return retValue;
	}

	public String getMimeType( String filename )
		throws FaultException
	{
		File file = new File( filename );
		if ( file.exists() == false ) {
			throw new FaultException( "FileNotFound" );
		}
		return fileTypeMap.getContentType( file );
	}
	
	public String getServiceDirectory()
	{
		String dir = interpreter().programFile().getParent();
		if ( dir == null || dir.isEmpty() ) {
			dir = ".";
		}
		
		return dir;
	}
	
	public String getFileSeparator()
	{
		return jolie.lang.Constants.fileSeparator;
	}

	@RequestResponse
	public void writeFile( Value request )
		throws FaultException
	{
		Value filenameValue = request.getFirstChild( "filename" );
		if ( !filenameValue.isString() ) {
			throw new FaultException( "FileNotFound" );
		}
		Value content = request.getFirstChild( "content" );
		try {
			if ( content.isByteArray() ) {
				FileOutputStream os = new FileOutputStream( filenameValue.strValue() );
				os.write( ((ByteArray)content.valueObject()).getBytes() );
				os.flush();
				os.close();
			} else {
				FileWriter writer = new FileWriter( filenameValue.strValue() );
				writer.write( content.strValue() );
				writer.flush();
				writer.close();
			}
		} catch( IOException e ) {
			throw new FaultException( "IOException", e );
		}
	}
	
	public Boolean delete( CommMessage request )
	{
		String filename = request.value().strValue();
		boolean isRegex = request.value().getFirstChild( "isRegex" ).intValue() > 0;
		boolean ret = true;
		if ( isRegex ) {
			File dir = new File( filename ).getAbsoluteFile().getParentFile();
			String[] files = dir.list( new ListFilter( filename ) );
			if ( files != null ) {
				for( String file : files ) {
					new File( file ).delete();
				}
			}
		} else {
			if ( new File( filename ).delete() == false ) {
				ret = false;
			}
		}
		return ret;
	}

	@RequestResponse
	public void rename( Value request )
		throws FaultException
	{
		String filename = request.getFirstChild( "filename" ).strValue();
		String toFilename = request.getFirstChild( "to" ).strValue();
		if ( new File( filename ).renameTo( new File( toFilename ) ) == false ) {
			throw new FaultException( "IOException" );
		}
	}
	
	public Value list( Value request )
	{
		File dir = new File( request.getFirstChild( "directory" ).strValue() );
		String[] files = dir.list( new ListFilter( request.getFirstChild( "regex" ).strValue() ) );
		Value response = Value.create();
		if ( files != null ) {
			ValueVector results = response.getChildren( "result" );
			for( String file : files ) {
				results.add( Value.create( file ) );
			}
		}
		return response;
	}
	
	private static class ListFilter implements FilenameFilter
	{
		final private Pattern pattern;
		public ListFilter( String regex )
		{
			this.pattern = Pattern.compile( regex );
		}

		public boolean accept( File file, String name )
		{
			return pattern.matcher( name ).matches();
		}
	}
}
