package com.nexusuniverse.npc.command;

import com.nexusuniverse.npc.npc.NpcDefinition;
import com.nexusuniverse.npc.npc.NpcManager;
import com.nexusuniverse.npc.npc.NpcType;
import com.nexusuniverse.npc.npc.TraitConfig;
import com.nexusuniverse.npc.skin.SkinFetcher;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NpcCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final NpcManager npcs;
    private final SkinFetcher skins;

    public NpcCommand(JavaPlugin plugin, NpcManager npcs, SkinFetcher skins) {
        this.plugin = plugin;
        this.npcs = npcs;
        this.skins = skins;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("nexusnpc.admin")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> handleCreate(player, args);
            case "remove" -> handleRemove(player, args);
            case "list" -> handleList(player);
            case "tp" -> handleTeleport(player, args);
            case "skin" -> handleSkin(player, args);
            case "equip" -> handleEquip(player, args);
            case "trait" -> handleTrait(player, args);
            case "waypoint" -> handleWaypoint(player, args);
            default -> sendUsage(player);
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Usage: /npc <create <id> <mob <entitytype>|player>|remove <id>|list|tp <id>"
                + "|skin <id> <username>|equip <id> <hand|head|chest|legs|feet>|trait <add|remove> <id> <type>"
                + "|waypoint <add|clear> <id>>");
    }

    // --- create / remove / list / tp ---

    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /npc create <id> <mob <entitytype>|player>");
            return;
        }
        String id = args[1];
        if (npcs.get(id) != null) {
            player.sendMessage(ChatColor.RED + "An NPC called \"" + id + "\" already exists.");
            return;
        }

        String kind = args[2].toLowerCase(Locale.ROOT);
        Location location = player.getLocation();

        if (kind.equals("player")) {
            NpcDefinition definition = npcs.create(id, NpcType.PLAYER, location, id);
            player.sendMessage(ChatColor.AQUA + "Created player-type NPC \"" + definition.id()
                    + "\". Give it a real skin with /npc skin " + definition.id() + " <username>.");
            return;
        }

        if (kind.equals("mob")) {
            EntityType entityType = EntityType.VILLAGER;
            if (args.length >= 4) {
                try {
                    entityType = EntityType.valueOf(args[3].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.RED + "\"" + args[3] + "\" isn't a real entity type.");
                    return;
                }
            }
            Class<?> entityClass = entityType.getEntityClass();
            if (entityClass == null || !org.bukkit.entity.LivingEntity.class.isAssignableFrom(entityClass)) {
                player.sendMessage(ChatColor.RED + "\"" + entityType.name() + "\" isn't a living creature -- pick a mob type (villager, zombie, etc).");
                return;
            }
            npcs.create(id, NpcType.MOB, location, id, entityType);
            player.sendMessage(ChatColor.AQUA + "Created mob-type NPC \"" + id + "\" (" + entityType.name() + ").");
            return;
        }

        player.sendMessage(ChatColor.RED + "Second argument must be \"mob\" or \"player\".");
    }

    private void handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /npc remove <id>");
            return;
        }
        if (npcs.remove(args[1])) {
            player.sendMessage(ChatColor.AQUA + "Removed NPC \"" + args[1] + "\".");
        } else {
            player.sendMessage(ChatColor.RED + "No NPC called \"" + args[1] + "\".");
        }
    }

    private void handleList(Player player) {
        var all = npcs.all();
        if (all.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No NPCs created yet -- /npc create <id> <mob|player>.");
            return;
        }
        player.sendMessage(ChatColor.GRAY + "--- NPCs (" + all.size() + ") ---");
        for (NpcDefinition definition : all) {
            String extra = definition.type() == NpcType.MOB
                    ? (definition.mobType() != null ? definition.mobType().name() : "VILLAGER")
                    : (definition.hasSkin() ? "skin: " + definition.skinSourceName() : "no skin set");
            player.sendMessage(ChatColor.WHITE + definition.id() + ChatColor.GRAY + " (" + definition.type() + ", " + extra + ")");
        }
    }

    private void handleTeleport(Player player, String[] args) {
        NpcDefinition definition = requireNpc(player, args, 1);
        if (definition == null) return;
        npcs.teleport(definition, player.getLocation());
        player.sendMessage(ChatColor.AQUA + "Moved \"" + definition.id() + "\" to your location.");
    }

    // --- skin ---

    private void handleSkin(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /npc skin <id> <username>");
            return;
        }
        NpcDefinition definition = requireNpc(player, args, 1);
        if (definition == null) return;
        if (definition.type() != NpcType.PLAYER) {
            player.sendMessage(ChatColor.RED + "Only player-type NPCs can have a skin -- \"" + definition.id() + "\" is mob-type.");
            return;
        }

        String username = args[2];
        player.sendMessage(ChatColor.GRAY + "Fetching " + username + "'s skin from Mojang...");
        // fetch() completes on the HTTP client's own thread, not the server thread -- every
        // Bukkit/PacketEvents call in the callback (applySkin ultimately despawns/respawns the
        // NPC and iterates Bukkit.getOnlinePlayers()) has to happen back on the main thread.
        skins.fetch(username).whenComplete((skin, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                player.sendMessage(ChatColor.RED + rootMessage(error));
                return;
            }
            npcs.applySkin(definition, username, skin.value(), skin.signature());
            player.sendMessage(ChatColor.AQUA + "\"" + definition.id() + "\" now wears " + username + "'s current skin.");
        }));
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : "Couldn't fetch that skin.";
    }

    // --- equip ---

    private void handleEquip(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /npc equip <id> <hand|head|chest|legs|feet> -- equips whatever's in your hand.");
            return;
        }
        NpcDefinition definition = requireNpc(player, args, 1);
        if (definition == null) return;
        if (definition.type() != NpcType.MOB) {
            player.sendMessage(ChatColor.RED + "Equipment display is only supported on mob-type NPCs in this version -- see CHANGES.md.");
            return;
        }

        EquipmentSlot slot = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "hand" -> EquipmentSlot.HAND;
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            default -> null;
        };
        if (slot == null) {
            player.sendMessage(ChatColor.RED + "Slot must be one of: hand, head, chest, legs, feet.");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        npcs.setEquipment(definition, slot, item.clone());
        player.sendMessage(ChatColor.AQUA + "Equipped \"" + definition.id() + "\"'s " + slot.name().toLowerCase(Locale.ROOT)
                + " slot with what you're holding.");
    }

    // --- traits ---

    private void handleTrait(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /npc trait <add|remove> <id> <type> -- known types: "
                    + String.join(", ", npcs.registeredTraitTypes()));
            return;
        }
        NpcDefinition definition = requireNpc(player, args, 2);
        if (definition == null) return;
        String type = args[3].toUpperCase(Locale.ROOT);

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (npcs.trait(type) == null) {
                    player.sendMessage(ChatColor.RED + "Unknown trait type \"" + type + "\" -- known types: "
                            + String.join(", ", npcs.registeredTraitTypes()));
                    return;
                }
                Map<String, Object> params = new LinkedHashMap<>();
                if (type.equals("PATROL")) params.put("waypoints", new ArrayList<String>());
                npcs.addTrait(definition, new TraitConfig(type, params));
                player.sendMessage(ChatColor.AQUA + "Added " + type + " to \"" + definition.id() + "\"."
                        + (type.equals("PATROL") ? " Add waypoints with /npc waypoint add " + definition.id() + "." : ""));
            }
            case "remove" -> {
                if (npcs.removeTrait(definition, type)) {
                    player.sendMessage(ChatColor.AQUA + "Removed " + type + " from \"" + definition.id() + "\".");
                } else {
                    player.sendMessage(ChatColor.RED + "\"" + definition.id() + "\" doesn't have that trait.");
                }
            }
            default -> player.sendMessage(ChatColor.RED + "Usage: /npc trait <add|remove> <id> <type>");
        }
    }

    // --- waypoints (PATROL trait's param list) ---

    @SuppressWarnings("unchecked")
    private void handleWaypoint(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /npc waypoint <add|clear> <id>");
            return;
        }
        NpcDefinition definition = requireNpc(player, args, 2);
        if (definition == null) return;
        TraitConfig patrol = definition.trait("PATROL");
        if (patrol == null) {
            player.sendMessage(ChatColor.RED + "\"" + definition.id() + "\" doesn't have the PATROL trait yet -- /npc trait add "
                    + definition.id() + " PATROL first.");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                List<String> waypoints = (List<String>) patrol.params().computeIfAbsent("waypoints", k -> new ArrayList<String>());
                Location location = player.getLocation();
                waypoints.add(location.getWorld().getName() + "," + location.getX() + "," + location.getY() + "," + location.getZ());
                npcs.addTrait(definition, patrol);
                player.sendMessage(ChatColor.AQUA + "Added your location as waypoint #" + waypoints.size() + " for \"" + definition.id() + "\".");
            }
            case "clear" -> {
                patrol.params().put("waypoints", new ArrayList<String>());
                npcs.addTrait(definition, patrol);
                player.sendMessage(ChatColor.AQUA + "Cleared \"" + definition.id() + "\"'s waypoints.");
            }
            default -> player.sendMessage(ChatColor.RED + "Usage: /npc waypoint <add|clear> <id>");
        }
    }

    // --- shared ---

    private NpcDefinition requireNpc(Player player, String[] args, int idIndex) {
        if (args.length <= idIndex) {
            player.sendMessage(ChatColor.RED + "Missing NPC id.");
            return null;
        }
        NpcDefinition definition = npcs.get(args[idIndex]);
        if (definition == null) {
            player.sendMessage(ChatColor.RED + "No NPC called \"" + args[idIndex] + "\".");
        }
        return definition;
    }
}
