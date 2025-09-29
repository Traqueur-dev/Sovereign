package fr.traqueur.sovereign.impl.redis;

import fr.traqueur.sovereign.api.config.BackendConfig;
import io.lettuce.core.api.async.RedisAsyncCommands;

/**
 * Redis-specific configuration for leader election.
 * <p>
 * This configuration specifies the Redis commands interface and key naming
 * for leader election coordination. It uses two types of keys:
 * </p>
 * <ul>
 *   <li><strong>Leader Key:</strong> Single key that holds the current leader's instance ID</li>
 *   <li><strong>Heartbeat Keys:</strong> Per-instance keys (prefixed) that track leader health</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * RedisElectionConfig config = RedisElectionConfig.builder()
 *     .redisCommands(redisAsyncCommands)
 *     .leaderKey("my-app:leader")
 *     .heartbeatKeyPrefix("my-app:heartbeat:")
 *     .build();
 * }</pre>
 *
 * @param redisCommands       the Lettuce async commands interface for Redis operations
 * @param leaderKey           the Redis key that stores the current leader ID
 * @param heartbeatKeyPrefix  the prefix for heartbeat keys (instance ID is appended)
 *
 * @see BackendConfig
 * @see fr.traqueur.sovereign.api.config.LeaderElectionConfig
 */
public record RedisElectionConfig(RedisAsyncCommands<String, String> redisCommands, String leaderKey, String heartbeatKeyPrefix) implements BackendConfig {

    /**
     * Creates a new builder for RedisElectionConfig.
     *
     * @return a new builder instance with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Validates the Redis configuration.
     *
     * @return true if all required fields are non-null and non-empty, false otherwise
     */
    @Override
    public boolean isValid() {
        return redisCommands != null && leaderKey != null && !leaderKey.isEmpty() && heartbeatKeyPrefix != null && !heartbeatKeyPrefix.isEmpty();
    }

    /**
     * Builder for constructing RedisElectionConfig instances.
     * <p>
     * Provides sensible defaults for key names:
     * </p>
     * <ul>
     *   <li>Leader key: {@code "sovereign:leader"}</li>
     *   <li>Heartbeat prefix: {@code "sovereign:heartbeat:"}</li>
     * </ul>
     */
    public static class Builder {
        private RedisAsyncCommands<String, String> redisCommands;
        private String leaderKey = "sovereign:leader";
        private String heartbeatKeyPrefix = "sovereign:heartbeat:";

        /**
         * Sets the Redis async commands interface.
         * <p>
         * This is required and must be provided before building.
         * </p>
         *
         * @param commands the Lettuce async commands interface
         * @return this builder instance
         */
        public Builder redisCommands(RedisAsyncCommands<String, String> commands) {
            this.redisCommands = commands;
            return this;
        }

        /**
         * Sets the Redis key for storing the leader ID.
         * <p>
         * Default: {@code "sovereign:leader"}
         * </p>
         *
         * @param leaderKey the leader key name
         * @return this builder instance
         */
        public Builder leaderKey(String leaderKey) {
            this.leaderKey = leaderKey;
            return this;
        }

        /**
         * Sets the prefix for heartbeat keys.
         * <p>
         * Heartbeat keys are formed by appending the instance ID to this prefix.
         * Default: {@code "sovereign:heartbeat:"}
         * </p>
         *
         * @param prefix the heartbeat key prefix
         * @return this builder instance
         */
        public Builder heartbeatKeyPrefix(String prefix) {
            this.heartbeatKeyPrefix = prefix;
            return this;
        }

        /**
         * Builds the RedisElectionConfig instance.
         * <p>
         * Note: Validation is deferred until the configuration is used by
         * {@link fr.traqueur.sovereign.api.config.LeaderElectionConfig.Builder#build()}.
         * </p>
         *
         * @return a new RedisElectionConfig instance
         */
        public RedisElectionConfig build() {
            return new RedisElectionConfig(redisCommands, leaderKey, heartbeatKeyPrefix);
        }
    }
}
