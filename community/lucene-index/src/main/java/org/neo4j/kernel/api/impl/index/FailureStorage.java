/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.api.impl.index;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import static java.lang.System.lineSeparator;

import static org.neo4j.helpers.collection.IteratorUtil.asIterable;

public class FailureStorage
{
    private final FolderLayout folderLayout;

    public FailureStorage( FolderLayout folderLayout )
    {
        this.folderLayout = folderLayout;
    }
    
    public void store( long indexId, String failure ) throws IOException
    {
        File failureFile = failureFile( indexId );
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter( new FileOutputStream( failureFile ), "utf-8" ) );
        try
        {
            writer.write( failure );
        }
        finally
        {
            writer.close();
        }
    }

    private File failureFile( long indexId )
    {
        File folder = folderLayout.getFolder( indexId );
        folder.mkdirs();
        File failureFile = new File( folder, "failure" );
        return failureFile;
    }
    
    /**
     * @return the failure, if any. Otherwise {@code null} marking no failure.
     */
    public String load( long indexId )
    {
        File failureFile = failureFile( indexId );
        if ( !failureFile.exists() )
        {
            return null;
        }
        
        StringBuilder result = new StringBuilder();
        int count = 0;
        for ( String line : asIterable( failureFile, "utf-8" ) )
        {
            if ( count++ > 0 )
            {
                result.append( lineSeparator() );
            }
            result.append( line );
        }
        return result.toString();
    }

    public void clear( long indexId )
    {
        failureFile( indexId ).delete();
    }
}
