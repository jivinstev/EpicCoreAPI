# Cascade decisions (append-only; tools/fork-branches.py cascade)

| date | decision | file | version | choice | by | reasoning |
|---|---|---|---|---|---|---|
| 2026-10-21 | b5f6bc8b342f | `src/main/java/net/eca/util/health/health_lock/HealthLockManager.java` | 26.2 | patch | default | Default patch (fixer): the authors' health-lock fast paths taken (HealthLockPresence early-outs and the HEAL_BAN_IDS.isEmpty() check); our 26.2 port change (EntityIds.peek for entities without an assigned id, CATALOG V104) kept, the new isEmpty() line inserted above it. |
