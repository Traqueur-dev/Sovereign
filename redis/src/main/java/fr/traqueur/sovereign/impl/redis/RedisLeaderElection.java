package fr.traqueur.sovereign.impl.redis;

import fr.traqueur.sovereign.api.LeaderElection;
import fr.traqueur.sovereign.api.LeaderElectionConfig;
import fr.traqueur.sovereign.api.State;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.slf4j.Logger;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis-based implementation of leader election.
 */
public class RedisLeaderElection implements LeaderElection {

    private final String instanceId;
    private final RedisAsyncCommands<String, String> redisCommands;
    private final ScheduledExecutorService scheduler;
    private final LeaderElectionConfig<RedisElectionConfig> config;
    private final Logger logger;
    private final Random random;

    private final AtomicReference<State> currentState = new AtomicReference<>(State.FOLLOWER);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong lastStateChange = new AtomicLong(System.currentTimeMillis());

    private volatile ScheduledFuture<?> electionTask;
    private volatile ScheduledFuture<?> heartbeatTask;

    protected RedisLeaderElection(String instanceId,
                               RedisAsyncCommands<String, String> redisCommands,
                               ScheduledExecutorService scheduler,
                               LeaderElectionConfig<RedisElectionConfig> config) {
        this.instanceId = instanceId;
        this.redisCommands = redisCommands;
        this.scheduler = scheduler;
        this.config = config;
        this.logger = config.logger();
        this.random = new Random();

        logger.info("Sovereign instance created: {}", instanceId);
    }

