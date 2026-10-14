# Residue: measure

Stopped at **measure**: the compile does not count: PARSE ABORT -- 8 of 8 errors are SYNTAX. javac stopped before attribution, so this is NOT a count: a rewrite broke a file. Offending files: mv/java/net/eca/util/health/health_lock/HealthLockManager.java:212: error: ')' expected mv/java/net/eca/util/health/health_lock/HealthLockManager.java:212: error: ';' expected mv/java/net/eca/util/health/health_lock/HealthLockManager.java:212: error: -> expected mv/java/net/eca/util/health/health_lock/HealthLockManager.java:212: error: illegal start of mv/java/net/eca/util/health/health_lock/HealthLockManager.java:356: error: ')' expected mv/java/net/eca/util/health/health_lock/HealthLockManager.java:356: error: -> expected.

## Remaining compile errors (compile-summary.py, grouped by CATALOG entry / family)

```
PARSE ABORT -- 8 of 8 errors are SYNTAX. javac stopped before attribution, so this is NOT a count: a rewrite broke a file. Offending files: mv/java/net/eca/util/health/health_lock/HealthLockManager.java:212: error: ')' expected mv/java/net/eca/util/health/health_lock/HealthLockManager.java:212: error: ';' expected mv/java/net/eca/util/health/health_lock/HealthLockManager.java:212: error: -> expected mv/java/net/eca/util/health/health_lock/HealthLockManager.java:212: error: illegal start of mv/java/net/eca/util/health/health_lock/HealthLockManager.java:356: error: ')' expected mv/java/net/eca/util/health/health_lock/HealthLockManager.java:356: error: -> expected
```

## To resume

`python3 tools/fork-port.py --repo . --target 26.1.2 --from-stage measure --budget 15.0 --gates ab`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
