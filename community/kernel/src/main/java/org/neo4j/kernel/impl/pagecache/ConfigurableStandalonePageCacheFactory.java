/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
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
package org.neo4j.kernel.impl.pagecache;

import java.time.ZoneId;

import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.io.pagecache.tracing.cursor.context.VersionContextSupplier;
import org.neo4j.logging.FormattedLogProvider;
import org.neo4j.scheduler.JobScheduler;
import org.neo4j.time.Clocks;

import static org.neo4j.io.pagecache.tracing.cursor.context.EmptyVersionContextSupplier.EMPTY;

/*
 * This class is an helper to allow to construct properly a page cache in the few places we need it without all
 * the graph database stuff, e.g., various store dump programs.
 *
 * All other places where a "proper" page cache is available, e.g. in store migration, should have that one injected.
 * And tests should use the ConfigurablePageCacheRule.
 */
public final class ConfigurableStandalonePageCacheFactory
{
    private ConfigurableStandalonePageCacheFactory()
    {
    }

    public static PageCache createPageCache( FileSystemAbstraction fileSystem, JobScheduler jobScheduler, PageCacheTracer pageCacheTracer )
    {
        return createPageCache( fileSystem, pageCacheTracer, Config.defaults(), EMPTY, jobScheduler );
    }

    public static PageCache createPageCache( FileSystemAbstraction fileSystem, Config config, JobScheduler jobScheduler, PageCacheTracer pageCacheTracer )
    {
        return createPageCache( fileSystem, pageCacheTracer, config, EMPTY, jobScheduler );
    }

    /**
     * Create page cache
     * @param fileSystem file system that page cache will be based on
     * @param pageCacheTracer global page cache tracer
     * @param config page cache configuration
     * @param versionContextSupplier version context supplier
     * @param jobScheduler page cache job scheduler
     * @return created page cache instance
     */
    public static PageCache createPageCache( FileSystemAbstraction fileSystem, PageCacheTracer pageCacheTracer, Config config,
            VersionContextSupplier versionContextSupplier, JobScheduler jobScheduler )
    {
        config.setIfNotSet( GraphDatabaseSettings.pagecache_memory, "8M" );
        ZoneId logTimeZone = config.get( GraphDatabaseSettings.db_timezone ).getZoneId();
        FormattedLogProvider logProvider = FormattedLogProvider.withZoneId( logTimeZone ).toOutputStream( System.err );
        ConfiguringPageCacheFactory pageCacheFactory = new ConfiguringPageCacheFactory(
                fileSystem, config, pageCacheTracer, logProvider.getLog( PageCache.class ), versionContextSupplier, jobScheduler, Clocks.nanoClock() );
        return pageCacheFactory.getOrCreatePageCache();
    }
}
