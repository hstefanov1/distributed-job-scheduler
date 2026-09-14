# Batch Deactivation Strategy

*Date: 2026-09-11 | Author: hstefanov1*

## Options Considered

### 1. Native query (bulk update)

```sql
update ...where status = ACTIVE and end_date <= current_timestamp
```

**Advantages**

- Fastest possible option: single round trip to the DB, no entity loading, no N+1 overhead.
- Lowest memory footprint: nothing is materialized into Java objects.
- Simple to write and reason about at the SQL level.

**Disadvantages**

- Completely bypasses JPA/Hibernate: our `@PreUpdate` won't fire, so `updatedAt` stays stale unless
  set manually in the SQL.
- `@Version` is not incremented: breaks optimistic locking guarantees for anything that trusts
  version.
- Native SQL is DB-specific: less portable, no compile-time checking against our entity.
- Holds row locks on all matching rows for the duration of the transaction: can block concurrent
  membership updates on those same rows.

### 2. Hibernate/Panache bulk update

```java
`Membership.update ("status = INACTIVE , version = version + 1, updated_at = current_timestamp where status = ACTIVE and endDate <= ?1", Instant.now ());`
```

**Advantages**

- Same single-query performance as option 1, but validated against our entity model (typos fail
  fast).
- Manually bumping version keeps the counter honest for anyone reading it afterward.
- Portable across databases since it's JPQL, not native SQL.

**Disadvantages**

- Still bypasses the persistence context: our `@PreUpdate` doesn't fire, so `updatedAt` needs to be
  set manually in the query too.
- No optimistic lock check: blind-overwrites rows regardless of concurrent membership activity.
- Bumping version after the fact avoids stale numbers but doesn't prevent the race.
- Same lock-duration risk as option 1: no batching, locks held on the full matching set at once.
- Any future `@PreUpdate`/`@PrePersist` logic must be manually duplicated into the JPQL, or it's
  silently skipped.

### 3. Batched fetch + entity-managed update loop

```java
List<Membership> expired = Membership.find("status = ACTIVE and endDate <= ?1", Instant.now())
    .page(0, 500)
    .list();
expired.forEach(m -> m.status = INACTIVE); // no explicit save call needed (hibernate flushes changes on commit)
```

**Advantages**

- Entities are managed: our `@PreUpdate` fires correctly (`updatedAt` stays accurate) and `@Version`
  increments naturally.
- Optimistic locking actually works: a concurrent user edit triggers a version-mismatch exception
  instead of being silently overwritten.
- Batching means locks are held on a much smaller row set per transaction, reducing contention with
  live user traffic.
- Easiest to extend later: any new lifecycle logic on the entity is automatically respected.
- Easiest to add per-row error handling/retry, so one conflict doesn't fail the whole run.

**Disadvantages**

- Slowest option: one SELECT, then individual UPDATEs per row.
- Higher memory usage: up to 500 full entities loaded into memory per page, vs. zero materialization
  in the bulk approaches.
- More implementation complexity: needs pagination logic to cover all pages, plus ignore-on-
  `OptimisticLockException` per row.
- Could take noticeably longer to finish on a very large expired set, which may matter if we have a
  tight SLA on inactivation freshness.

- Still bypasses the persistence context: our `@PreUpdate` doesn't fire, so `updatedAt` needs to be
  set manually in the query too.
- No optimistic lock check: blind-overwrites rows regardless of concurrent membership activity.
- Bumping version after the fact avoids stale numbers but doesn't prevent the race.
- Same lock-duration risk as option 1: no batching, locks held on the full matching set at once.
- Any future `@PreUpdate`/`@PrePersist` logic must be manually duplicated into the JPQL, or it's
  silently skipped.

## Conclusion

Given our concerns about not overwriting an in-flight membership update, option 3 is the only one
that structurally protects against it. Options 1 and 2 both risk silently overwriting a concurrent
change.
