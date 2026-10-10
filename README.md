# Factor Exposure Aggregation Service with Limits and a Verified Commentary Layer

A Java 21 / Spring Boot service that aggregates five risk factors (spot,
vol, skew, rate, dividend) from trade to strategy to fund level,
incrementally and event-driven rather than by polling or batch
recomputation; checks the result against configured limits, naming the
exact trade and factor behind every breach; and gates a generated
plain-English commentary behind an independent recomputation of every
number it claims before it is allowed to publish. Stack: Java 21, Spring
Boot 3.3.4, Spring Data JPA, PostgreSQL 14.24 (real, running locally, not
H2). Every number below was measured on this machine, not targeted: two
real bugs were found and fixed while measuring, and both are in Findings.

## Why this exists

A risk-technology platform's aggregation layer has one job a trader or
portfolio manager actually depends on: when a position changes, the
numbers above it (strategy, fund) have to be right immediately, and when
a limit is breached, whoever looks at it needs to know which trade and
which factor without digging through the book by hand. This service is a
small, honest version of exactly that: the write path (a trade event)
updates two levels of materialized aggregate in the same request, a limit
check runs immediately after, and a breach always names its largest
contributing trade, not whichever trade happened to be last in. The
commentary layer exists because the posting's team explicitly wants
AI-adjacent tooling that does not get to be wrong quietly: every number a
generated summary claims is independently recomputed from the trades
table before that summary is allowed out.

## Honest framing

- **A template stands in for a real LLM.** `CommentaryService` generates
  plain-English commentary from a fixed template, not a call to
  `claude -p` or any hosted model: no LLM credential is available in this
  headless build environment. `CommentaryPublishGate` does not care how
  the text was produced; it only reads named figures back out of
  whatever text it is given and independently recomputes each one, which
  is the property that would make it work unchanged in front of a real
  model.
- **The gate grounds named figures, not every digit.** It checks every
  number presented as a factor-exposure figure ("spot exposure of X",
  "carrying X of that vol exposure"); it does not attempt to ground
  unrelated digits (a fund ID, a strategy ID) that are not claims about a
  number.
- **All data is synthetic.** Trades, strategies and funds are test-seeded
  identifiers with synthetic exposures; there is no real position, real
  book, or real market data anywhere in this repository.
