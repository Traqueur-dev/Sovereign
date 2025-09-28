package fr.traqueur.sovereign.impl.redis;

import fr.traqueur.sovereign.InternalBackendType;
import fr.traqueur.sovereign.api.BackendType;
import fr.traqueur.sovereign.api.LeaderElectionConfig;
import fr.traqueur.sovereign.api.LeaderElectionProvider;

import java.util.concurrent.ScheduledExecutorService;

public class RedisLeaderElectionProvider implements LeaderElectionProvider<RedisLeaderElection, RedisElectionConfig> {
    @Override
    public BackendType getType() {
        return InternalBackendType.REDIS;
    }

    @Override
    public RedisLeaderElection create(String instanceId, ScheduledExecutorService scheduler, LeaderElectionConfig<RedisElectionConfig> config) {
        return new RedisLeaderElection(instanceId, config.additionalConfig().redisCommands(), scheduler, config);
    }
}
