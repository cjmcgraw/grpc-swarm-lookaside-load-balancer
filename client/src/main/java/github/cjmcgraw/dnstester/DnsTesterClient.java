package github.cjmcgraw.dnstester;

import com.google.common.util.concurrent.ListenableFuture;
import github.cjmcgraw.dnstester.nameresolvers.MyCustomNameResolverProvider;
import io.grpc.*;

import java.util.concurrent.*;
import java.util.logging.Level;

import github.cjmcgraw.dnstester.TestServerGrpc;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DnsTesterClient {
  private static final Logger log = LogManager.getLogger(DnsTesterClient.class);
  private static final ExecutorService mainExecutor = Executors.newFixedThreadPool(10);
  private static final ExecutorService offloadExecutor = Executors.newFixedThreadPool(50);
  public static void main(String[] args) throws Exception {
    log.error("Starting script");
    NameResolverRegistry
            .getDefaultRegistry()
            .register(new MyCustomNameResolverProvider());

    String target = System.getenv("_LLB_TARGET");
    ManagedChannel channel = ManagedChannelBuilder
            .forTarget("my-custom://" + target)
            .defaultLoadBalancingPolicy("round_robin")
            .executor(mainExecutor)
            .offloadExecutor(offloadExecutor)
            .usePlaintext()
            .build();

    DnsTester dnsTester = new DnsTester(channel);
    try {
      String response = dnsTester.call("request - initial");
      for (int j = 0; j < 15; j++ ) {
        for (int i = 0; i < 15; i++) {
          response = dnsTester.call("request - " + i);
        }
        Thread.sleep(500);
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

  public static class DnsTester {
    private final ManagedChannel channel;
    private final TestServerGrpc.TestServerFutureStub stub;
    private final String callerId;
    public DnsTester(ManagedChannel channel) {
      this.channel = channel;
      this.stub = TestServerGrpc
              .newFutureStub(channel);
              //.withDeadlineAfter(10, TimeUnit.SECONDS);
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
                .callServer(request);

        CallResponse response = futResponse.get(200L, TimeUnit.MILLISECONDS);
        String output = response.getMessage();
        long end = System.nanoTime();
        log.error("request took " + (end - start) * 1e-6 + " ms");
        return output;
      } catch (Exception e) {
        log.error(e);
        return "ERROR!!";
      }
    }
  }
}
