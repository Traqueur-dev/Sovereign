package fr.traqueur.sovereign.api;

import fr.traqueur.sovereign.api.config.BackendConfig;
import fr.traqueur.sovereign.api.config.LeaderElectionConfig;

import java.util.concurrent.ScheduledExecutorService;

public interface LeaderElectionProvider<T extends LeaderElection, B extends BackendConfig> {

    Class<B> getType();

    T create(String instanceId, ScheduledExecutorService scheduler, LeaderElectionConfig<B> config);

}
