package github.cjmcgraw.dnstester.nameresolvers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;


public class MyCustomNameResolverProvider extends NameResolverProvider {
    private static final Logger log = LogManager.getLogger(MyCustomNameResolverProvider.class);
    private static final Logger httpResponseLog = LogManager.getLogger("HttpResponseProcessor");
    private static final ObjectMapper mapper = new ObjectMapper();
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
            actualUri = new URI("http://" + targetUri.getHost() + ":80");
        } catch (URISyntaxException exception) {
            throw new RuntimeException(exception);
        }

        Executor executor = args.getOffloadExecutor();
        if (executor == null) {
            throw new IllegalArgumentException("In order to use the custom name resolver, you must provide an offload executor!");
        }
        return new HttpRequestNameResolver(
                args.getOffloadExecutor(),
                actualUri,
                this::processResponseIntoTargets
        );
    }

    @Override
    public String getDefaultScheme() {
        log.error("getting scheme");
        return SCHEME;
    }

    private Set<String> processResponseIntoTargets(HttpResponse<String> response) {
        try {
            LookasideResponse resp = mapper.readValue(
                    response.body().strip(),
                    LookasideResponse.class
            );
            return resp
                    .validServers
                    .stream()
                    .map(server -> server.addr + ":" + server.port)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            httpResponseLog.error("HttpResponseProcessing: failed to parse http response into targets!" + response);
            httpResponseLog.error(e);
            throw new RuntimeException(e);
        }
    }


    static class ValidServer {
        @JsonProperty("name")
        private String name;
        @JsonProperty("host")
        private String host;
        @JsonProperty("addr")
        private String addr;
        @JsonProperty("port")
        private int port;
    }

    static class LookasideResponse {
        @JsonProperty("valid_servers")
        private Set<ValidServer> validServers;
    }
}
