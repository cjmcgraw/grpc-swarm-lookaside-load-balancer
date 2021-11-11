package github.cjmcgraw.dnstester;

import github.cjmcgraw.dnstester.nameresolvers.MyCustomNameResolverProvider;
import io.grpc.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import github.cjmcgraw.dnstester.TestServerGrpc;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DnsTesterClient {
  private static final Logger log = LogManager.getLogger(DnsTesterClient.class);
  public static void main(String[] args) throws Exception {
    log.error("Starting script");
    NameResolverRegistry
            .getDefaultRegistry()
            .register(new MyCustomNameResolverProvider());

    ManagedChannel channel = ManagedChannelBuilder
        .forTarget("my-custom://localhost")
        .defaultLoadBalancingPolicy("round_robin")
        .usePlaintext()
        .build();

    DnsTester dnsTester = new DnsTester(channel);
    try {
      String response = dnsTester.call("request - initial");
      for (int i = 0; i < 15; i++ ) {
        response = dnsTester.call("request - " + i);
      }
    } finally {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
    log.info("Finished script");
  }

  static public class DnsTester {
    private final ManagedChannel channel;
    private final TestServerGrpc.TestServerBlockingStub stub;
    private final String callerId;
    public DnsTester(ManagedChannel channel) {
      this.channel = channel;
      this.stub = TestServerGrpc
              .newBlockingStub(channel);
      this.callerId = "123";
    }

    public String call(String callerName) {
      long start = System.nanoTime();
      CallRequest request = CallRequest.newBuilder()
              .setCallerId("123")
              .setCallerName(callerName)
              .build();

      try {
        CallResponse response = stub.callServer(request);
        String output = response.getMessage();
        long end = System.nanoTime();
        log.info("request took " + (end - start) * 1e-6 + " ms");
        return output;
      } catch (Exception e) {
        return null;
      }
    }
  }
}