    @Override
    public CompletableFuture<Void> start() {
        if (!isRunning.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        logger.info("Starting Sovereign election for instance: {}", instanceId);

        // Random delay to avoid race conditions (like original)
        int randomDelay = random.nextInt(10);
        logger.debug("Instance {} will wait {}s before starting election to avoid race conditions", instanceId, randomDelay);

        return CompletableFuture
                .runAsync(() -> logger.debug("Instance {} delay started...", instanceId),
                        CompletableFuture.delayedExecutor(randomDelay, TimeUnit.SECONDS, scheduler))
                .thenRunAsync(this::startPeriodicTasks, scheduler)
                .thenComposeAsync(ignored -> attemptElection(), scheduler);
    }

    @Override
    public CompletableFuture<Void> stop() {
        if (!isRunning.compareAndSet(true, false)) {
            return CompletableFuture.completedFuture(null);
        }

        logger.info("Stopping Sovereign election for instance: {}", instanceId);

        return CompletableFuture
                .runAsync(this::stopPeriodicTasks, scheduler)
                .thenComposeAsync(ignored -> {
                    if (isLeader()) {
                        return releaseLeadership();
                    }
                    return CompletableFuture.completedFuture(null);
                }, scheduler)
                .exceptionally(throwable -> {
                    logger.error("Error during shutdown for instance: {}", instanceId, throwable);
                    return null;
                });
    }

    @Override
    public boolean isLeader() {
        return currentState.get() == State.LEADER;
    }

    @Override
    public State getState() {
        return currentState.get();
    }

    @Override
    public String getId() {
        return instanceId;
    }

    private void startPeriodicTasks() {
        electionTask = scheduler.scheduleAtFixedRate(
                this::runElectionCycle,
                config.electionInterval().toSeconds(),
                config.electionInterval().toSeconds(),
                TimeUnit.SECONDS
        );

        heartbeatTask = scheduler.scheduleAtFixedRate(
                this::runHeartbeat,
                config.heartbeatInterval().toSeconds(),
                config.heartbeatInterval().toSeconds(),
                TimeUnit.SECONDS
        );

        logger.debug("Periodic tasks started for instance: {}", instanceId);
    }

    private void stopPeriodicTasks() {
        if (electionTask != null) {
            electionTask.cancel(false);
        }
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        logger.debug("Periodic tasks stopped for instance: {}", instanceId);
    }

    private void runElectionCycle() {
        if (!isRunning.get()) {
            return;
        }

        masterElection().exceptionally(throwable -> {
            logger.warn("Election cycle failed for instance {}: {}", instanceId, throwable.getMessage());
            return null;
        });
    }

    private void runHeartbeat() {
        if (!isRunning.get() || !isLeader()) {
            return;
        }

        sendHeartbeat().exceptionally(throwable -> {
            logger.warn("Heartbeat failed for leader {}: {}", instanceId, throwable.getMessage());
            return null;
        });
    }

    private CompletableFuture<Void> masterElection() {
        if (!isRunning.get()) {
            return CompletableFuture.completedFuture(null);
        }

        return redisCommands
                .get(config.additionalConfig().leaderKey())
                .thenCompose(this::handleMasterElectionResult)
                .toCompletableFuture()
                .exceptionally(throwable -> {
                    logger.error("Error during master election for instance: {}", instanceId, throwable);
                    return null;
                });
    }

    private CompletableFuture<Void> handleMasterElectionResult(String currentLeader) {
        if (currentLeader == null) {
            // No leader, attempt election
            return attemptElection();
        } else if (currentLeader.equals(instanceId)) {
            // We are already the leader, renew TTL
            return handleCurrentLeaderStatus();
        } else {
            // Someone else is leader, check their health
            return checkLeaderHealth(currentLeader);
        }
    }

    private CompletableFuture<Void> handleCurrentLeaderStatus() {
        if (!isLeader()) {
            transitionTo(State.LEADER);
            logger.info("Confirmed as leader for instance: {}", instanceId);
        }
        return renewLeaderTTL();
    }

    private CompletableFuture<Void> attemptElection() {
        logger.debug("Attempting leader election for instance: {}", instanceId);

        SetArgs setArgs = SetArgs.Builder.nx().ex(config.leaderTTL().toSeconds());

        return redisCommands.set(config.additionalConfig().leaderKey(), instanceId, setArgs)
                .thenCompose(this::handleElectionResult)
                .toCompletableFuture();
    }

    private CompletableFuture<Void> handleElectionResult(String result) {
        if ("OK".equals(result)) {
            // Double verification that we are really the leader
            return redisCommands.get(config.additionalConfig().leaderKey())
                    .thenCompose(actualLeader -> {
                        if (instanceId.equals(actualLeader)) {
                            // Election successful and confirmed
                            transitionTo(State.LEADER);
                            logger.info("Successfully elected as leader: {}", instanceId);
                            return cleanupOldHeartbeats();
                        } else {
                            // Race condition detected - another server took control
                            logger.warn("Election race condition detected for instance: {}. Actual leader is: {}", instanceId, actualLeader);
                            transitionTo(State.FOLLOWER);
                            return CompletableFuture.completedFuture(null);
                        }
                    })
                    .toCompletableFuture();
        } else {
            // Election failed, get current leader for info
            return redisCommands.get(config.additionalConfig().leaderKey())
                    .thenAccept(currentLeader -> {
                        if (currentLeader != null) {
                            logger.debug("Election failed for instance: {}, current leader is: {}", instanceId, currentLeader);
                        }
                        transitionTo(State.FOLLOWER);
                    })
                    .toCompletableFuture();
        }
    }

    private CompletableFuture<Void> renewLeaderTTL() {
        return redisCommands.expire(config.additionalConfig().leaderKey(), config.leaderTTL().toSeconds())
                .thenAccept(result -> {
                    if (!result) {
                        transitionTo(State.FOLLOWER);
                        logger.warn("Leader key TTL renewal failed for instance: {}, no longer leader", instanceId);
                    } else {
                        logger.debug("Leader key TTL renewed successfully for instance: {}", instanceId);
                    }
                })
                .toCompletableFuture();
    }

    private CompletableFuture<Void> checkLeaderHealth(String currentLeader) {
        String heartbeatKey = config.additionalConfig().heartbeatKeyPrefix() + currentLeader;

        return redisCommands.get(heartbeatKey)
                .thenCompose(lastHeartbeat -> evaluateLeaderHealth(currentLeader, lastHeartbeat))
                .toCompletableFuture();
    }

    private CompletableFuture<Void> evaluateLeaderHealth(String currentLeader, String lastHeartbeat) {
        if (lastHeartbeat == null) {
            logger.info("Leader {} not responding (no heartbeat), attempting takeover", currentLeader);
            return attemptElection();
        }

        try {
            long lastHeartbeatTime = Long.parseLong(lastHeartbeat);
            long currentTime = System.currentTimeMillis();
            long heartbeatAge = currentTime - lastHeartbeatTime;

            if (heartbeatAge > (config.leaderTTL().toSeconds() + 5) * 1000L) {
                logger.info("Leader {} heartbeat is stale ({}ms old), attempting takeover", currentLeader, heartbeatAge);
                return attemptElection();
            } else {
                if (isLeader()) {
                    transitionTo(State.FOLLOWER);
                    logger.info("Instance {} is no longer the leader, valid heartbeat found for leader {}", instanceId, currentLeader);
                }
                return CompletableFuture.completedFuture(null);
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid heartbeat format for leader {}, attempting takeover", currentLeader);
            return attemptElection();
        }
    }

    private CompletableFuture<Void> sendHeartbeat() {
        String heartbeatKey = config.additionalConfig().heartbeatKeyPrefix() + instanceId;
        String timestamp = String.valueOf(System.currentTimeMillis());

        return redisCommands.setex(heartbeatKey, config.leaderTTL().toSeconds() + 5, timestamp)
                .thenRun(() -> logger.debug("Heartbeat sent by leader: {}", instanceId)).toCompletableFuture();
    }

    private CompletableFuture<Void> releaseLeadership() {
        logger.debug("Releasing leadership for instance: {}", instanceId);

        return redisCommands
                .get(config.additionalConfig().leaderKey())
                .thenComposeAsync(currentLeader -> {
                    if (!instanceId.equals(currentLeader)) {
                        logger.debug("Instance {} is not the current leader ({}), skipping release", instanceId, currentLeader);
                        return CompletableFuture.completedFuture(null);
                    }

                    return CompletableFuture
                            .allOf(
                                    redisCommands.del(config.additionalConfig().leaderKey()).toCompletableFuture(),
                                    redisCommands.del(config.additionalConfig().heartbeatKeyPrefix() + instanceId).toCompletableFuture()
                            )
                            .thenRun(() -> {
                                transitionTo(State.FOLLOWER);
                                logger.info("Leadership released successfully for instance: {}", instanceId);
                            });
                })
                .toCompletableFuture()
                .exceptionally(throwable -> {
                    logger.error("Error releasing leadership for instance: {}", instanceId, throwable);
                    return null;
                });
    }

    private CompletableFuture<Void> cleanupOldHeartbeats() {
        return redisCommands.keys(config.additionalConfig().heartbeatKeyPrefix() + "*")
                .thenCompose(keys -> {
                    if (keys == null || keys.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    List<CompletableFuture<Void>> cleanupTasks = keys.stream()
                            .filter(key -> !key.equals(config.additionalConfig().heartbeatKeyPrefix() + instanceId))
                            .map(this::cleanupHeartbeatKey)
                            .toList();

                    return CompletableFuture.allOf(cleanupTasks.toArray(new CompletableFuture[0]));
                })
                .thenRun(() -> logger.debug("Heartbeat cleanup completed for instance: {}", instanceId))
                .toCompletableFuture();
    }

    private CompletableFuture<Void> cleanupHeartbeatKey(String heartbeatKey) {
        return redisCommands.get(heartbeatKey)
                .thenCompose(value -> {
                    if (value == null) {
                        return CompletableFuture.completedFuture(null);
                    }

                    try {
                        long lastHeartbeat = Long.parseLong(value);
                        long currentTime = System.currentTimeMillis();

                        if (currentTime - lastHeartbeat > (config.leaderTTL().toSeconds() + 10) * 1000L) {
                            return redisCommands.del(heartbeatKey)
                                    .thenRun(() -> logger.debug("Cleaned up stale heartbeat key: {}", heartbeatKey))
                                    .toCompletableFuture();
                        }
                    } catch (NumberFormatException e) {
                        return redisCommands.del(heartbeatKey)
                                .thenRun(() -> logger.debug("Cleaned up invalid heartbeat key: {}", heartbeatKey))
                                .toCompletableFuture();
                    }

                    return CompletableFuture.completedFuture(null);
                })
                .toCompletableFuture();
    }

    private void transitionTo(State newState) {
        State previousState = currentState.getAndSet(newState);
        if (previousState != newState) {
            lastStateChange.set(System.currentTimeMillis());
            logger.debug("State transition for {}: {} -> {}", instanceId, previousState, newState);
        }
    }
}