package github.cjmcgraw.dnstester.nameresolvers;

import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;


public class MyCustomNameResolverProvider extends NameResolverProvider {
    private static final Logger log = LogManager.getLogger(MyCustomNameResolverProvider.class);
    public static final String SCHEME = "my-custom";

    @Override
    protected boolean isAvailable() {
        log.info("checking isAvailable");
        return true;
    }

    @Override
    protected int priority() {
        return 0;
    }

    @Override
    public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
        log.info("creating new name resolver");
        return null;
    }

    @Override
    public String getDefaultScheme() {
        return SCHEME;
    }
}
