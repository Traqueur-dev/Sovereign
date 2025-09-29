package fr.traqueur.sovereign.api;

import fr.traqueur.sovereign.api.config.BackendConfig;
import fr.traqueur.sovereign.api.config.LeaderElectionConfig;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Service Provider Interface (SPI) for leader election backend implementations.
 * <p>
 * Implementations of this interface are discovered automatically via Java's {@link java.util.ServiceLoader}
 * mechanism. Each backend must:
 * </p>
 * <ul>
 *   <li>Implement this interface</li>
 *   <li>Provide a concrete {@link BackendConfig} type</li>
 *   <li>Register via {@code META-INF/services/fr.traqueur.sovereign.api.LeaderElectionProvider}</li>
 * </ul>
 * <p>
 * The provider is responsible for creating instances of the backend-specific
 * {@link LeaderElection} implementation.
 * </p>
 *
 * @param <T> the type of LeaderElection implementation this provider creates
 * @param <B> the type of BackendConfig this provider uses
 *
 * @see LeaderElectionFactory
 * @see BackendConfig
 * @see LeaderElection
 */
public interface LeaderElectionProvider<T extends LeaderElection, B extends BackendConfig> {

    /**
     * Returns the backend configuration class type.
     * <p>
     * This is used by the factory to match configurations with their corresponding providers.
     * </p>
     *
     * @return the Class object for the backend configuration type
     */
    Class<B> getType();

    /**
     * Creates a new leader election instance.
     *
     * @param instanceId the unique identifier for this election instance
     * @param scheduler  the executor service for scheduling election tasks
     * @param config     the configuration for this election instance
     * @return a new LeaderElection instance
     */
    T create(String instanceId, ScheduledExecutorService scheduler, LeaderElectionConfig<B> config);

}
