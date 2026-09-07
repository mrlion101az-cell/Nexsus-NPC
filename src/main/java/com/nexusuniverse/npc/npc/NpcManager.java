package com.nexusuniverse.npc.npc;

import com.nexusuniverse.npc.NpcConfig;
import com.nexusuniverse.npc.trait.LookCloseTrait;
import com.nexusuniverse.npc.trait.PatrolTrait;
import com.nexusuniverse.npc.trait.Trait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Owns every NPC's persisted {@link NpcDefinition} and, while the plugin is enabled, its live
 * {@link NpcInstance}. Persistence follows the same atomic-write pattern as the rest of the Nexus
 * plugin family (write to a temp file, then move it over the real one) -- see NexusRealms'
 * LandClaimManager#save() for why that matters.
 */
public class NpcManager {

    private final JavaPlugin plugin;
    private final NpcConfig config;
    private final File file;
    private final Map<String, NpcDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, NpcInstance> active = new LinkedHashMap<>();
    private final Map<String, Trait> traits = new LinkedHashMap<>();

    public NpcManager(JavaPlugin plugin, NpcConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
        registerTrait(new LookCloseTrait());
        registerTrait(new PatrolTrait());
        load();
    }

    private void registerTrait(Trait trait) {
        traits.put(trait.typeName().toUpperCase(Locale.ROOT), trait);
    }

    public Trait trait(String typeName) {
        return traits.get(typeName.toUpperCase(Locale.ROOT));
    }

    public java.util.Collection<String> registeredTraitTypes() {
        return traits.keySet();
    }

    // --- lookups ---

    public NpcDefinition get(String id) {
        return definitions.get(id.toLowerCase(Locale.ROOT));
    }

    public java.util.Collection<NpcDefinition> all() {
        return definitions.values();
    }

    public NpcInstance activeInstance(String id) {
        return active.get(id.toLowerCase(Locale.ROOT));
    }

    // --- lifecycle ---

    /** Spawns the live instance for every loaded definition -- call once from onEnable, after worlds are up. */
    public void spawnAll() {
        for (NpcDefinition definition : definitions.values()) {
            spawn(definition);
        }
    }

    /** Despawns every live instance without touching the persisted definitions -- call from onDisable. */
    public void despawnAll() {
        for (NpcInstance instance : active.values()) {
            instance.despawn();
        }
        active.clear();
    }

    /** Shows every currently-active PLAYER-type NPC to a player who just joined. */
    public void showAllTo(Player player) {
        for (NpcInstance instance : active.values()) {
            instance.showTo(player);
        }
    }

