# PostgreSQL Advisory Locks

*Date: 2026-09-12 | Author: hstefanov1*

## Key Characteristics

- Each lock consumes a shared memory. If we generate many distinct keys, we can exhaust the pool.
- Advisory locks don't cross databases. We must ensure we use the same database for leader election (worker node).

## Two lock types

1. **session:** until unlock or disconnect.
2. **transaction:** auto-releases on commit/rollback.

## Two lock holders

1. **shared:** many holders.
2. **exclusive:** one holder.

## Acquire lock types

- **blocking:** waits until the lock is available.
- **non-blocking:** try and return immediately.
- Inspect who holds what via the `pg_locks` view.

## Relevant Functions

`pg_try_advisory_lock(key1 int, key2 int)`

Gets an exclusive session-level advisory lock if available. This will either get the lock immediately and return `true`,
or return `false` without waiting if the lock cannot be acquired immediately.

`pg_advisory_unlock(key1 int, key2 int)`

Releases a previously acquired exclusive session-level advisory lock. Returns `true` if the lock is successfully
released. If the lock was not held, `false` is returned, and in addition, an SQL warning will be reported by the server.

## Conclusion

- **Use lock type:** session (until unlock or disconnect)
- **Use lock holder:** exclusive (one holder)
- **Acquire lock type:** non-blocking (try and return immediately)
