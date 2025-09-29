# Core Architecture

This document describes the core architecture of the Sovereign leader election library, including its pluggable backend system, event system, and design patterns.

## Table of Contents

1. [Overview](#overview)
2. [Architecture Principles](#architecture-principles)
3. [Service Provider Interface (SPI)](#service-provider-interface-spi)
4. [Core Components](#core-components)
5. [Event System](#event-system)
6. [Configuration System](#configuration-system)
7. [Component Interactions](#component-interactions)
8. [Implementing a Custom Backend](#implementing-a-custom-backend)

---

## Overview

Sovereign is a modular leader election library built on a pluggable architecture. The core module provides interfaces and infrastructure, while backend modules (e.g., Redis, Zookeeper) implement the actual coordination logic.

**Design Goals:**
- **Pluggable:** Easy to add new backend implementations
- **Type-Safe:** Generics ensure compile-time type safety
- **Non-Blocking:** Async operations using `CompletableFuture`
- **Event-Driven:** Comprehensive event system for state changes
- **Zero-Config Discovery:** Automatic backend registration via SPI

---

## Architecture Principles

### 1. Separation of Concerns

```
┌─────────────────────────────────────────────────────────┐
│                     Application                         │
└───────────────────────┬─────────────────────────────────┘
                        │ uses
                        ▼
┌─────────────────────────────────────────────────────────┐
│                  Core API Module                        │
│  ┌─────────────────────────────────────────────────┐    │
│  │  LeaderElection Interface                       │    │
│  │  LeaderElectionFactory (SPI loader)             │    │
│  │  LeaderElectionConfig (builder)                 │    │
│  │  Event System (EventBus, Events, Listeners)     │    │
│  └─────────────────────────────────────────────────┘    │
└───────────────────────┬─────────────────────────────────┘
                        │ implements
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
   ┌─────────┐     ┌─────────┐     ┌─────────┐
   │  Redis  │     │Zookeeper│     │  Custom │
   │ Backend │     │ Backend │     │ Backend │
   └─────────┘     └─────────┘     └─────────┘
```

### 2. Generic Type Safety

```java
public interface LeaderElectionProvider<
    T extends LeaderElection,      // Implementation type
    B extends BackendConfig         // Config type
> {
    Class<B> getType();
    T create(String instanceId, ScheduledExecutorService scheduler,
             LeaderElectionConfig<B> config);
}
```

This ensures:
- Compile-time type checking
- No runtime casting errors
- IDE autocomplete support

### 3. Asynchronous First

All state-changing operations return `CompletableFuture<Void>`:

```java
election.start()
    .thenCompose(ignored -> doWork())
    .thenCompose(ignored -> election.stop())
    .exceptionally(this::handleError);
```

---

## Service Provider Interface (SPI)

Sovereign uses Java's `ServiceLoader` mechanism for automatic backend discovery.

### How It Works

```
┌───────────────────────────────────────────────────────────┐
│ 1. Application Classpath                                  │
├───────────────────────────────────────────────────────────┤
│                                                           │
│  core.jar                                                 │
│  ├── LeaderElectionFactory.class                          │
│  └── LeaderElectionProvider.class                         │
│                                                           │
│  redis.jar                                                │
│  ├── RedisLeaderElectionProvider.class                    │
│  └── META-INF/services/                                   │
│      └── fr.traqueur.sovereign.api.LeaderElectionProvider │
│          └── "fr...impl.redis.RedisLeaderElectionProvider"│
│                                                           │
└───────────────────────────────────────────────────────────┘
                        │
                        ▼
┌───────────────────────────────────────────────────────────┐
│ 2. Factory Static Initialization                          │
├───────────────────────────────────────────────────────────┤
│                                                           │
│  ServiceLoader.load(LeaderElectionProvider.class)         │
│    .forEach(LeaderElectionFactory::register)              │
│                                                           │
│  Result:                                                  │
│  PROVIDERS.put(RedisElectionConfig.class,                 │
│                new RedisLeaderElectionProvider())         │
│                                                           │
└───────────────────────────────────────────────────────────┘
                        │
                        ▼
┌───────────────────────────────────────────────────────────┐
│ 3. Runtime Usage                                          │
├───────────────────────────────────────────────────────────┤
│                                                           │
│  LeaderElectionFactory.create(id, scheduler, config)      │
│    └─► Lookup provider by config.additionalConfig().class │
│        └─► provider.create(id, scheduler, config)         │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

### Registration Flow (from LeaderElectionFactory.java:51-54)

```java
static {
    ServiceLoader.load(LeaderElectionProvider.class)
            .forEach(LeaderElectionFactory::register);
}
```

**Benefits:**
- No manual registration required
- Add backends by including JAR on classpath
- Compile-time provider validation

---

## Core Components

### 1. LeaderElection Interface

The main contract for all implementations.

```
┌─────────────────────────────────────────────────────────┐
│               LeaderElection Interface                  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Lifecycle:                                             │
│  ├─► start(): CompletableFuture<Void>                   │
│  └─► stop(): CompletableFuture<Void>                    │
│                                                         │
│  State Queries:                                         │
│  ├─► isLeader(): boolean                                │
│  ├─► getState(): State                                  │
│  └─► getId(): String                                    │
│                                                         │
│  Event Registration:                                    │
│  ├─► on(Class<T>, Listener<T>, boolean): Registration   │
│  ├─► onLeadershipAcquired(Listener, boolean)            │
│  └─► onLeadershipLost(Listener, boolean)                │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 2. LeaderElectionFactory

The factory for creating election instances via SPI.

```java
// From LeaderElectionFactory.java:88-101
public static <T extends LeaderElection, B extends BackendConfig> T create(
        String instanceId,
        ScheduledExecutorService scheduler,
        LeaderElectionConfig<B> config) {

    LeaderElectionProvider<T, B> provider =
        (LeaderElectionProvider<T, B>) PROVIDERS.get(
            config.additionalConfig().getClass()
        );

    if (provider == null) {
        throw new IllegalArgumentException(
            "Unknown backend: " + config.additionalConfig().getClass()
        );
    }

    return provider.create(instanceId, scheduler, config);
}
```

### 3. State Enum

Three possible states for an election instance:

```
┌──────────┐
│ FOLLOWER │  ─► Not the leader, following another instance
└──────────┘

┌──────────┐
│CANDIDATE │  ─► Attempting to become leader (optional, backend-specific)
└──────────┘

┌──────────┐
│  LEADER  │  ─► Currently the elected leader
└──────────┘
```

### 4. BackendConfig Interface

Base interface for backend-specific configurations.

```java
public interface BackendConfig {
    /**
     * Validates this configuration.
     * @return true if valid, false otherwise
     */
    boolean isValid();
}
```

Each backend provides its own implementation (e.g., `RedisElectionConfig`).

---

## Event System

Sovereign provides a comprehensive event system for monitoring election state.

### Event Hierarchy

```
                        Event (abstract)
                          │
         ┌────────────────┼────────────────┐
         │                │                │
         ▼                ▼                ▼
 StateChangedEvent  LeadershipEvent  ElectionFailedEvent
                          │
         ┌────────────────┴────────────────┐
         │                                 │
         ▼                                 ▼
 LeadershipAcquiredEvent          LeadershipLostEvent
```

### Event Types

| Event | When Fired | Properties |
|-------|-----------|------------|
| `StateChangedEvent` | Any state transition | `previousState`, `newState` |
| `LeadershipAcquiredEvent` | Transition to LEADER | `instanceId`, `timestamp` |
| `LeadershipLostEvent` | Transition from LEADER | `instanceId`, `timestamp` |
| `ElectionFailedEvent` | Election attempt fails | `instanceId`, `timestamp`, `cause` |

### EventBus Architecture (from EventBus.java)

```
┌─────────────────────────────────────────────────────────┐
│                       EventBus                          │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  listeners: CopyOnWriteArrayList<ListenerEntry>         │
│  eventExecutor: Executor                                │
│  isRunning: AtomicBoolean                               │
│                                                         │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  addListener(listener, eventType, async)                │
│    └─► Wraps listener in TypedListenerWrapper           │
│        └─► Returns unregister() callback                │
│                                                         │
│  publishEvent(event)                                    │
│    └─► For each listener:                               │
│        ├─► If async: execute on eventExecutor           │
│        └─► If sync: call directly                       │
│                                                         │
│  TypedListenerWrapper.notifyIfMatches(event)            │
│    └─► if (eventType.isInstance(event))                 │
│        └─► listener.onEvent(event)                      │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### Listener Registration

```java
// Async listener (executed on background thread)
ListenerRegistration reg = election.on(
    StateChangedEvent.class,
    event -> logger.info("State: {} -> {}",
                         event.previousState(),
                         event.newState()),
    true  // async
);

// Unregister when done
reg.unregister();
```

### Event Flow Example

```
Time ────────────────────────────────────────────────────►

Instance State:  FOLLOWER ──────► LEADER ───────► FOLLOWER

Events Published:
                    │               │              │
                    ▼               ▼              ▼
              StateChangedEvent  StateChangedEvent  StateChangedEvent
              (FOLLOWER→LEADER)  (LEADER→LEADER)   (LEADER→FOLLOWER)
                    │                                 │
                    ▼                                 ▼
           LeadershipAcquiredEvent              LeadershipLostEvent
```

---

## Configuration System

### Builder Pattern with Generic Type Safety

```java
// From LeaderElectionConfig.java
public class LeaderElectionConfig<B extends BackendConfig> {

    public static <B extends BackendConfig> Builder<B> configureFor(B backendConfig) {
        return new Builder<>(backendConfig);
    }

    public static class Builder<B extends BackendConfig> {
        private B backendConfig;
        private Duration leaderTtl = Duration.ofSeconds(30);
        private Duration electionInterval = Duration.ofSeconds(15);
        private Duration heartbeatInterval = Duration.ofSeconds(10);
        private Logger logger = LoggerFactory.getLogger(LeaderElection.class);

        public Builder<B> leaderTtl(Duration ttl) { ... }
        public Builder<B> electionInterval(Duration interval) { ... }
        public Builder<B> heartbeatInterval(Duration interval) { ... }

        public LeaderElectionConfig<B> build() {
            if (!backendConfig.isValid()) {
                throw new IllegalStateException("Invalid backend config");
            }
            return new LeaderElectionConfig<>(this);
        }
    }
}
```

### Configuration Flow

```
1. Create Backend Config
   ↓
   RedisElectionConfig redisConfig = RedisElectionConfig.builder()
       .redisCommands(commands)
       .leaderKey("app:leader")
       .build();

2. Create Election Config (type-safe!)
   ↓
   LeaderElectionConfig<RedisElectionConfig> config =  // Generic type!
       LeaderElectionConfig.configureFor(redisConfig)
           .leaderTtl(Duration.ofSeconds(30))
           .electionInterval(Duration.ofSeconds(15))
           .build();

3. Create Election Instance
   ↓
   LeaderElection election = LeaderElectionFactory.create(
       "instance-1", scheduler, config
   );
```

---

## Component Interactions

### Full Lifecycle Flow

```
┌──────────────┐
│ Application  │
└──────┬───────┘
       │
       │ 1. Configure Backend
       ▼
┌────────────────────┐
│ RedisElectionConfig│
└──────┬─────────────┘
       │
       │ 2. Configure Election
       ▼
┌─────────────────────────────┐
│ LeaderElectionConfig<Redis> │
└──────┬──────────────────────┘
       │
       │ 3. Create Instance
       ▼
┌──────────────────────────┐
│ LeaderElectionFactory    │
│   └─► lookup provider    │────┐
└──────────────────────────┘    │
                                │ 4. Delegate creation
                                ▼
                    ┌───────────────────────────┐
                    │RedisLeaderElectionProvider│
                    └──────┬────────────────────┘
                           │
                           │ 5. Instantiate
                           ▼
                    ┌──────────────────────┐
                    │ RedisLeaderElection  │
                    │   ├─► EventBus       │
                    │   ├─► State tracking │
                    │   └─► Periodic tasks │
                    └──────┬───────────────┘
                           │
                           │ 6. Start
                           ▼
                    ┌──────────────────────┐
                    │ Election Algorithm   │
                    │   ├─► Redis ops      │
                    │   ├─► State updates  │
                    │   └─► Event dispatch │
                    └──────────────────────┘
```

### Internal Component Interaction (within an implementation)

```
┌───────────────────────────────────────────────────────────────┐
│                  RedisLeaderElection Instance                 │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐         ┌──────────────────┐            │
│  │ ScheduledExecutor│────────►│ Election Task    │            │
│  └──────────────────┘         │ (periodic)       │            │
│          │                    └────────┬─────────┘            │
│          │                             │                      │
│          │                             ▼                      │
│          │                    ┌────────────────────┐          │
│          │                    │ State Machine      │          │
│          │                    │  currentState      │          │
│          │                    └────────┬───────────┘          │
│          │                             │                      │
│          │                             │ state change         │
│          │                             ▼                      │
│          │                    ┌────────────────────┐          │
│          │                    │    EventBus        │          │
│          │                    │  publishEvent()    │          │
│          │                    └────────┬───────────┘          │
│          │                             │                      │
│          │                             ├─► Listener A (async) │
│          │                             ├─► Listener B (sync)  │
│          │                             └─► Listener C (async) │
│          │                                                    │
│          └────────────────►┌──────────────────┐               │ 
│                            │ Heartbeat Task   │               │
│                            │ (periodic)       │               │
│                            └──────────────────┘               │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

---

## Implementing a Custom Backend

### Step-by-Step Guide

#### 1. Create Backend Configuration

```java
package com.example.custom;

import fr.traqueur.sovereign.api.config.BackendConfig;

public record CustomElectionConfig(
    CustomClient client,
    String coordinationPath
) implements BackendConfig {

    @Override
    public boolean isValid() {
        return client != null && coordinationPath != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private CustomClient client;
        private String coordinationPath = "/sovereign/leader";

        public Builder client(CustomClient client) {
            this.client = client;
            return this;
        }

        public Builder coordinationPath(String path) {
            this.coordinationPath = path;
            return this;
        }

        public CustomElectionConfig build() {
            return new CustomElectionConfig(client, coordinationPath);
        }
    }
}
```

#### 2. Implement LeaderElection

```java
package com.example.custom;

import fr.traqueur.sovereign.api.LeaderElection;
import fr.traqueur.sovereign.api.State;
import fr.traqueur.sovereign.api.config.LeaderElectionConfig;
import fr.traqueur.sovereign.api.events.*;
import fr.traqueur.sovereign.api.listeners.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class CustomLeaderElection implements LeaderElection {

    private final String instanceId;
    private final CustomClient client;
    private final EventBus eventBus;
    private final AtomicReference<State> currentState;
    private final ScheduledExecutorService scheduler;

    protected CustomLeaderElection(
            String instanceId,
            CustomClient client,
            ScheduledExecutorService scheduler,
            LeaderElectionConfig<CustomElectionConfig> config) {
        this.instanceId = instanceId;
        this.client = client;
        this.scheduler = scheduler;
        this.currentState = new AtomicReference<>(State.FOLLOWER);
        this.eventBus = new EventBus(scheduler, config.logger());
    }

    @Override
    public CompletableFuture<Void> start() {
        // Implement your election logic
        return CompletableFuture.runAsync(() -> {
            // Start periodic tasks, etc.
        }, scheduler);
    }

    @Override
    public CompletableFuture<Void> stop() {
        // Cleanup resources
        eventBus.shutdown();
        return CompletableFuture.completedFuture(null);
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

    @Override
    public <T extends Event> ListenerRegistration on(
            Class<T> eventType,
            Listener<T> listener,
            boolean async) {
        return eventBus.addListener(listener, eventType, async);
    }

    private void transitionTo(State newState) {
        State previous = currentState.getAndSet(newState);
        if (previous != newState) {
            eventBus.publishEvent(new StateChangedEvent(
                instanceId,
                System.currentTimeMillis(),
                previous,
                newState
            ));

            if (newState == State.LEADER) {
                eventBus.publishEvent(new LeadershipAcquiredEvent(
                    instanceId,
                    System.currentTimeMillis()
                ));
            } else if (previous == State.LEADER) {
                eventBus.publishEvent(new LeadershipLostEvent(
                    instanceId,
                    System.currentTimeMillis()
                ));
            }
        }
    }
}
```

#### 3. Create Provider (SPI Implementation)

```java
package com.example.custom;

import fr.traqueur.sovereign.api.LeaderElectionProvider;
import fr.traqueur.sovereign.api.config.LeaderElectionConfig;

import java.util.concurrent.ScheduledExecutorService;

public record CustomLeaderElectionProvider()
        implements LeaderElectionProvider<CustomLeaderElection, CustomElectionConfig> {

    @Override
    public Class<CustomElectionConfig> getType() {
        return CustomElectionConfig.class;
    }

    @Override
    public CustomLeaderElection create(
            String instanceId,
            ScheduledExecutorService scheduler,
            LeaderElectionConfig<CustomElectionConfig> config) {
        return new CustomLeaderElection(
            instanceId,
            config.additionalConfig().client(),
            scheduler,
            config
        );
    }
}
```

#### 4. Register via ServiceLoader

Create file: `src/main/resources/META-INF/services/fr.traqueur.sovereign.api.LeaderElectionProvider`

```
com.example.custom.CustomLeaderElectionProvider
```

#### 5. Usage

```java
// Configure backend
CustomElectionConfig customConfig = CustomElectionConfig.builder()
    .client(myClient)
    .coordinationPath("/my-app/leader")
    .build();

// Configure election
LeaderElectionConfig<CustomElectionConfig> config =
    LeaderElectionConfig.configureFor(customConfig)
        .leaderTtl(Duration.ofSeconds(30))
        .build();

// Create instance (auto-discovers CustomLeaderElectionProvider!)
LeaderElection election = LeaderElectionFactory.create(
    "instance-1",
    scheduler,
    config
);

election.start().join();
```

---

## Design Patterns Summary

| Pattern | Usage | Benefit |
|---------|-------|---------|
| **Service Provider Interface** | Backend discovery | Zero-config plugin system |
| **Factory Pattern** | Instance creation | Centralized instantiation logic |
| **Builder Pattern** | Configuration | Fluent, type-safe config |
| **Generic Type Parameters** | Type safety | Compile-time backend matching |
| **Observer Pattern** | Event system | Decoupled state monitoring |
| **State Pattern** | Election states | Clear state transitions |
| **Template Method** | LeaderElection interface | Consistent lifecycle API |

---

## Thread Safety Guarantees

- **Atomic State:** `AtomicReference<State>` for current state
- **Concurrent Collections:** `CopyOnWriteArrayList` for listeners
- **Async Operations:** All state changes via `CompletableFuture`
- **Immutable Events:** All event classes are records (immutable)
- **Thread-Safe Provider Map:** `ConcurrentHashMap` in factory

---

## Module Dependencies

```
┌─────────────────────────────────────────┐
│          Application Module             │
│  (depends on: core + specific backend)  │
└───────────────┬─────────────────────────┘
                │
    ┌───────────┼───────────┐
    ▼           ▼           ▼
┌────────┐  ┌────────┐  ┌─────────┐
│  Core  │  │ Redis  │  │ZooKeeper│
│        │  │        │  │         │
│ (API)  │◄─┤ (impl) │  │ (impl)  │
│        │  │        │  │         │
└────────┘  └────────┘  └─────────┘
    │
    │ depends on
    ▼
┌────────┐
│ SLF4J  │
└────────┘
```

**Core Dependencies:**
- SLF4J API (logging abstraction only)

**Backend Dependencies:**
- Core module (provided)
- Backend-specific client libraries (e.g., Lettuce for Redis)

---

## References

- **Core API:** `fr.traqueur.sovereign.api.*`
- **Event System:** `fr.traqueur.sovereign.api.events.*`
- **Listeners:** `fr.traqueur.sovereign.api.listeners.*`
- **Config:** `fr.traqueur.sovereign.api.config.*`
- **Redis Implementation:** See `redis/ARCHITECTURE.md`