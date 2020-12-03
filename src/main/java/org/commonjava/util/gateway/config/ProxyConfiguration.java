package org.commonjava.util.gateway.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.Startup;
import io.vertx.core.json.JsonObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

@Startup
@ApplicationScoped
public class ProxyConfiguration
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private Retry retry;

    private Set<ServiceConfig> services = Collections.synchronizedSet( new HashSet<>() );

    public Set<ServiceConfig> getServices()
    {
        return services;
    }

    public Retry getRetry()
    {
        return retry;
    }

    @Override
    public String toString()
    {
        return "ProxyConfiguration{" + "retry=" + retry + ", services=" + services + '}';
    }

    @PostConstruct
    void init()
    {
        load( true );
        logger.info( "Proxy config, {}", this );
    }

    private static final String PROXY_YAML = "proxy.yaml";

    /**
     * Load proxy config from classpath resource (if init is true) and '${user.dir}/config/proxy.yaml'.
     */
    public void load( boolean init )
    {
        if ( init )
        {
            InputStream res = this.getClass().getClassLoader().getResourceAsStream( PROXY_YAML );
            if ( res != null )
            {
                logger.info( "Load from classpath, {}", PROXY_YAML );
                doLoad( res );
            }
        }

        loadFromFile();
    }

    private void loadFromFile()
    {
        String userDir = System.getProperty( "user.dir" ); // where the JVM was invoked
        File file = new File( userDir, "config/" + PROXY_YAML );
        if ( file.exists() )
        {
            logger.info( "Load from file, {}", file );
            try
            {
                doLoad( new FileInputStream( file ) );
            }
            catch ( FileNotFoundException e )
            {
                logger.error( "Load failed", e );
                return;
            }
        }
        else
        {
            logger.info( "Skip load, NO_SUCH_FILE, {}", file );
        }
    }

    private transient String md5Hex; // used to check whether the custom proxy.yaml has changed

    private void doLoad( InputStream res )
    {
        try
        {
            String str = IOUtils.toString( res, UTF_8 );
            String md5 = DigestUtils.md5Hex( str ).toUpperCase();
            if ( md5.equals( md5Hex ) )
            {
                logger.info( "Skip, NO_CHANGE" );
                return;
            }

            md5Hex = md5;
            Yaml yaml = new Yaml();
            Map<String, Object> obj = yaml.load( str );
            Map<String, Object> proxy = (Map) obj.get( "proxy" );

            JsonObject jsonObject = JsonObject.mapFrom( proxy );
            ProxyConfiguration parsed = jsonObject.mapTo( this.getClass() );
            logger.info( "Loaded: {}", parsed );

            if ( this.retry == null )
            {
                this.retry = parsed.retry;
            }
            else if ( parsed.retry != null )
            {
                this.retry.copyFrom( parsed.retry );
            }

            if ( parsed.services != null )
            {
                parsed.services.forEach( sv -> {
                    this.services.remove( sv ); // remove it first so the add can replace the old one
                    this.services.add( sv );
                } );
            }
        }
        catch ( IOException e )
        {
            logger.error( "Load failed", e );
        }
    }

    public static class ServiceConfig
    {
        public String host;

        public int port;

        public List<String> methods;

        @JsonProperty( "path-pattern" )
        public String pathPattern;

        @Override
        public boolean equals( Object o )
        {
            if ( this == o )
                return true;
            if ( o == null || getClass() != o.getClass() )
                return false;
            ServiceConfig that = (ServiceConfig) o;
            return port == that.port && host.equals( that.host );
        }

        @Override
        public int hashCode()
        {
            return Objects.hash( host, port );
        }

        @Override
        public String toString()
        {
            return "ServiceConfig{" + "host='" + host + '\'' + ", port=" + port + ", methods=" + methods
                            + ", pathPattern='" + pathPattern + '\'' + '}';
        }
    }

    public static class Retry
    {
        public volatile int count;

        public volatile long interval;

        @Override
        public String toString()
        {
            return "Retry{" + "count=" + count + ", interval=" + interval + '}';
        }

        public void copyFrom( Retry retry )
        {
            count = retry.count;
            interval = retry.interval;
        }
    }
}