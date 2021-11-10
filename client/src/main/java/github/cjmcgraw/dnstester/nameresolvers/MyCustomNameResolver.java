package github.cjmcgraw.dnstester.nameresolvers;

import io.grpc.NameResolver;

public class MyCustomNameResolver extends NameResolver {
    @Override
    public String getServiceAuthority() {
        return null;
    }

    @Override
    public void shutdown() {

    }
}
