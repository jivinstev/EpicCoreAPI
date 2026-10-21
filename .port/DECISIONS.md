# Cascade decisions (append-only; tools/fork-branches.py cascade)

| date | decision | file | version | choice | by | reasoning |
|---|---|---|---|---|---|---|
| 2026-10-21 | ae96b62d8444 | `src/main/java/net/eca/util/raid/RaidInstance.java` | 26.3 | patch | default | Default patch (fixer): the authors' raid glow / bell-teleport change taken whole, in the port branch's names and adapted through 26.3's rows (getMin/MaxBuildHeight -> getMinY/getMaxY()+1 etc.); the only conflict is the import block, where our 26.3 Holder import (the 26.3 structure-lookup port change) is kept and Direction added beside it. Over the 80-line budget only by the size of the authors' ne |
