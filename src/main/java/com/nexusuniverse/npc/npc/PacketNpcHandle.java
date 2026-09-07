package com.nexusuniverse.npc.npc;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A PLAYER-type NPC: no real Bukkit entity exists at all -- this is purely packets sent to
 * whichever clients are online, via the (separately installed) PacketEvents plugin. This is by
 * far the least battle-tested file in this plugin: this sandbox has no network access to Maven
 * Central or PaperMC's repo, so none of this could actually be compiled or run here (see
 * CHANGES.md). The packet wrapper class/constructor names below were checked one-by-one against
 * PacketEvents 2.13.0's published javadocs, but if `mvn package` reports a missing method or
 * constructor here, that's almost certainly a wrapper signature that shifted in a point release
 * -- check https://javadocs.packetevents.com for the wrapper class named in the error first.
 */
public class PacketNpcHandle extends NpcInstance {

    /** Counts down from a very high int so generated fake entity IDs never collide with real,
     *  normally-incrementing-from-zero entity IDs the server itself hands out. */
    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(2_000_000_000);

    private final JavaPlugin plugin;
    private final NpcConfig config;
    private final int entityId;
    private Location location;
    private float yaw;
    private float pitch;

    public PacketNpcHandle(NpcDefinition definition, JavaPlugin plugin, NpcConfig config) {
        super(definition);
        this.plugin = plugin;
        this.config = config;
        this.entityId = NEXT_ENTITY_ID.getAndDecrement();
        this.location = definition.location();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }

    public int entityId() {
        return entityId;
    }

    /** Spawns for every player currently online -- call once right after construction. */
    public void spawnForOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            showTo(player);
        }
        // The fake profile has to sit in the tab list briefly for the client to actually load
        // and cache the skin texture -- but it doesn't need to stay there. Hide it again shortly
        // after, same visible tab-list flicker Citizens-style plugins have always had.
        Bukkit.getScheduler().runTaskLater(plugin, this::hideFromTablist, config.hideFromTablistAfterTicks());
    }

    @Override
    public void showTo(Player player) {
        UserProfile profile = buildProfile();
        var addPlayer = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile, true, 0, GameMode.SURVIVAL, null, null);
        send(player, new WrapperPlayServerPlayerInfoUpdate(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER, addPlayer));

        send(player, new WrapperPlayServerSpawnEntity(
                entityId, Optional.of(definition.npcUuid()), EntityTypes.PLAYER,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                pitch, yaw, yaw, 0, Optional.empty()));

        // index 17: "Displayed Skin Parts" bitmask -- 0x7F shows every layer (cape, jacket,
        // sleeves, pants, hat). This index has been stable for a long time as of the versions
        // checked during this build, but Mojang does occasionally renumber entity metadata
        // indices between major versions -- if the NPC renders as a "naked"/no-overlay skin on
        // whatever version this actually runs on, this is the first thing to re-check against
        // that version's protocol docs. Built as an explicitly-typed list (rather than
        // List.of(new EntityData<>(...))) so generic inference on the wildcard type isn't left
        // to chance.
        List<EntityData<?>> metadata = new java.util.ArrayList<>();
        metadata.add(new EntityData<>(17, EntityDataTypes.BYTE, (byte) 0x7F));
        send(player, new WrapperPlayServerEntityMetadata(entityId, metadata));
    }

    private void hideFromTablist() {
        UserProfile profile = new UserProfile(definition.npcUuid(), sanitizedName(), List.of());
        var hidden = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profile, false, 0, GameMode.SURVIVAL, null, null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player, new WrapperPlayServerPlayerInfoUpdate(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED, hidden));
        }
    }

    private UserProfile buildProfile() {
        List<TextureProperty> textures = definition.hasSkin()
                ? List.of(new TextureProperty("textures", definition.skinValue(), definition.skinSignature()))
                : List.of();
        return new UserProfile(definition.npcUuid(), sanitizedName(), textures);
    }

    /** Fake player profile names follow the same rules as real Minecraft usernames: <=16 chars,
     *  [A-Za-z0-9_] only. Colors/longer display names would need a scoreboard-team prefix/suffix
     *  trick on top of this -- not built in v0.1.0, see CHANGES.md. */
    private String sanitizedName() {
        String raw = definition.displayName().replaceAll("[^A-Za-z0-9_]", "");
        if (raw.isEmpty()) raw = "NPC";
        return raw.length() > 16 ? raw.substring(0, 16) : raw;
    }

    @Override
    public Location location() {
        return location.clone();
    }

    @Override
    public void moveTo(Location newLocation) {
        this.location = newLocation;
        this.yaw = newLocation.getYaw();
        this.pitch = newLocation.getPitch();
        Vector3d pos = new Vector3d(newLocation.getX(), newLocation.getY(), newLocation.getZ());
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player, new WrapperPlayServerEntityTeleport(entityId, pos, yaw, pitch, true));
            send(player, new WrapperPlayServerEntityHeadLook(entityId, yaw));
        }
    }

    @Override
    public void lookAt(Location target) {
        Location eye = location.clone().add(0, 1.62, 0);
        double dx = target.getX() - eye.getX();
        double dy = target.getY() - eye.getY();
        double dz = target.getZ() - eye.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        this.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        this.pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontalDistance));
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player, new WrapperPlayServerEntityRotation(entityId, yaw, pitch, true));
            send(player, new WrapperPlayServerEntityHeadLook(entityId, yaw));
        }
    }

    @Override
    public void refreshEquipment() {
        // Deferred to a later version -- see CHANGES.md. Sending held-item/armor for a
        // packet-only player entity needs WrapperPlayServerEntityEquipment, which wasn't
        // confirmed against the javadocs as carefully as the wrappers above; rather than ship an
        // unverified guess for something players will visibly notice is wrong, equipment display
        // is limited to MOB-type NPCs in this version.
    }

    @Override
    public void refreshSkin() {
        despawn();
        spawnForOnlinePlayers();
    }

    @Override
    public void despawn() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player, new WrapperPlayServerDestroyEntities(entityId));
            send(player, new WrapperPlayServerPlayerInfoRemove(definition.npcUuid()));
        }
    }

    private void send(Player player, PacketWrapper<?> wrapper) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, wrapper);
    }
}