    /** Runs every registered trait on every active NPC once. Called by a repeating scheduler task. */
    public void tickAll() {
        for (NpcInstance instance : active.values()) {
            for (TraitConfig traitConfig : instance.definition().traits()) {
                Trait trait = trait(traitConfig.type());
                if (trait != null) {
                    try {
                        trait.tick(instance, traitConfig, config);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "NexusNPC: trait " + traitConfig.type()
                                + " threw an exception ticking NPC " + instance.definition().id(), e);
                    }
                }
            }
        }
    }

    private void spawn(NpcDefinition definition) {
        try {
            NpcInstance instance = definition.type() == NpcType.PLAYER
                    ? spawnPlayerType(definition)
                    : spawnMobType(definition);
            instance.refreshEquipment();
            active.put(definition.id(), instance);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "NexusNPC: failed to spawn NPC \"" + definition.id() + "\"", e);
        }
    }

    private NpcInstance spawnMobType(NpcDefinition definition) {
        EntityType type = definition.mobType() != null ? definition.mobType() : EntityType.VILLAGER;
        LivingEntity entity = (LivingEntity) definition.location().getWorld().spawnEntity(definition.location(), type);
        return new MobNpcHandle(definition, entity);
    }

    private NpcInstance spawnPlayerType(NpcDefinition definition) {
        PacketNpcHandle handle = new PacketNpcHandle(definition, plugin, config);
        handle.spawnForOnlinePlayers();
        return handle;
    }

    // --- mutation (all of these persist immediately) ---

    public NpcDefinition create(String id, NpcType type, Location location, String displayName) {
        return create(id, type, location, displayName, null);
    }

    /** @param mobType only used for {@link NpcType#MOB}; ignored (may be null) for PLAYER. */
    public NpcDefinition create(String id, NpcType type, Location location, String displayName, EntityType mobType) {
        NpcDefinition definition = new NpcDefinition(id.toLowerCase(Locale.ROOT), UUID.randomUUID(), type, displayName, location.clone());
        if (type == NpcType.MOB) definition.setMobType(mobType != null ? mobType : EntityType.VILLAGER);
        definitions.put(definition.id(), definition);
        spawn(definition);
        save();
        return definition;
    }

    public boolean remove(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        NpcDefinition definition = definitions.remove(key);
        if (definition == null) return false;
        NpcInstance instance = active.remove(key);
        if (instance != null) instance.despawn();
        save();
        return true;
    }

    public void applySkin(NpcDefinition definition, String sourceName, String value, String signature) {
        definition.setSkin(sourceName, value, signature);
        save();
        NpcInstance instance = active.get(definition.id());
        if (instance != null) instance.refreshSkin();
    }

    public void teleport(NpcDefinition definition, Location location) {
        definition.setLocation(location.clone());
        save();
        NpcInstance instance = active.get(definition.id());
        if (instance != null) instance.moveTo(location);
    }

    public void setEquipment(NpcDefinition definition, EquipmentSlot slot, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            definition.equipment().remove(slot);
        } else {
            definition.equipment().put(slot, item.clone());
        }
        save();
        NpcInstance instance = active.get(definition.id());
        if (instance != null) instance.refreshEquipment();
    }

    public void addTrait(NpcDefinition definition, TraitConfig traitConfig) {
        definition.removeTrait(traitConfig.type());
        definition.traits().add(traitConfig);
        save();
    }

    public boolean removeTrait(NpcDefinition definition, String type) {
        boolean had = definition.trait(type) != null;
        definition.removeTrait(type);
        if (had) save();
        return had;
    }

    // --- persistence ---

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        ConfigurationSection root = data.createSection("npcs");
        for (NpcDefinition definition : definitions.values()) {
            ConfigurationSection section = root.createSection(definition.id());
            section.set("type", definition.type().name());
            section.set("uuid", definition.npcUuid().toString());
            section.set("display-name", definition.displayName());
            if (definition.mobType() != null) section.set("mob-type", definition.mobType().name());
            if (definition.hasSkin()) {
                ConfigurationSection skin = section.createSection("skin");
                skin.set("source", definition.skinSourceName());
                skin.set("value", definition.skinValue());
                skin.set("signature", definition.skinSignature());
            }
            Location loc = definition.location();
            section.set("world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
            section.set("x", loc.getX());
            section.set("y", loc.getY());
            section.set("z", loc.getZ());
            section.set("yaw", (double) loc.getYaw());
            section.set("pitch", (double) loc.getPitch());
            if (!definition.equipment().isEmpty()) {
                ConfigurationSection equipment = section.createSection("equipment");
                definition.equipment().forEach((slot, item) -> equipment.set(slot.name(), item));
            }
            if (!definition.traits().isEmpty()) {
                List<Map<String, Object>> traitMaps = definition.traits().stream().map(TraitConfig::toMap).toList();
                section.set("traits", traitMaps);
            }
        }

        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            File tmp = new File(plugin.getDataFolder(), "npcs.yml.tmp");
            data.save(tmp);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "NexusNPC: failed to save npcs.yml", e);
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = data.getConfigurationSection("npcs");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            try {
                loadOne(id, root.getConfigurationSection(id));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "NexusNPC: skipped a corrupt NPC entry (" + id + ") in npcs.yml", e);
            }
        }
    }

    private void loadOne(String id, ConfigurationSection section) {
        if (section == null) return;
        NpcType type = NpcType.valueOf(section.getString("type", "MOB"));
        UUID uuid = UUID.fromString(section.getString("uuid"));
        String displayName = section.getString("display-name", id);

        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) world = Bukkit.getWorlds().get(0);
        Location location = new Location(world,
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));

        NpcDefinition definition = new NpcDefinition(id, uuid, type, displayName, location);

        String mobTypeName = section.getString("mob-type");
        if (mobTypeName != null) definition.setMobType(EntityType.valueOf(mobTypeName));

        ConfigurationSection skin = section.getConfigurationSection("skin");
        if (skin != null) {
            definition.setSkin(skin.getString("source"), skin.getString("value"), skin.getString("signature"));
        }

        ConfigurationSection equipment = section.getConfigurationSection("equipment");
        if (equipment != null) {
            for (String slotName : equipment.getKeys(false)) {
                try {
                    EquipmentSlot slot = EquipmentSlot.valueOf(slotName);
                    ItemStack item = equipment.getItemStack(slotName);
                    if (item != null) definition.equipment().put(slot, item);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        for (Map<?, ?> raw : section.getMapList("traits")) {
            definition.traits().add(TraitConfig.fromMap(raw));
        }

        definitions.put(id, definition);
    }
}
