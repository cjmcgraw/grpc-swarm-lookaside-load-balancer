package github.cjmcgraw.grpc.loadbalancers.nameresolvers;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.Status;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HttpRequestNameResolver extends NameResolver {
    private static final Logger log = LogManager.getLogger(HttpRequestNameResolver.class);
    private static final Duration REFRESH_TIME = Duration.ofMinutes(5);
    private static final Duration MAX_WAIT_FOR_COMPLETION_TIME = Duration.ofSeconds(1);
    private static final Duration HTTP_TIMEOUT = Duration.ofMillis(100);
    private final HttpClient httpClient;
    private final HttpRequest httpRequest;
    private final Executor executor;
    private final URI target;

    private final ScheduledExecutorService refreshExecutor;
    private final Function<HttpResponse<String>, Set<String>> parseResponse;
    private Set<String> knownTargets;
    private CompletableFuture<Void> pendingRequest;
    private long timeOfLastCache;
    private boolean shouldClearCacheWhenAvailable = false;
    private Listener2 lastKnownListener;

    HttpRequestNameResolver(Executor executor, URI target, Function<HttpResponse<String>, Set<String>> parseResponse) {
        this.parseResponse = parseResponse;
        this.target = target;
        this.knownTargets = new HashSet<>();
        this.timeOfLastCache = 0L;
        this.executor = executor;

        httpClient = HttpClient
                .newBuilder()
                .executor(executor)
                .connectTimeout(HTTP_TIMEOUT)
                .build();

        httpRequest = HttpRequest
                .newBuilder()
                .GET()
                .uri(target)
                .build();

        refreshExecutor = Executors.newSingleThreadScheduledExecutor();
        refreshExecutor.scheduleAtFixedRate(
                this::refresh,
                REFRESH_TIME.toMillis(),
                REFRESH_TIME.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void start(NameResolver.Listener2 listener) {
        resolve();
        this.lastKnownListener = listener;
        executor.execute(
                () -> {
                    try {
                        log.error("NameResolver: attempting new listener with known targets size=" + knownTargets.size());
                        if (knownTargets.isEmpty()) {
                            if (pendingRequest != null && (!pendingRequest.isDone()
                                    || pendingRequest.isCompletedExceptionally())) {
                                pendingRequest.join();
                            }
                            if (knownTargets.isEmpty()) {
                                log.error("NameResolver: known targets not populated yet!");
                                throw new RuntimeException("Have not resolved known targets yet");
                            }
                        }
                        List<EquivalentAddressGroup> addresses = knownTargets
                                .stream()
                                .map(this::targetStringToSocket)
                                .map(EquivalentAddressGroup::new)
                                .collect(Collectors.toList());

                        listener.onResult(
                                NameResolver.ResolutionResult
                                        .newBuilder()
                                        .setAddresses(addresses)
                                        .build()
                        );
                        log.error("NameResolver: Successfully updated listener");
                    } catch (Exception e) {
                        log.error("NameResolver: exception when building out for known targets!");
                        log.error(e);
                        Status status = Status
                                .UNAVAILABLE
                                .withDescription("exception=" + e.getMessage())
                                .withCause(e);
                        listener.onError(status);
                    }
                }
        );
    }

    @Override
    public void refresh() {
        shouldClearCacheWhenAvailable = true;
        resolve();
        if (lastKnownListener != null) {
            start(lastKnownListener);
        }
    }

    @Override
    public void shutdown() {
        refreshExecutor.shutdownNow();
    }

    @Override
    public String getServiceAuthority() {
        // lol what does this do?
        return target.getAuthority();
    }

    public void resolve() {
        if (!shouldAttemptResolution()) {
            return;
        }
        log.error("NameResolver resolve triggered!");
        Executor selectedExecutor = executor;
        timeOfLastCache = System.currentTimeMillis();
        shouldClearCacheWhenAvailable = false;
        pendingRequest = httpClient
                .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .orTimeout(250, TimeUnit.MILLISECONDS)
                .thenAcceptAsync(
                        response -> {
                            try {
                                Set<String> validTargets = parseResponse.apply(response);
                                if (!validTargets.isEmpty()) {
                                    knownTargets = validTargets;
                                }
                                log.info("NameResolver: resolve successful: " + knownTargets);
                            } catch (Exception e) {
                                log.error(e);
                                throw new RuntimeException(e);
                            }

                        },
                        selectedExecutor
                );
    }

    private InetSocketAddress targetStringToSocket(String target) {
        String[] strs = target.split(":", 2);
        if (strs.length != 2) {
            throw new UnknownFormatConversionException(
                    "Expected target to have a separator of addr:port. Found target=" + target
            );
        }
        String addr = strs[0];
        int port = Integer.parseInt(strs[1]);
        log.error("creating new connection for " + target);
        return new InetSocketAddress(addr, port);
    }

    private boolean shouldAttemptResolution() {
        long timeSinceLastUpdate = System.currentTimeMillis() - timeOfLastCache;

        if (shouldClearCacheWhenAvailable) {
            return true;
        }

        if (knownTargets.isEmpty()) {
            if (timeSinceLastUpdate > (2 * MAX_WAIT_FOR_COMPLETION_TIME.toMillis())) {
                return true;
            }
        }

        if (timeSinceLastUpdate > REFRESH_TIME.toMillis()) {
            return true;
        }

        return false;
    }
}
