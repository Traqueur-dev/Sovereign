package fr.traqueur.sovereign.api;

import java.util.concurrent.ScheduledExecutorService;

public interface LeaderElectionProvider<T extends LeaderElection, B extends BackendConfig> {

    Class<B> getType();

    T create(String instanceId, ScheduledExecutorService scheduler, LeaderElectionConfig<B> config);

}
