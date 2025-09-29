package fr.traqueur.sovereign.impl.redis;

import fr.traqueur.sovereign.api.config.LeaderElectionConfig;
import fr.traqueur.sovereign.api.LeaderElectionProvider;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Service Provider Interface (SPI) implementation for Redis-based leader election.
 * <p>
 * This provider is automatically discovered via Java's {@link java.util.ServiceLoader}
 * mechanism and registered with {@link fr.traqueur.sovereign.api.LeaderElectionFactory}.
 * </p>
 * <p>
 * To use this provider, include the Redis module in your classpath. The provider
 * will be automatically loaded and available for creating Redis-based leader election
 * instances.
 * </p>
 * <p>
 * This provider is registered via the file:
 * {@code META-INF/services/fr.traqueur.sovereign.api.LeaderElectionProvider}
 * </p>
 *
 * @see RedisLeaderElection
 * @see RedisElectionConfig
 * @see fr.traqueur.sovereign.api.LeaderElectionProvider
 */
public record RedisLeaderElectionProvider() implements LeaderElectionProvider<RedisLeaderElection, RedisElectionConfig> {

    /**
     * Returns the configuration type handled by this provider.
     *
     * @return {@link RedisElectionConfig} class
     */
    @Override
    public Class<RedisElectionConfig> getType() {
        return RedisElectionConfig.class;
    }

    /**
     * Creates a new Redis-based leader election instance.
     *
     * @param instanceId the unique identifier for this election instance
     * @param scheduler  the executor service for scheduling election tasks
     * @param config     the configuration including Redis-specific settings
     * @return a new RedisLeaderElection instance
     */
    @Override
    public RedisLeaderElection create(String instanceId, ScheduledExecutorService scheduler, LeaderElectionConfig<RedisElectionConfig> config) {
        return new RedisLeaderElection(instanceId, config.additionalConfig().redisCommands(), scheduler, config);
    }
}
