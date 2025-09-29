package fr.traqueur.sovereign.api.config;

/**
 * Base interface for backend-specific configuration.
 * <p>
 * Each backend implementation should provide its own configuration class
 * implementing this interface. The configuration is validated before being used
 * by the leader election mechanism.
 * </p>
 *
 * @see LeaderElectionConfig
 */
public interface BackendConfig {

    /**
     * Validates the configuration.
     *
     * @return true if the configuration is valid, false otherwise
     */
    boolean isValid();

}
