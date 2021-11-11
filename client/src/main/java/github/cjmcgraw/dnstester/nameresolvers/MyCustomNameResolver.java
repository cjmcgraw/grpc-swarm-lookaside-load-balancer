package github.cjmcgraw.dnstester.nameresolvers;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.grpc.NameResolver;
import io.grpc.SynchronizationContext;
import io.grpc.netty.shaded.io.netty.util.concurrent.CompleteFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.ConnectionPendingException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class MyCustomNameResolver extends NameResolver {
    private static final ObjectMapper mapper = new ObjectMapper();

    static class ValidServer {
        @JsonProperty("name")
        private String name;
        @JsonProperty("host")
        private String host;
        @JsonProperty("port")
        private int port;
    }

    static class LookasideResponse {
        @JsonProperty("valid_servers")
       private Set<ValidServer> validServers;
    }

    private static final Logger log = LogManager.getLogger(MyCustomNameResolver.class);
    private static final Duration REFRESH_TIME = Duration.ofMinutes(15);
    private static final Duration MAX_WAIT_FOR_COMPLETION_TIME = Duration.ofSeconds(5);
    private static final Duration HTTP_TIMEOUT = Duration.ofMillis(500);
    private final HttpClient httpClient;
    private final HttpRequest httpRequest;
    private final SynchronizationContext syncContext;
    private final ServiceConfigParser serviceConfigParser;
    private final Executor executor;
    private final URI target;

    private long timeOfLastCache;
    private Set<ValidServer> knownServers;
    private CompletableFuture<Void> pendingRequest;

    MyCustomNameResolver(
            URI target,
            SynchronizationContext syncContext,
            ServiceConfigParser serviceConfigParser,
            Executor executor) {
        this.target = target;
        this.knownServers = new HashSet<>();
        this.timeOfLastCache = 0L;
        this.syncContext = syncContext;
        this.serviceConfigParser = serviceConfigParser;
        this.executor = executor;

        httpClient = HttpClient.newBuilder()
                .executor(executor)
                .connectTimeout(HTTP_TIMEOUT)
                .build();

        httpRequest = HttpRequest
                .newBuilder()
                .GET()
                .uri(target)
                .build();
    }

    public void resolve() {
        if (!shouldAttemptResolution()) {
            return;
        }
        timeOfLastCache = System.currentTimeMillis();
        pendingRequest = httpClient
                .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .orTimeout(250, TimeUnit.MILLISECONDS)
                .thenAcceptAsync(
                        response -> {
                            try {
                                LookasideResponse resp = mapper.readValue(
                                        response.body().strip(),
                                        LookasideResponse.class
                                );
                                if (!resp.validServers.isEmpty()) {
                                    knownServers = resp.validServers;
                                }
                            } catch (Exception e) {
                                log.error(e);
                            }
                        },
                        executor
                );
    }

    private boolean shouldAttemptResolution() {
        long timeSinceLastUpdate = System.currentTimeMillis() - timeOfLastCache;

        if (knownServers.isEmpty()) {
            if (timeSinceLastUpdate > (2 * MAX_WAIT_FOR_COMPLETION_TIME.toMillis())) {
                return true;
            }
        }

        if (timeSinceLastUpdate > REFRESH_TIME.toMillis()) {
            return true;
        }

        return false;
    }



    @Override
    public String getServiceAuthority() {
        return target.getAuthority();
    }

    @Override
    public void start(Listener listener) {
        resolve();
        pendingRequest.join();
        if (knownServers.isEmpty()) {
            throw new ConnectionPendingException();
        }
    }

    @Override
    public void start(Listener2 listener) {
        resolve();
        pendingRequest.join();
        if (knownServers.isEmpty()) {
            throw new ConnectionPendingException();
        }
    }

    @Override
    public void refresh() {
        resolve();
    }

    @Override
    public void shutdown() {

    }
}