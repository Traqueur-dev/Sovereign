package fr.traqueur.sovereign.impl.redis;

import fr.traqueur.sovereign.api.LeaderElectionConfig;
import fr.traqueur.sovereign.api.LeaderElectionProvider;

import java.util.concurrent.ScheduledExecutorService;

public record RedisLeaderElectionProvider() implements LeaderElectionProvider<RedisLeaderElection, RedisElectionConfig> {
    @Override
    public Class<RedisElectionConfig> getType() {
        return RedisElectionConfig.class;
    }

    @Override
    public RedisLeaderElection create(String instanceId, ScheduledExecutorService scheduler, LeaderElectionConfig<RedisElectionConfig> config) {
        return new RedisLeaderElection(instanceId, config.additionalConfig().redisCommands(), scheduler, config);
    }
}
