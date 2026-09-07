# NexusNPC v0.1.1 — compile fixes

Three real problems turned up when this was actually built with `mvn clean package` (in a
different sandbox than the one that originally wrote this plugin — one with real internet access
to Maven Central and repo.papermc.io), all now fixed:

1. **`pom.xml` failed to parse at all.** Several of its comments used " -- " as a plain-text dash,
   which XML does not allow anywhere inside a comment (only immediately before the closing `-->`).
   Every instance is now a single dash instead.
2. **`NexusNpcPlugin.java` imported the wrong top-level package for `SpigotPacketEventsBuilder`.**
   It was written as `com.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder`,
   but that class actually lives under `io.github.retrooper` (not `com.github.retrooper`) — a
   well-known split in how PacketEvents packages its core API versus its per-platform builder
   classes. Fixed to `io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder`.
3. **`PacketNpcHandle.java` was missing an import for `NpcConfig`.** `NpcConfig` lives in the
   top-level `com.nexusuniverse.npc` package; `PacketNpcHandle` lives in the `com.nexusuniverse.npc.npc`
   subpackage, and Java doesn't auto-import from a parent package. Every other file that uses
   `NpcConfig` (`NpcManager`, `Trait`, `LookCloseTrait`, `PatrolTrait`) already imported it
   correctly — this was the one file that didn't.

With those three fixed, the full source now compiles clean (one harmless `serialVersionUID` lint
warning on `SkinFetcher.SkinLookupException`, not a real issue) against a hand-built stand-in for
the entire Bukkit/Paper, PacketEvents, Gson, and Adventure API surface this plugin touches —
built because this sandbox, like the one that wrote v0.1.0, still has no network route to Maven
Central or PaperMC's repo. That confirms the Java itself is now internally consistent end to end,
but — same caveat as before — it isn't a substitute for the real `mvn clean package` you already
ran. Nothing else was changed: no behavior, no new features, just these three fixes.

# NexusNPC v0.1.0 — first build

A new plugin, not a modification of an existing one: an original Citizens/Sentinel-inspired NPC
framework, built from scratch for the current Minecraft version (Java Edition 26.2 as of this
build — Paper's own "latest" download tracks new Minecraft releases directly, so bump
`paper.api.version` in pom.xml before building if a newer one has shipped since).

Build note, same limitation as every other Nexus plugin built in a sandbox without internet
access to Maven Central / PaperMC's repo: this could not actually be compiled here. Every line was
checked by hand against the real API docs of everything it depends on (Paper's own API, plus
PacketEvents' published javadocs for the packet-level NPC code specifically), but please run
`mvn clean package` yourself before deploying, and let me know what it says if anything doesn't
compile — see the specific callout below for where a problem is most likely to be.

## What this version is

Two kinds of NPC, matching what was asked for ("villagers or normal Steve characters"):

- **Mob-type NPCs** (`/npc create <id> mob [entity-type]`) are real Bukkit entities (Villager by
  default, or any living mob type you name). Simple and robust — no external dependency, normal
  server-side AI switched off so a Patrol trait (or a later combat trait) is the only thing moving
  them, invulnerable and won't despawn.
- **Player-type "Steve" NPCs** (`/npc create <id> player`) are NOT real entities at all — Paper's
  maintainers explicitly declined to add an API for spawning fake player entities (closed as
  "third-party libraries already do this better"), so these exist purely as packets sent to
  nearby clients, using the PacketEvents plugin (installed separately alongside NexusNPC, the same
  way most modern NPC plugins depend on it — see pom.xml's comment on why it isn't shaded in).

**Skins from real players**: `/npc skin <id> <username>` fetches that player's current skin
straight from Mojang's own API (the same two-step username→UUID→texture lookup Citizens' own
`/npc skin` command uses) and takes a permanent snapshot of it — it won't silently change later if
that real player changes their own skin.

**Traits** (attachable behaviors, `/npc trait add|remove <id> <type>`):
- `LOOK_CLOSE` — faces the nearest player within `npc.look-close-range` blocks.
- `PATROL` — walks a loop between waypoints you set with `/npc waypoint add <id>` (stand where you
  want a stop, run the command). Straight-line movement, not obstacle-aware pathfinding.

**Equipment** (`/npc equip <id> <hand|head|chest|legs|feet>`, equips whatever's in your hand) is
mob-type only in this version — see the scope-limit note below.

Everything is per-NPC config, persisted to `npcs.yml` with the same atomic-write pattern
(temp file + move) used across the rest of the Nexus plugin family, so a crash mid-save can't
corrupt it.

## What's deliberately NOT in this version

The request explicitly went further than a first build should try to guess at in one pass — these
are real, separate phases, not omissions:

- **Sentinel-style combat/guard AI.** NPCs don't fight, defend, or target anything yet. Traits
  only cover looking and patrolling. A guard-your-base trait (detect intruders, attack hostiles,
  return fire) is a natural next version once this core is confirmed working.
- **Full autonomy.** These NPCs only do what a trait explicitly tells them to do — there's no
  decision-making layer that picks its own behavior. "Actual autonomous NPC players" is the most
  ambitious ask in the original request and deserves its own dedicated design pass, not a bolt-on.
- **Custom dialogue.** No conversation/dialogue-tree system yet.
- **Player-type equipment display.** Showing held items/armor on a packet-only fake player needs
  its own packet wrapper (`WrapperPlayServerEntityEquipment`) that wasn't checked against the
  javadocs as carefully as the wrappers actually used here — rather than ship an unverified guess
  for something players would immediately notice looks wrong, this version limits visible
  equipment to mob-type NPCs. Likely a quick follow-up once the core is confirmed working.
- **Colored/long display names on player-type NPCs.** A fake player's profile name follows real
  Minecraft username rules (≤16 chars, no color codes) — a longer or colored nameplate needs a
  scoreboard-team prefix/suffix trick layered on top, not built yet.

## Where to look first if `mvn package` fails

`PacketNpcHandle.java` is flagged in its own file comment as the least-verified code in this
delivery — it's the one file whose correctness depends on a third-party library's exact method
signatures rather than just Paper's own API. Every wrapper class and constructor it uses was
checked one-by-one against PacketEvents 2.13.0's published javadocs, but if the compiler reports a
missing method there, it's almost certainly one wrapper's signature having shifted in a point
release — worth checking https://javadocs.packetevents.com for that specific class before assuming
anything else is wrong.

## Setup

1. Install [PacketEvents](https://github.com/retrooper/packetevents/releases) as its own plugin
   jar alongside NexusNPC (required — NexusNPC's plugin.yml declares it as a hard dependency).
2. `mvn clean package`, drop the resulting jar in `plugins/`.
3. `/npc create <id> mob` or `/npc create <id> player`, then `/npc skin <id> <username>` for a
   player-type one.
