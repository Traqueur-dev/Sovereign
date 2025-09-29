package fr.traqueur.sovereign.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Configuration for a leader election mechanism.
 *
 * @param leaderTTL          Time-to-live for the leader lease.
 * @param heartbeatInterval  Interval at which the leader sends heartbeats.
 * @param electionInterval   Interval at which nodes check for leader status and initiate elections if necessary.
 */
public record LeaderElectionConfig<B extends BackendConfig>(Duration leaderTTL,
                                                            Duration heartbeatInterval,
                                                            Duration electionInterval,
                                                            Logger logger,
                                                            B additionalConfig) {

    /**
     * Constructs a LeaderElectionConfig with a builder.
     * @param builder The builder containing the configuration parameters.
     */
    public LeaderElectionConfig(Builder<B> builder) {
        this(builder.leaderTtl, builder.heartbeatInterval, builder.electionInterval, builder.logger, builder.additionalConfig);
    }

    /**
     * Creates a new Builder instance for LeaderElectionConfig with the specified additional backend configuration.
     * @param additionalConfig The additional backend configuration.
     * @return A new Builder instance.
     */
    public static <B extends BackendConfig> Builder<B> configureFor(B additionalConfig) {
        Builder<B> builder = new Builder<>();
        builder.additionalConfig = additionalConfig;
        return builder;
    }

    /**
     * Creates a new Builder instance for LeaderElectionConfig.
     * @return A new Builder instance.
     */
    public static <B extends BackendConfig> Builder<B> builder() {
        return new Builder<>();
    }

    /**
     * Builder class for constructing LeaderElectionConfig instances.
     */
    public static class Builder<B extends BackendConfig> {

        /** Default values for the configuration parameters */
        private Logger logger = LoggerFactory.getLogger("Sovereign");
        private Duration leaderTtl = Duration.ofSeconds(15);
        private Duration heartbeatInterval = Duration.ofSeconds(5);
        private Duration electionInterval = Duration.ofSeconds(3);
        private B additionalConfig;

        private Builder() {
            // Private constructor to enforce usage of the static builder() method
        }

        public Builder<B> additionalConfig(B additionalConfig) {
            this.additionalConfig = additionalConfig;
            return this;
        }

        /** Sets the logger for the leader election process. */
        public Builder<B> logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /** Sets the leader TTL duration. */
        public Builder<B> leaderTtl(Duration leaderTtl) {
            this.leaderTtl = leaderTtl;
            return this;
        }

        /** Sets the heartbeat interval duration. */
        public Builder<B> heartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
            return this;
        }

        /** Sets the election interval duration. */
        public Builder<B> electionInterval(Duration electionInterval) {
            this.electionInterval = electionInterval;
            return this;
        }

        /** Builds and returns a LeaderElectionConfig instance. */
        public LeaderElectionConfig<B> build() {
            if(!isValid()) {
                throw new IllegalStateException("Invalid LeaderElectionConfig configuration");
            }
            return new LeaderElectionConfig<>(this);
        }

        /** Validates the configuration parameters. */
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
