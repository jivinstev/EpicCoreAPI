# Epic Core API - Community Port

**Unofficial.** A community-maintained port of **Epic Core API** by **CJiangqiu** to NeoForge 1.21.1. It is not made or supported by CJiangqiu: please report problems with this port [on its own issue tracker](https://github.com/jivinstev/EpicCoreAPI/issues), not to the original author.

Published with the original author's permission (https://github.com/CJiangqiu/EpicCoreAPI/issues/9), under the original MIT licence with its copyright notice kept.

## The original

- A library mod that provides powerful entity manipulation APIs through CoreMod (ITransformationService), Java Agent, and Mixin technologies.
- Source: https://github.com/CJiangqiu/EpicCoreAPI
- On Modrinth: https://modrinth.com/mod/epic-core-api (Forge, 1.20.1)

## What this port changes

- Runs on **NeoForge 1.21.1**.
- Carries fixes for defects found while porting and playing it. Each is tracked, with its evidence and which release fixes it, in an issue:
  - [Pre-existing: force-removal aborts half-way when any chunk is loading entities (removeFromLoadingInbox throws on an immutable list)](https://github.com/jivinstev/EpicCoreAPI/issues/1)
  - [Pre-existing: summoning The Last Sword's TEST ENTITY switches off every other content mod (global AllReturn) and disconnects the player](https://github.com/jivinstev/EpicCoreAPI/issues/2)
  - [Pre-existing: with The Last Sword loaded, the JVMTI backend recurses inside its own class-load hook (hang at load or on exit)](https://github.com/jivinstev/EpicCoreAPI/issues/3)

## Requirements

- Nothing beyond NeoForge 1.21.1.

## How releases are made

Every release is built by the original author's own build from https://github.com/jivinstev/EpicCoreAPI, passes automated checks (it builds, loads on a dedicated server, runs in a real client), and is played by a person before it is published here.

## Credits

- Contributor: WZZ
- Bug reports and fix suggestions: jivinstev
- Mascot: MCreator

## Licence

MIT, copyright CJiangqiu. See LICENSE.txt in the source.
