package fr.traqueur.sovereign.impl.redis;

import fr.traqueur.sovereign.api.BackendConfig;
import io.lettuce.core.api.async.RedisAsyncCommands;

public record RedisElectionConfig(RedisAsyncCommands<String, String> redisCommands, String leaderKey, String heartbeatKeyPrefix) implements BackendConfig {

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean isValid() {
        return redisCommands != null && leaderKey != null && !leaderKey.isEmpty() && heartbeatKeyPrefix != null && !heartbeatKeyPrefix.isEmpty();
    }

    public static class Builder {
        private RedisAsyncCommands<String, String> redisCommands;
        private String leaderKey = "sovereign:leader";
        private String heartbeatKeyPrefix = "sovereign:heartbeat:";

        public Builder redisCommands(RedisAsyncCommands<String, String> commands) {
            this.redisCommands = commands;
            return this;
        }

        public Builder leaderKey(String leaderKey) {
            this.leaderKey = leaderKey;
            return this;
        }

        public Builder heartbeatKeyPrefix(String prefix) {
            this.heartbeatKeyPrefix = prefix;
            return this;
        }

        public RedisElectionConfig build() {
            return new RedisElectionConfig(redisCommands, leaderKey, heartbeatKeyPrefix);
        }
    }
}
