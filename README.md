# Sovereign 👑

A robust, distributed leader election library for Java applications, designed to provide reliable coordination in distributed systems.

## Features

- **Multiple Backend Support**: Pluggable architecture with Redis backend implementation
- **Service Provider Interface**: Auto-discovery of backend implementations via Java ServiceLoader
- **Type Safety**: Generic type system ensuring compile-time safety between providers and configurations
- **Async Operations**: Non-blocking API using CompletableFuture
- **Health Monitoring**: Automatic leader health checking and failover
- **Configurable Timeouts**: Flexible configuration for TTL, heartbeat, and election intervals
- **Clean State Management**: Clear state transitions (FOLLOWER, CANDIDATE, LEADER)

## Quick Start

### Add Dependency

```xml
<dependency>
    <groupId>com.github.Traqueur-dev.Sovereign</groupId>
    <artifactId>redis</artifactId>
    <version>1.0.0-DEVELOPMENT-SNAPSHOT</version>
</dependency>
```

### Basic Usage

```java
// Configure Redis backend
RedisElectionConfig redisConfig = RedisElectionConfig.builder()
    .redisCommands(redisAsyncCommands)
    .leaderKey("my-app:leader")
    .heartbeatKeyPrefix("my-app:heartbeat:")
    .build();

// Configure leader election
LeaderElectionConfig<RedisElectionConfig> config = LeaderElectionConfig
    .configureFor(redisConfig)
    .leaderTtl(Duration.ofSeconds(30))
    .heartbeatInterval(Duration.ofSeconds(10))
    .electionInterval(Duration.ofSeconds(5))
    .build();

// Create leader election instance
LeaderElection election = LeaderElectionFactory.create(
    "instance-1",
    scheduler,
    config
);

// Start the election process
election.start().thenRun(() -> {
    System.out.println("Election started");
});

// Check leadership status
if (election.isLeader()) {
    System.out.println("I am the leader!");
}

// Stop when done
election.stop();
```

## Architecture

### Core Components

- **`LeaderElection`**: Main interface for election operations
- **`LeaderElectionProvider`**: SPI for backend implementations
- **`LeaderElectionFactory`**: Factory for creating election instances
- **`LeaderElectionConfig`**: Builder-pattern configuration
- **`State`**: Enum representing node states (FOLLOWER, CANDIDATE, LEADER)

### Backend Implementations

#### Redis Backend
Uses Redis for distributed coordination with:
- **Leader Key**: Single key holding current leader ID
- **Heartbeat Keys**: Per-instance keys for health monitoring
- **TTL Management**: Automatic expiration for fault tolerance
- **Race Condition Prevention**: Atomic operations and double verification

## Configuration

### Core Configuration

```java
LeaderElectionConfig.builder()
    .leaderTtl(Duration.ofSeconds(30))        // Leader lease duration
    .heartbeatInterval(Duration.ofSeconds(10)) // Heartbeat frequency
    .electionInterval(Duration.ofSeconds(5))   // Election check frequency
    .logger(LoggerFactory.getLogger("MyApp"))  // Custom logger
    .additionalConfig(backendConfig)           // Backend-specific config
    .build();
```

### Redis Configuration

```java
RedisElectionConfig.builder()
    .redisCommands(redisAsyncCommands)         // Lettuce async commands
    .leaderKey("app:leader")                   // Leader key name
    .heartbeatKeyPrefix("app:heartbeat:")      // Heartbeat key prefix
    .build();
```

## State Management

The library manages three distinct states:

- **FOLLOWER**: Default state, waiting for leader or participating in election
- **CANDIDATE**: Attempting to become leader
- **LEADER**: Currently elected leader, sending heartbeats

State transitions are logged and can be monitored via the `getState()` method.

## Building Custom Backends

Implement the `LeaderElectionProvider` interface:

```java
public class MyBackendProvider implements LeaderElectionProvider<MyLeaderElection, MyBackendConfig> {

    @Override
    public Class<MyBackendConfig> getType() {
        return MyBackendConfig.class;
    }

    @Override
    public MyLeaderElection create(String instanceId,
                                   ScheduledExecutorService scheduler,
                                   LeaderElectionConfig<MyBackendConfig> config) {
        return new MyLeaderElection(instanceId, scheduler, config);
    }
}
```

Register via `META-INF/services/fr.traqueur.sovereign.api.LeaderElectionProvider`.

## Building

```bash
./gradlew build
```

## Roadmap

### Planned Features

- **Event System**: Reactive notifications for state changes and leadership transitions
- **Additional Backends**: Support for Zookeeper, Consul, etcd

## Requirements

- Java 21+
- Redis 6.0+ (for Redis backend)
- SLF4J for logging