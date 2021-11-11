package github.cjmcgraw.dnstester.nameresolvers;

import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Executor;


public class MyCustomNameResolverProvider extends NameResolverProvider {
    private static final Logger log = LogManager.getLogger(MyCustomNameResolverProvider.class);
    public static final String SCHEME = "my-custom";

    @Override
    protected boolean isAvailable() {
        log.error("checking isAvailable");
        return true;
    }

    @Override
    protected int priority() {
        return 0;
    }

    @Override
    public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
        URI actualUri;
        try {
            actualUri = new URI(targetUri.toString().replace(SCHEME, "http"));
        } catch (URISyntaxException exception) {
            throw new RuntimeException(exception);
        }

        Executor executor = args.getOffloadExecutor();
        if (executor == null) {
            throw new IllegalArgumentException("In order to use the custom name resolver, you must provide an offload executor!");
        }
        return new MyCustomNameResolver(
                actualUri,
                args.getSynchronizationContext(),
                args.getServiceConfigParser(),
                args.getOffloadExecutor()
        );
    }

    @Override
    public String getDefaultScheme() {
        log.error("getting scheme");
        return SCHEME;
    }
}
