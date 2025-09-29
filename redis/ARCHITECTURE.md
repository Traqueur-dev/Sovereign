# Redis Backend Architecture

This document describes the Redis-based implementation of the Sovereign leader election library.

## Table of Contents

1. [Overview](#overview)
2. [Redis Data Model](#redis-data-model)
3. [Election Algorithm](#election-algorithm)
4. [Race Condition Prevention](#race-condition-prevention)
5. [Heartbeat Mechanism](#heartbeat-mechanism)
6. [State Transitions](#state-transitions)
7. [Failure Scenarios](#failure-scenarios)
8. [Component Diagram](#component-diagram)

---

## Overview

The Redis backend uses Redis as a distributed coordination service for leader election. It leverages Redis's atomic operations (SETNX, EXPIRE) and TTL mechanisms to implement a robust leader election algorithm.

**Key Features:**
- Atomic leader acquisition using `SET NX EX`
- TTL-based leader expiration
- Heartbeat-based health monitoring
- Race condition prevention via double verification
- Automatic failover on leader failure

---

## Redis Data Model

The implementation uses two types of Redis keys:

```
┌─────────────────────────────────────────────────────────┐
│                    Redis Key Space                      │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  1. Leader Key:  "sovereign:leader"                     │
│     ├─ Value: <instance-id>                             │
│     └─ TTL:   30 seconds (configurable)                 │
│                                                         │
│  2. Heartbeat Keys: "sovereign:heartbeat:<instance-id>" │
│     ├─ Value: <timestamp-millis>                        │
│     └─ TTL:   35 seconds (leader_ttl + 5s)              │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### Leader Key

- **Purpose:** Holds the ID of the current leader
- **Atomicity:** Acquired using `SET key value NX EX ttl`
- **TTL:** Automatically expires if not renewed by leader

### Heartbeat Keys

- **Purpose:** Track leader health with timestamp updates
- **Pattern:** One key per instance (`sovereign:heartbeat:<instance-id>`)
- **Usage:** Followers check leader's heartbeat to detect staleness
- **Cleanup:** Stale heartbeats are cleaned up by new leader on acquisition

---

## Election Algorithm

### Initial Election Flow

```
┌──────────────┐
│   Instance   │
│   Starts     │
└──────┬───────┘
       │
       ├─► Random Delay (0-10s)  ◄── Prevents thundering herd
       │
       ▼
┌──────────────────┐
│  Attempt SETNX   │
│  on leader key   │
└──────┬───────────┘
       │
       ├─────► SUCCESS ──┐
       │                 │
       └─────► FAILED ───┼──► Become FOLLOWER
                         │
                         ▼
                  ┌──────────────┐
                  │   Verify     │  ◄── Double-check actual leader
                  │   GET key    │
                  └──────┬───────┘
                         │
                         ├─► Matches? ──► Become LEADER
                         │
                         └─► Conflict ──► Become FOLLOWER
```

### Election Steps (from RedisLeaderElection.java:267-306)

1. **Attempt Election:** Execute `SET NX EX` on leader key
2. **Double Verification:** Re-read key to confirm we are leader
3. **State Transition:** Transition to LEADER if confirmed
4. **Cleanup:** Remove stale heartbeat keys from previous elections

---

## Race Condition Prevention

Multiple strategies prevent race conditions during simultaneous elections:

### 1. Random Startup Delay

```java
int randomDelay = random.nextInt(10);  // 0-10 seconds
```

Instances wait a random period before participating, spreading out election attempts.

### 2. Double Verification Pattern

```
Time ─────────────────────────────────────────────►

Instance A:  SET NX ✓  ───┐
                          ├──► GET = "A" ✓ → LEADER
Instance B:  SET NX ✗     │
             GET = "A"  ◄─┘ → FOLLOWER
```

Even if SETNX succeeds, the instance verifies it's still the leader before transitioning.

### 3. Atomic Redis Operations

All critical operations use Redis's atomic commands:
- `SET key value NX EX ttl` - Atomic test-and-set with expiration
- `EXPIRE key ttl` - Atomic TTL renewal
- `DEL key` - Atomic key deletion

---

## Heartbeat Mechanism

Leaders continuously send heartbeats to prove their health:

```
                    Leader Instance
                          │
    ┌─────────────────────┼─────────────────────┐
    │                     │                     │
    │  Heartbeat Task     │   Election Task     │
    │  (every 10s)        │   (every 15s)       │
    │                     │                     │
    ▼                     ▼                     ▼
SETEX heartbeat     EXPIRE leader         GET leader
  timestamp            key TTL              key value
    │                     │                     │
    └─────────────────────┴─────────────────────┘
                          │
                          ▼
                     Redis Server
```

### Heartbeat Configuration (from RedisLeaderElection.java:184-189)

- **Frequency:** Every 10 seconds (configurable via `heartbeatInterval`)
- **TTL:** Leader TTL + 5 seconds (e.g., 35s for 30s leader TTL)
- **Content:** Current timestamp in milliseconds

### Staleness Detection (from RedisLeaderElection.java:329-354)

Followers check leader heartbeat age:

```java
if (heartbeatAge > (leaderTTL + 5) * 1000L) {
    // Leader is stale, attempt takeover
    attemptElection();
}
```

---

## State Transitions

Instances transition between three states:

```
                  ┌───────────┐
                  │ FOLLOWER  │ ◄── Initial State
                  └─────┬─────┘
                        │
                        │ No leader found OR
                        │ Leader heartbeat stale
                        ▼
                  ┌───────────┐
                  │CANDIDATE  │ ◄── Attempting election
                  └─────┬─────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
        ▼               ▼               ▼
   SETNX success   SETNX failed   Another leader
        │               │           detected
        │               │               │
        ▼               └───────┬───────┘
  ┌──────────┐                 │
  │  LEADER  │                 │
  └────┬─────┘                 │
       │                       │
       │ TTL expires OR        │
       │ Manual stop           │
       └───────────────────────┼───────►
                               │
                               ▼
                         ┌───────────┐
                         │ FOLLOWER  │
                         └───────────┘
```

### Events Published (from RedisLeaderElection.java:437-463)

State transitions trigger events:

| Transition | Events Published |
|-----------|------------------|
| `* → LEADER` | `StateChangedEvent`, `LeadershipAcquiredEvent` |
| `LEADER → *` | `StateChangedEvent`, `LeadershipLostEvent` |
| `* → FOLLOWER` | `StateChangedEvent` |
| Election Failed | `ElectionFailedEvent` |

---

## Failure Scenarios

### Scenario 1: Leader Crashes

```
Timeline ──────────────────────────────────────────────►

Leader A:   ─────X (crash)
            Leader key expires after 30s
                               │
Follower B: ───────────────────┼──► Detect no leader
                               │    Attempt election ✓
                               │    Become LEADER
                               │
Follower C: ───────────────────┼──► Detect no leader
                                    Attempt election ✗
                                    Remain FOLLOWER
```

**Recovery Time:** Max `leaderTTL` + `electionInterval` (e.g., 30s + 15s = 45s)

### Scenario 2: Leader Network Partition

```
Network Split
    │
    ├─ [Leader A] ───X──► Cannot renew TTL
    │                     Leader key expires
    │                     State: still thinks it's LEADER
    │
    └─ [Followers B,C] ──► Detect stale heartbeat
                          New election occurs
                          B becomes LEADER
```

**Split-Brain Prevention:** Redis acts as coordinator; partitioned leader cannot renew TTL

### Scenario 3: Heartbeat Stale but Leader Key Present

```
Leader A: SETEX heartbeat fails (network glitch)
          Leader key still present (TTL renewed via election task)

Follower B: Detects stale heartbeat (> leaderTTL + 5s)
            Attempts takeover
            └─► SETNX fails (leader key exists)
            └─► Re-checks heartbeat
            └─► If still stale after threshold, forcibly attempts election
```

---

## Component Diagram

### Class Structure

```
┌─────────────────────────────────────────────────────────────┐
│                  fr.traqueur.sovereign.impl.redis           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌────────────────────────────────────────────┐             │
│  │   RedisLeaderElectionProvider              │             │
│  │   implements LeaderElectionProvider        │             │
│  ├────────────────────────────────────────────┤             │
│  │ + getType(): Class<RedisElectionConfig>    │             │
│  │ + create(...): RedisLeaderElection         │             │
│  └──────────────┬─────────────────────────────┘             │
│                 │ creates                                   │
│                 ▼                                           │
│  ┌────────────────────────────────────────────┐             │
│  │   RedisLeaderElection                      │             │
│  │   implements LeaderElection                │             │
│  ├────────────────────────────────────────────┤             │
│  │ - instanceId: String                       │             │
│  │ - redisCommands: RedisAsyncCommands        │             │
│  │ - eventBus: EventBus                       │             │
│  │ - scheduler: ScheduledExecutorService      │             │
│  │ - currentState: AtomicReference<State>     │             │
│  │ - isRunning: AtomicBoolean                 │             │
│  │ - electionTask: ScheduledFuture<?>         │             │
│  │ - heartbeatTask: ScheduledFuture<?>        │             │
│  ├────────────────────────────────────────────┤             │
│  │ + start(): CompletableFuture<Void>         │             │
│  │ + stop(): CompletableFuture<Void>          │             │
│  │ + isLeader(): boolean                      │             │
│  │ + getState(): State                        │             │
│  │ + on(...): ListenerRegistration            │             │
│  ├────────────────────────────────────────────┤             │
│  │ - runElectionCycle()                       │             │
│  │ - runHeartbeat()                           │             │
│  │ - attemptElection()                        │             │
│  │ - renewLeaderTTL()                         │             │
│  │ - checkLeaderHealth(String)                │             │
│  │ - sendHeartbeat()                          │             │
│  │ - releaseLeadership()                      │             │
│  │ - cleanupOldHeartbeats()                   │             │
│  │ - transitionTo(State)                      │             │
│  └────────────────────────────────────────────┘             │
│                 │ uses                                      │
│                 ▼                                           │
│  ┌────────────────────────────────────────────┐             │
│  │   RedisElectionConfig                      │             │
│  │   implements BackendConfig                 │             │
│  ├────────────────────────────────────────────┤             │
│  │ + redisCommands: RedisAsyncCommands        │             │
│  │ + leaderKey: String                        │             │
│  │ + heartbeatKeyPrefix: String               │             │
│  ├────────────────────────────────────────────┤             │
│  │ + builder(): Builder                       │             │
│  │ + isValid(): boolean                       │             │
│  └────────────────────────────────────────────┘             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Periodic Task Flow

```
┌──────────────────────────────────────────────────────────────┐
│                    RedisLeaderElection                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ScheduledExecutorService                                    │
│       │                                                      │
│       ├──► Election Task (every 15s)                         │
│       │    └─► runElectionCycle()                            │
│       │        └─► masterElection()                          │
│       │            ├─► No leader? → attemptElection()        │
│       │            ├─► We are leader? → renewLeaderTTL()     │
│       │            └─► Other leader? → checkLeaderHealth()   │
│       │                                                      │
│       └──► Heartbeat Task (every 10s)                        │
│            └─► runHeartbeat()                                │
│                └─► if (isLeader()) sendHeartbeat()           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Configuration Example

```java
// Create Redis client
RedisClient redisClient = RedisClient.create("redis://localhost:6379");
StatefulRedisConnection<String, String> connection = redisClient.connect();
RedisAsyncCommands<String, String> commands = connection.async();

// Configure Redis backend
RedisElectionConfig redisConfig = RedisElectionConfig.builder()
    .redisCommands(commands)
    .leaderKey("my-app:leader")
    .heartbeatKeyPrefix("my-app:heartbeat:")
    .build();

// Configure election parameters
LeaderElectionConfig<RedisElectionConfig> config = LeaderElectionConfig
    .configureFor(redisConfig)
    .instanceId("instance-1")
    .leaderTtl(Duration.ofSeconds(30))
    .electionInterval(Duration.ofSeconds(15))
    .heartbeatInterval(Duration.ofSeconds(10))
    .build();

// Create election instance
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
LeaderElection election = LeaderElectionFactory.create("instance-1", scheduler, config);

// Register listeners
election.onLeadershipAcquired(event -> {
    System.out.println("I am now the leader!");
}, true);

// Start election
election.start().join();
```

---

## Performance Considerations

### Redis Operations per Cycle

**When FOLLOWER:**
- 1 × `GET` (check leader key)
- 1 × `GET` (check leader heartbeat)

**When LEADER:**
- 1 × `EXPIRE` (renew leader TTL)
- 1 × `SETEX` (send heartbeat)

**Total:** ~4 Redis operations per instance per election cycle

### Network Traffic

With default configuration (15s election, 10s heartbeat):
- **Follower:** ~8 ops/minute
- **Leader:** ~10 ops/minute

### Failover Time

| Scenario              | Recovery Time                         |
|-----------------------|---------------------------------------|
| Clean leader shutdown | ~`electionInterval` (15s)             |
| Leader crash          | ~`leaderTTL + electionInterval` (45s) |
| Network partition     | ~`leaderTTL + electionInterval` (45s) |

---

## Thread Safety

- **Atomic Operations:** State managed via `AtomicReference` and `AtomicBoolean`
- **Async Redis:** All Redis operations executed asynchronously via Lettuce
- **Concurrent Collections:** EventBus uses `CopyOnWriteArrayList` for listeners
- **Scheduled Tasks:** Coordinated via single `ScheduledExecutorService`

---

## References

- **Implementation:** `RedisLeaderElection.java`
- **Configuration:** `RedisElectionConfig.java`
- **Provider:** `RedisLeaderElectionProvider.java`
- **Core API:** See `core/ARCHITECTURE.md`