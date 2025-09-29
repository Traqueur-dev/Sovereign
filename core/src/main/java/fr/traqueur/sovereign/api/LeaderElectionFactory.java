package fr.traqueur.sovereign.api;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

public class LeaderElectionFactory {
    
    private static final Map<Class<? extends BackendConfig>, LeaderElectionProvider<?, ?>> PROVIDERS = new ConcurrentHashMap<>();

    static {
        ServiceLoader.load(LeaderElectionProvider.class)
                .forEach(LeaderElectionFactory::register);
    }

    private static <T extends LeaderElection, B extends BackendConfig> void register(LeaderElectionProvider<T, B> provider) {
        PROVIDERS.put(provider.getType(), provider);
    }

    @SuppressWarnings("unchecked")
    public static <T extends LeaderElection, B extends BackendConfig> T create(
            String instanceId,
            ScheduledExecutorService scheduler,
            LeaderElectionConfig<B> config) {
        
        LeaderElectionProvider<T, B> provider = 
            (LeaderElectionProvider<T, B>) PROVIDERS.get(config.additionalConfig().getClass());
            
        if (provider == null) {
            throw new IllegalArgumentException("Unknown backend: " + config.additionalConfig().getClass().getSimpleName());
        }
        
        return provider.create(instanceId, scheduler, config);
    }
}