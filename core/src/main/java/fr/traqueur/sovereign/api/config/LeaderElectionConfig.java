package fr.traqueur.sovereign.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Configuration for a leader election mechanism.
 * <p>
 * This record holds the core configuration parameters for leader election,
 * including timing parameters and backend-specific configuration.
 * Use the {@link Builder} to create instances with validation.
 * </p>
 *
 * @param <B> the type of backend-specific configuration
 * @param leaderTTL          Time-to-live for the leader lease
 * @param heartbeatInterval  Interval at which the leader sends heartbeats
 * @param electionInterval   Interval at which nodes check for leader status and initiate elections if necessary
 * @param logger             Logger instance for election process logging
 * @param additionalConfig   Backend-specific configuration
 *
 * @see BackendConfig
 * @see Builder
 */
public record LeaderElectionConfig<B extends BackendConfig>(Duration leaderTTL,
                                                            Duration heartbeatInterval,
                                                            Duration electionInterval,
                                                            Logger logger,
                                                            B additionalConfig) {

    /**
     * Constructs a LeaderElectionConfig from a builder.
     *
     * @param builder the builder containing the configuration parameters
     */
    public LeaderElectionConfig(Builder<B> builder) {
        this(builder.leaderTtl, builder.heartbeatInterval, builder.electionInterval, builder.logger, builder.additionalConfig);
    }

    /**
     * Creates a new Builder instance for LeaderElectionConfig with the specified backend configuration.
     * <p>
     * This is the recommended way to create a configuration as it ensures the backend
     * configuration is set upfront.
     * </p>
     *
     * @param <B> the type of backend-specific configuration
     * @param additionalConfig the backend-specific configuration
     * @return a new Builder instance with the backend configuration set
     */
    public static <B extends BackendConfig> Builder<B> configureFor(B additionalConfig) {
        Builder<B> builder = new Builder<>();
        builder.additionalConfig = additionalConfig;
        return builder;
    }

    /**
     * Creates a new Builder instance for LeaderElectionConfig.
     *
     * @param <B> the type of backend-specific configuration
     * @return a new Builder instance
     */
    public static <B extends BackendConfig> Builder<B> builder() {
        return new Builder<>();
    }

    /**
     * Builder class for constructing LeaderElectionConfig instances.
     * <p>
     * Provides a fluent API for configuring leader election parameters with sensible defaults.
     * All durations must be positive. The configuration is validated at build time.
     * </p>
     *
     * @param <B> the type of backend-specific configuration
     */
    public static class Builder<B extends BackendConfig> {

        /** Default logger for the election process */
        private Logger logger = LoggerFactory.getLogger("Sovereign");
        /** Default leader TTL: 15 seconds */
        private Duration leaderTtl = Duration.ofSeconds(15);
        /** Default heartbeat interval: 5 seconds */
        private Duration heartbeatInterval = Duration.ofSeconds(5);
        /** Default election interval: 3 seconds */
        private Duration electionInterval = Duration.ofSeconds(3);
        /** Backend-specific configuration */
        private B additionalConfig;

        private Builder() {
            // Private constructor to enforce usage of the static builder() method
        }

        /**
         * Sets the backend-specific configuration.
         *
         * @param additionalConfig the backend configuration
         * @return this builder instance
         */
        public Builder<B> additionalConfig(B additionalConfig) {
            this.additionalConfig = additionalConfig;
            return this;
        }

        /**
         * Sets the logger for the leader election process.
         *
         * @param logger the logger instance
         * @return this builder instance
         */
        public Builder<B> logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets the leader TTL duration.
         * <p>
         * This defines how long a leader lease is valid. Must be positive.
         * </p>
         *
         * @param leaderTtl the leader TTL duration
         * @return this builder instance
         */
        public Builder<B> leaderTtl(Duration leaderTtl) {
            this.leaderTtl = leaderTtl;
            return this;
        }

        /**
         * Sets the heartbeat interval duration.
         * <p>
         * This defines how frequently the leader sends heartbeats. Must be positive.
         * Should be less than the leader TTL.
         * </p>
         *
         * @param heartbeatInterval the heartbeat interval duration
         * @return this builder instance
         */
        public Builder<B> heartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
            return this;
        }

        /**
         * Sets the election interval duration.
         * <p>
         * This defines how frequently nodes check for leader status and initiate elections.
         * Must be positive.
         * </p>
         *
         * @param electionInterval the election interval duration
         * @return this builder instance
         */
        public Builder<B> electionInterval(Duration electionInterval) {
            this.electionInterval = electionInterval;
            return this;
        }

        /**
         * Builds and returns a LeaderElectionConfig instance.
         *
         * @return a new LeaderElectionConfig instance
         * @throws IllegalStateException if the configuration is invalid
         */
        public LeaderElectionConfig<B> build() {
            if(!isValid()) {
                throw new IllegalStateException("Invalid LeaderElectionConfig configuration");
            }
            return new LeaderElectionConfig<>(this);
        }

        /**
         * Validates the configuration parameters.
         *
         * @return true if the configuration is valid, false otherwise
         */
        private boolean isValid() {
            if(logger == null) {
                return false;
            }
            if(additionalConfig == null || !additionalConfig.isValid()) {
                return false;
            }
            if(leaderTtl.isNegative() || leaderTtl.isZero()) {
                return false;
            }
            if(heartbeatInterval.isNegative() || heartbeatInterval.isZero()) {
                return false;
            }
            return !electionInterval.isNegative() && !electionInterval.isZero();
        }
    }

}
