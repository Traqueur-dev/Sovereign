package fr.traqueur.sovereign.api;

import fr.traqueur.sovereign.api.config.BackendConfig;
import fr.traqueur.sovereign.api.config.LeaderElectionConfig;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Factory class for creating leader election instances.
 * <p>
 * This factory uses Java's {@link ServiceLoader} mechanism to automatically discover
 * and register backend implementations at startup. Providers are registered once
 * and cached for efficient reuse.
 * </p>
 * <p>
 * The factory matches backend configurations to their corresponding providers
 * and creates instances with proper type safety.
 * </p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Configure backend
 * RedisElectionConfig redisConfig = RedisElectionConfig.builder()
 *     .redisCommands(commands)
 *     .build();
 *
 * // Configure election
 * LeaderElectionConfig<RedisElectionConfig> config = LeaderElectionConfig
 *     .configureFor(redisConfig)
 *     .leaderTtl(Duration.ofSeconds(30))
 *     .build();
 *
 * // Create election instance
 * LeaderElection election = LeaderElectionFactory.create("instance-1", scheduler, config);
 * }</pre>
 *
 * @see LeaderElectionProvider
 * @see LeaderElection
 * @see LeaderElectionConfig
 */
public class LeaderElectionFactory {

    /**
     * Map of registered providers, keyed by their backend configuration class.
     */
    private static final Map<Class<? extends BackendConfig>, LeaderElectionProvider<?, ?>> PROVIDERS = new ConcurrentHashMap<>();

    static {
        // Auto-discover and register all providers via ServiceLoader
        ServiceLoader.load(LeaderElectionProvider.class)
                .forEach(LeaderElectionFactory::register);
    }

    /**
     * Registers a leader election provider.
     * <p>
     * This method is called automatically during class initialization for all
     * providers discovered via ServiceLoader.
     * </p>
     *
     * @param <T>      the type of LeaderElection implementation
     * @param <B>      the type of BackendConfig
     * @param provider the provider to register
     */
    public static <T extends LeaderElection, B extends BackendConfig> void register(LeaderElectionProvider<T, B> provider) {
        PROVIDERS.put(provider.getType(), provider);
    }

    /**
     * Creates a new leader election instance.
     * <p>
     * The factory matches the provided configuration's backend type with a registered
     * provider and delegates instance creation to that provider.
     * </p>
     *
     * @param <T>        the type of LeaderElection implementation
     * @param <B>        the type of BackendConfig
     * @param instanceId the unique identifier for this election instance
     * @param scheduler  the executor service for scheduling election tasks
     * @param config     the configuration for this election instance
     * @return a new LeaderElection instance
     * @throws IllegalArgumentException if no provider is registered for the backend configuration type
     */
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