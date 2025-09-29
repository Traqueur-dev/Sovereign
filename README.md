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
- **Event System**: Reactive event notifications for state changes and leadership transitions with sync/async support

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

// Register event listeners (optional)
election.onLeadershipAcquired(event -> {
    System.out.println("Became leader at: " + event.timestamp());
}, false);

election.onLeadershipLost(event -> {
    System.out.println("Lost leadership at: " + event.timestamp());
}, false);

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

## Event System

Sovereign provides a comprehensive event system for monitoring leader election lifecycle:

### Available Events

- **`StateChangedEvent`**: Fired on any state transition (FOLLOWER, CANDIDATE, LEADER)
- **`LeadershipAcquiredEvent`**: Fired when the instance becomes leader
- **`LeadershipLostEvent`**: Fired when the instance loses leadership
- **`ElectionFailedEvent`**: Fired when an election cycle encounters an error

### Registering Listeners

```java
// Generic event registration
election.on(StateChangedEvent.class, event -> {
    System.out.println("State changed from " + event.previousState() +
                       " to " + event.newState());
}, true); // true = async, false = sync

// Convenience methods for common events
election.onLeadershipAcquired(event -> {
    // Handle leadership acquisition
}, false);

election.onLeadershipLost(event -> {
    // Handle leadership loss
}, false);
```

### Synchronous vs Asynchronous Listeners

- **Synchronous (`async = false`)**: Listeners are called immediately on the calling thread. Use for lightweight operations.
- **Asynchronous (`async = true`)**: Listeners are called on the event executor thread pool. Use for heavy processing.

### Unregistering Listeners

```java
ListenerRegistration registration = election.onLeadershipAcquired(event -> {
    // Handle event
}, false);

// Later, to unregister:
registration.remove();
```

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

- **Additional Backends**: Support for Zookeeper, Consul, etcd
- **Metrics Integration**: Built-in metrics for monitoring election behavior

## Requirements

- Java 21+
- Redis 6.0+ (for Redis backend)
- SLF4J for logging