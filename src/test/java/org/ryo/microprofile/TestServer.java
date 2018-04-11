package org.ryo.microprofile;

import java.util.Iterator;
import java.util.ServiceLoader;

import org.eclipse.microprofile.config.spi.ConfigSourceProvider;

public class TestServer {

    public static void main(String[] args) {
        ServiceLoader<ConfigSourceProvider> providerService = ServiceLoader.load(ConfigSourceProvider.class);
        Iterator<ConfigSourceProvider> providers = providerService.iterator();
        if (providers.hasNext()) {
            ConfigSourceProvider configSourceProvider = providers.next();
            configSourceProvider.getConfigSources(null);
        }
    }
}