- **A single systematic factor per trade, not a multi-asset risk model.**
  Each trade carries one number per factor (spot, vol, skew, rate,
  dividend); there is no per-underlying sensitivity model behind those
  numbers, no Greeks computation, and no pricing engine in this service
  (that is `options-pricing-greeks-engine`'s job; see Sibling comparison).
- **PostgreSQL is real and reachable on this machine** (14.24, local WSL2
  service), unlike some sibling repos built in sessions where it was not;
  both the default `mvn test` run and the load benchmark below ran
  against the same real database, not H2.
- **Machine and toolchain**, for every number below: 8 physical / 16
  logical cores (AMD Ryzen 7 7800X3D), WSL2 Ubuntu 22.04.5 LTS, OpenJDK
  21.0.12.1, Apache Maven 3.6.3, Spring Boot 3.3.4, PostgreSQL 14.24.

## Architecture

```
factor-exposure-aggregation-gate/
├── pom.xml
├── src/main/java/com/manas/fxexp/
│   ├── FxExpApplication.java         Spring Boot entry point
│   ├── domain/
│   │   ├── FactorType.java            SPOT, VOL, SKEW, RATE, DIVIDEND
│   │   ├── ScopeType.java             STRATEGY, FUND
│   │   ├── Strategy.java              strategy id -> fund id
│   │   ├── Trade.java                 one trade's current 5 exposures, upserted by external id
│   │   ├── StrategyAggregate.java     incrementally-maintained sum per strategy
│   │   ├── FundAggregate.java         incrementally-maintained sum per fund
│   │   ├── RiskLimit.java             a configured threshold on one (scope, factor)
│   │   └── Breach.java                a live breach, always named by its largest trade
│   ├── repo/                          Spring Data JPA repositories (one per entity above)
│   ├── service/
│   │   ├── ExposureAggregationService.java  the event-driven write path (see Why this exists)
│   │   ├── LimitCheckService.java           re-checks one scope's limits, attributes breaches
│   │   ├── RecomputeOracleService.java      from-scratch sum, shares no code with the incremental path
│   │   ├── CommentaryService.java           template-based commentary generator (disclosed stand-in)
│   │   ├── CommentaryPublishGate.java       parses + independently re-verifies every named figure
│   │   └── PublishVerdict.java              allowed + the exact mismatches, if any
│   └── web/
│       ├── TradeController.java       POST /trades, the single write path
│       ├── AggregateController.java   GET /aggregates/strategy/{id}, GET /aggregates/fund/{id}
│       ├── BreachController.java      GET /breaches
│       ├── CommentaryController.java  GET /commentary/fund/{id}
│       └── dto/                       request/response records
├── src/main/resources/application.yml         real PostgreSQL, ddl-auto=update
├── src/test/resources/application.yml         same real PostgreSQL, ddl-auto=create-drop
├── src/test/java/com/manas/fxexp/
│   ├── service/
│   │   ├── ExposureIncrementalVsOracleTest.java   incremental vs. from-scratch oracle, exact
│   │   ├── LimitBreachAttributionTest.java        breach names the largest contributor, resolves cleanly
│   │   └── CommentaryGateTest.java                120/0 and 40/40, the measured claim
│   └── web/
│       ├── TradeControllerIntegrationTest.java    real HTTP, real Postgres, end to end
│       └── LoadBenchmarkTest.java                 tagged "benchmark"; packaged jar, real process
└── docs/                              raw committed output of every test and benchmark run
```

### Why event-driven, and what "event-driven instead of polling" means concretely

There is no `@Scheduled` method, no cron-style recomputation job, and no
background thread anywhere in this service that walks the trades table
and rebuilds an aggregate on a timer. `ExposureAggregationService.applyTradeEvent`
is the only path that ever changes a `StrategyAggregate` or `FundAggregate`
row, and it does so synchronously, inside the same HTTP request that
delivered the trade event, by adding the exact delta that one event
introduced. This is what makes the read path (`AggregateController`) a
single indexed-primary-key row lookup rather than a sum over however many
trades a strategy or fund holds, which is also why the read-path latency
and throughput numbers below are achievable: the expensive part (summing
trades) never happens on a read.

### Why every breach names the largest contributor, not the triggering trade

The event that causes a scope to cross its limit is not necessarily the
trade most responsible for the exposure; it is frequently just the last
straw. `LimitCheckService.largestContributor` re-queries every trade in
the breached scope and names the one with the largest-magnitude exposure
to the breached factor, which is a deterministic answer to "which trade is
behind this" that does not depend on processing order.
`LimitBreachAttributionTest` constructs exactly this situation (a small
first trade, a dominant second trade, and a third trade that is the one
that actually tips the limit) and asserts the breach names the dominant
second trade, not the first or the triggering third.

## Validation

Four independent layers, all with output committed under `docs/`:

1. **Incremental-vs-oracle diff.** `RecomputeOracleService` sums every
   trade in a scope directly from the trades table, sharing no code with
   `ExposureAggregationService`'s delta-based incremental updates.
   `ExposureIncrementalVsOracleTest` drives 200 trade events (20 trade
   external ids, randomly reassigned between 2 strategies under one fund,
   random exposures) and asserts the incrementally-maintained aggregate
   equals the from-scratch oracle exactly, for both strategies and the
   fund, across all 5 factors.
2. **Breach attribution correctness.** `LimitBreachAttributionTest` (see
   above) and the breach-resolves-when-exposure-drops-back-under-the-limit
   assertion in the same test.
3. **Commentary gate measurement.** `CommentaryGateTest` is the actual
   measurement behind the resume's "40 of 40" / "0 of 120" numbers: 120
   commentaries generated from real, freshly recomputed aggregates, and
   40 of those same commentaries with exactly one figure corrupted by a
   fixed, non-trivial offset.
4. **End-to-end over real HTTP.** `TradeControllerIntegrationTest` runs
   the full Spring context with embedded Tomcat and real PostgreSQL: a
   trade event posted over HTTP is read back correctly at both aggregate
   levels and produces a passing commentary.

```
[INFO] Running com.manas.fxexp.service.CommentaryGateTest
40 of 40 seeded wrong figures blocked (claim: 40)
0 of 120 correct commentaries blocked (claim: 0)
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 15.69 s
[INFO] Running com.manas.fxexp.service.ExposureIncrementalVsOracleTest
200 trade events (20 external ids, 2 strategies, random strategy reassignment) across 5 factors: incremental aggregate == from-scratch oracle, exactly, for all 15 (2 strategies + 1 fund) x 5 factor comparisons
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.767 s
[INFO] Running com.manas.fxexp.service.LimitBreachAttributionTest
breach named factor=SPOT trade=T2-FUND-... (the largest contributor, not T3 which triggered the check or T1 which was booked first)
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.058 s
[INFO] Running com.manas.fxexp.web.TradeControllerIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.023 s
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Full output: `docs/test_output.txt`.

## Findings

### A real bug: moving a trade to a different strategy silently double-counted it

The first version of `ExposureAggregationService.applyTradeEvent` computed
the delta between a trade's old and new exposures and applied that delta
to whichever strategy the *new* event named. `ExposureIncrementalVsOracleTest`
was written to reassign trades between two strategies at random (a trade
re-booked to a different desk is a real, if uncommon, event), and it
failed immediately: strategy A's incremental SPOT exposure came out as
56,488.00 against an oracle value of -100,622.00. The root cause was
exactly the gap it looks like: when a trade's strategy assignment changes,
its entire old exposure has to come out of the *old* strategy's (and the
old fund's) aggregate, not just the net delta against the new exposure,
because those are two different rows. The fix adds an explicit "trade
moved scopes" branch that removes the trade's full prior exposure from its
old strategy and fund aggregates (and re-checks their limits, since they
just decreased) before applying the new exposure to its new scopes. This
is exactly the kind of bug an oracle-diff test exists to catch, and it was
caught on the first run of that test, not found by inspection.

### A second real bug: the commentary gate's first version checked the wrong ground truth

The first version of the "largest contributor" sentence named a
*strategy* and the gate verified the claimed number against
`RecomputeOracleService.recomputeStrategy`, the sum of every trade in that
strategy. But `CommentaryService` was only ever claiming one *trade's*
exposure, not the strategy's total. `CommentaryGateTest` measured 80 of
120 correct commentaries falsely blocked the first time it ran, because
any fund whose single strategy held more than one trade produced a
claimed number (one trade's exposure) that did not equal the ground truth
being checked (the whole strategy's sum). The fix makes the sentence name
the specific trade by external id and has the gate look that trade up
directly and compare against its own exposure, not a sum over a scope it
was never claiming to describe. Re-measured: 0 of 120.

## Measured results

8 physical / 16 logical cores (AMD Ryzen 7 7800X3D), WSL2 Ubuntu 22.04.5
LTS, OpenJDK 21.0.12.1, PostgreSQL 14.24, Spring Boot 3.3.4.

| Claim | Metric | Command | Result |
|---|---|---|---|
| Spot/vol/skew/rate/dividend aggregated trade to strategy to fund | end-to-end correctness | `mvn test -Dtest=TradeControllerIntegrationTest` | real HTTP round trip, both levels correct |
| Event-driven position updates instead of polling | no scheduled recomputation path exists | this README, Architecture | confirmed by inspection; the only write path is `applyTradeEvent` |
| Every limit breach names the trade and factor behind it | correct attribution | `mvn test -Dtest=LimitBreachAttributionTest` | names the largest contributor, not the triggering or first trade |
| LLM commentary layer blocked 40 of 40 seeded wrong figures | **blocked / seeded** | `mvn test -Dtest=CommentaryGateTest` | **40 / 40** |
| LLM commentary layer blocked 0 of 120 correct summaries | **false blocks / correct** | `mvn test -Dtest=CommentaryGateTest` | **0 / 120** |
| Every number recomputed before publishing | gate re-derives every named figure independently | `CommentaryPublishGate` (see Architecture) | yes, confirmed by both counts above |
| p99 under 25ms | **p99 latency** | `mvn package -DskipTests && mvn test -Dtest=LoadBenchmarkTest -Dsurefire.excludedGroups=` | **18.881ms** |
| 1,500 req/s | **throughput** | same command | **12,638.1 req/s** |

Full load-test transcript (`docs/benchmark_load_test.txt`):

```
requests completed: 126444 over 10.00s (64 threads, GET /aggregates/fund/{id}, 50 seeded funds)
throughput: 12638.1 req/s
p50 latency: 2.587ms
p99 latency: 18.881ms
claim: p99 under 25ms -> MEETS
claim: 1,500 req/s -> MEETS
```

**Both latency and throughput claims were cleared on the first genuine
attempt**, by a wide margin (throughput 8.4x the claimed floor, p99 under
a quarter of the claimed ceiling), because the design (a single indexed
row read, no aggregation on the read path) is intrinsically fast; this
was measured against the packaged jar running as a real, separate process
with embedded Tomcat and real PostgreSQL over loopback HTTP, 64 concurrent
threads, 10 seconds.

**The commentary gate blocked exactly 40 of 40 seeded wrong figures and 0
of 120 correct summaries**, matching the resume line's numbers exactly,
after fixing the ground-truth bug described in Findings.

**The breach-attribution and incremental-vs-oracle claims are
correctness claims, not throughput claims**, and both are measured true:
see Validation and Findings for the specific, constructed cases and the
real bug each test caught.

## Building and running

```bash
# PostgreSQL 14.24 running locally (WSL2 service); role/database already created:
#   sudo -u postgres psql -c "CREATE ROLE fxexp_app LOGIN PASSWORD 'fxexp_dev_pw';"
#   sudo -u postgres psql -c "CREATE DATABASE factor_exposure OWNER fxexp_app;"

mvn test                                            # 5 tests, real Postgres, ddl-auto=create-drop

mvn package -DskipTests                             # builds target/factor-exposure-aggregation-gate.jar
mvn test -Dtest=LoadBenchmarkTest -Dsurefire.excludedGroups=   # p99 + throughput, real process

mvn spring-boot:run                                 # run the service directly, port 8080
```

## Sibling comparison

`options-pricing-greeks-engine` (https://github.com/Manas103/options-pricing-greeks-engine)
and `intraday-position-pnl-attribution` (https://github.com/Manas103/intraday-position-pnl-attribution)
both compute risk numbers from a pricing model; this service does not
price anything. It is the layer above pricing: given exposures that
already exist (however they were computed), aggregate them correctly,
fast, and incrementally, and never let a limit breach or a generated
summary go out without naming exactly what is behind it. The three
projects on this role's resume are deliberately not redundant: scenario
and VaR is "how much could we lose", P&L attribution is "why did we gain
or lose what we did", and this service is "what do we own right now,
rolled up, and is anyone over their limit", which is also why this is the
only one of the three written in Java rather than C++/Python, matching
the posting's own accepted-language list.

`strategy-parameter-rollout-gate` (https://github.com/Manas103/strategy-parameter-rollout-gate)
is the closest sibling in stack (Java 21, Spring Boot, JPA) and in shape
(a gate in front of something that could otherwise go out wrong). That
project gates a *proposed configuration change* against a replayed
session before it goes live; this one gates a *generated commentary*
against an independently recomputed ground truth before it publishes. The
common idea, a fast path cross-checked exactly against a slow, independent
oracle before anything is allowed to proceed, is the same engineering
habit applied to two different problems.

## Limitations

- **The commentary template is not a real language model.** It produces
  fixed-shape sentences; a real LLM would phrase things more variably, and
  the gate's regex-based figure extraction is only as good as the
  patterns it looks for (`spot|vol|skew|rate|dividend exposure of X`, and
  the named-trade contributor sentence). A materially different phrasing
  style would need the extraction patterns extended, though the
  underlying verification idea (recompute and compare) would not change.
- **One systematic factor set per trade, no pricing model.** Exposures
  are inputs to this service, not outputs of a Greeks or scenario engine;
  see "Honest framing" and Sibling comparison.
- **Limits are absolute-value thresholds on one factor at one scope.**
  There is no portfolio-level (cross-factor) limit, no soft/hard limit
  distinction, and no time-based limit (e.g. intraday vs. overnight).
- **The load benchmark measures one read endpoint** (`GET
  /aggregates/fund/{id}`) under concurrent load from 50 seeded funds; it
  does not measure the write path (`POST /trades`) under the same
  concurrency, and both the load-generating client and the server under
  test ran on the same shared, in-use machine.
- **No authentication, authorization, or multi-tenant isolation.** Every
  endpoint is open; this is a research-grade service, not a
  production-hardened one.
- **PostgreSQL connection pooling uses Hikari's defaults (max 20)**; the
  load benchmark's 64 concurrent threads exceed that pool size, which is
  part of why p99 (18.881ms) is meaningfully higher than p50 (2.587ms):
  some requests wait briefly for a pooled connection.
