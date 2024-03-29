package github.cjmcgraw.grpclb;

import com.google.common.util.concurrent.ListenableFuture;
import github.cjmcgraw.grpclb.nameresolvers.ExampleCustomNameResolverProvider;
import github.cjmcgraw.grpclb.test.CallRequest;
import github.cjmcgraw.grpclb.test.CallResponse;
import github.cjmcgraw.grpclb.test.TestServerGrpc;
import io.grpc.*;

import java.util.concurrent.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoadBalancerTestClient {
  private static final Logger log = LogManager.getLogger(LoadBalancerTestClient.class);
  private static final ExecutorService mainExecutor = Executors.newFixedThreadPool(10);
  private static final ExecutorService offloadExecutor = Executors.newFixedThreadPool(50);
  public static void main(String[] args) throws Exception {
    log.error("Starting script");
    NameResolverRegistry
            .getDefaultRegistry()
            .register(new ExampleCustomNameResolverProvider());

    String target = System.getenv("_LLB_TARGET");
    ManagedChannel channel = ManagedChannelBuilder
            .forTarget("example-custom://" + target)
            .defaultLoadBalancingPolicy("round_robin")
            .executor(mainExecutor)
            .offloadExecutor(offloadExecutor)
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(100, TimeUnit.MILLISECONDS)
            .keepAliveWithoutCalls(false)
            .usePlaintext()
            .build();

    CallHelper helper = new CallHelper(channel);
    try {
      helper.call("request - initial");
      for (int j = 0; j < 10_000; j++ ) {
        for (int i = 0; i < 20; i++) {
          helper.call("request - " + j + "," + i);
        }
        Thread.sleep(100);
      }
    } finally {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      mainExecutor.shutdownNow();
      mainExecutor.awaitTermination(5, TimeUnit.SECONDS);
      offloadExecutor.shutdownNow();
      offloadExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }
    log.info("Finished script");
  }

  public static class CallHelper {
    private final ManagedChannel channel;
    private final TestServerGrpc.TestServerFutureStub stub;
    private final String callerId;
    public CallHelper(ManagedChannel channel) {
      this.channel = channel;
      this.stub = TestServerGrpc
              .newFutureStub(channel);
      this.callerId = "123";

    }

    public String call(String callerName) throws InterruptedException {
      long start = System.nanoTime();
      CallRequest request = CallRequest
              .newBuilder()
              .setCallerId("123")
              .setCallerName(callerName)
              .build();

      try {
        ListenableFuture<CallResponse> futResponse = stub
                .withDeadlineAfter(100, TimeUnit.MILLISECONDS)
                .callServer(request);

        CallResponse response = futResponse.get(50L, TimeUnit.MILLISECONDS);
        String output = response.getMessage();
        long end = System.nanoTime();
        log.info("request took " + (end - start) * 1e-6 + " ms");
        return output;
      } catch (Exception e) {
        log.error(e);
        return "ERROR!!";
      }
    }
  }
}
