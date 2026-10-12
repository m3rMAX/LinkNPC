package com.linknpc;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class LinkNPC extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, ArmorStand> npcs = new HashMap<>();
    private final Map<UUID, List<BlockDisplay>> visuals = new HashMap<>();
    private NamespacedKey typeKey;
    private NamespacedKey linkKey;
    private NamespacedKey markerKey;
    private NamespacedKey visualOwnerKey;

    @Override
    public void onEnable() {
        typeKey = new NamespacedKey(this, "npc_type");
        linkKey = new NamespacedKey(this, "npc_link");
        markerKey = new NamespacedKey(this, "linknpc_marker");
        visualOwnerKey = new NamespacedKey(this, "visual_owner");

        PluginCommandSetup();
        Bukkit.getPluginManager().registerEvents(this, this);
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                registerIfNpc(entity);
                registerVisual(entity);
            }
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long time = System.currentTimeMillis();
            Iterator<Map.Entry<UUID, ArmorStand>> iterator = npcs.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, ArmorStand> entry = iterator.next();
                ArmorStand anchor = entry.getValue();
                if (anchor == null || !anchor.isValid() || anchor.isDead()) {
                    iterator.remove();
                    visuals.remove(entry.getKey());
                    continue;
                }
                double bob = Math.sin(time / 420.0 + entry.getKey().hashCode()) * 0.10;
                List<BlockDisplay> parts = visuals.getOrDefault(entry.getKey(), Collections.emptyList());
                Location visualLocation = anchor.getLocation().clone().add(0, bob, 0);
                for (BlockDisplay part : parts) {
                    if (part != null && part.isValid()) part.teleport(visualLocation);
                }
                // The invisible interaction anchor gently looks left and right.
                anchor.setHeadPose(new EulerAngle(0, Math.sin(time / 700.0) * 0.18, 0));
            }
        }, 0L, 2L);
        getLogger().info("LinkNPC enabled. Main command: /NPCHERO (alias: /npc)");
    }

    private void PluginCommandSetup() {
        Command command = getCommand("npchero");
        if (command == null) {
            getLogger().severe("Command npchero is missing from plugin.yml; disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("linknpc.use")) {
            sender.sendMessage(color("&cليس لديك صلاحية استخدام هذا الأمر."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("discord") || sub.equals("youtube")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("هذا الأمر متاح للاعبين داخل اللعبة فقط.");
                return true;
            }
            if (args.length != 2) {
                player.sendMessage(color("&cالاستخدام: /NPCHERO " + sub + " <link>"));
                return true;
            }
            String link = normalizeUrl(args[1]);
            if (link == null) {
                player.sendMessage(color("&cالرابط غير صالح. استخدم رابطاً يبدأ بـ https:// أو http://"));
                return true;
            }
            spawnNpc(player, sub, link);
            player.sendMessage(color("&aتم إنشاء شخصية &e" + sub.toUpperCase(Locale.ROOT)
                    + " &aبنجاح. انقر عليها لعرض الرابط."));
            return true;
        }

        if (sub.equals("remove")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("هذا الأمر متاح للاعبين داخل اللعبة فقط.");
                return true;
            }
            ArmorStand nearest = player.getNearbyEntities(5, 4, 5).stream()
                    .filter(e -> e instanceof ArmorStand && isLinkNpc(e))
                    .map(e -> (ArmorStand) e)
                    .min((a, b) -> Double.compare(a.getLocation().distanceSquared(player.getLocation()),
                            b.getLocation().distanceSquared(player.getLocation())))
                    .orElse(null);
            if (nearest == null) player.sendMessage(color("&cاقترب من شخصية LinkNPC ضمن 5 بلوكات أولاً."));
            else {
                npcs.remove(nearest.getUniqueId());
                List<BlockDisplay> parts = visuals.remove(nearest.getUniqueId());
                if (parts != null) for (BlockDisplay part : parts) if (part != null && part.isValid()) part.remove();
                nearest.remove();
                player.sendMessage(color("&aتم حذف أقرب شخصية LinkNPC مع شكلها بالكامل."));
            }
            return true;
        }

        if (sub.equals("list")) {
            int count = 0;
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (isLinkNpc(entity)) {
                        count++;
                        String type = entity.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
                        Location loc = entity.getLocation();
                        sender.sendMessage(color("&8- &e" + type + " &7@ &f" + world.getName()
                                + " &7(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")"));
                    }
                }
            }
            sender.sendMessage(color(count == 0 ? "&7لا توجد شخصيات LinkNPC حالياً." : "&aالعدد: &f" + count));
            return true;
        }

        sender.sendMessage(color("&cأمر غير معروف. استخدم &f/NPCHERO help"));
        return true;
    }

    private void spawnNpc(Player player, String type, String link) {
        Location location = player.getLocation().clone();
        Vector direction = location.getDirection();
        direction.setY(0);
        if (direction.lengthSquared() > 0) location.add(direction.normalize().multiply(1.5));

        ArmorStand anchor = location.getWorld().spawn(location, ArmorStand.class);
        anchor.setVisible(false);
        anchor.setArms(false);
        anchor.setBasePlate(false);
        anchor.setGravity(false);
        anchor.setInvulnerable(true);
        anchor.setPersistent(true);
        anchor.setCanPickupItems(false);
        anchor.setCustomNameVisible(true);
        anchor.setCustomName(color(type.equals("discord") ? "&9&lDISCORD MASCOT" : "&c&lYOUTUBE MASCOT"));
        anchor.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        anchor.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
        anchor.getPersistentDataContainer().set(linkKey, PersistentDataType.STRING, link);

        List<BlockDisplay> parts = new ArrayList<>();
        if (type.equals("discord")) {
            // A bespoke blue mascot built from animated display blocks, not a player skin.
            addPart(parts, anchor, Material.BLUE_CONCRETE, -0.32f, 0.95f, -0.18f, 0.64f);
            addPart(parts, anchor, Material.BLUE_CONCRETE, -0.48f, 1.13f, -0.16f, 0.28f);
            addPart(parts, anchor, Material.BLUE_CONCRETE, 0.20f, 1.13f, -0.16f, 0.28f);
            addPart(parts, anchor, Material.BLUE_CONCRETE, -0.24f, 0.48f, -0.12f, 0.22f);
            addPart(parts, anchor, Material.BLUE_CONCRETE, 0.08f, 0.48f, -0.12f, 0.22f);
            addPart(parts, anchor, Material.WHITE_CONCRETE, -0.20f, 1.17f, 0.12f, 0.13f);
            addPart(parts, anchor, Material.WHITE_CONCRETE, 0.10f, 1.17f, 0.12f, 0.13f);
            addPart(parts, anchor, Material.BLACK_CONCRETE, -0.16f, 1.19f, 0.20f, 0.065f);
            addPart(parts, anchor, Material.BLACK_CONCRETE, 0.14f, 1.19f, 0.20f, 0.065f);
            addPart(parts, anchor, Material.LIGHT_BLUE_CONCRETE, -0.10f, 0.82f, 0.22f, 0.12f);
        } else {
            // A bespoke red video mascot with a white play symbol, made from block displays.
            addPart(parts, anchor, Material.RED_CONCRETE, -0.34f, 0.78f, -0.18f, 0.68f);
            addPart(parts, anchor, Material.RED_CONCRETE, -0.43f, 1.18f, -0.14f, 0.24f);
            addPart(parts, anchor, Material.RED_CONCRETE, 0.19f, 1.18f, -0.14f, 0.24f);
            addPart(parts, anchor, Material.RED_CONCRETE, -0.24f, 0.45f, -0.12f, 0.20f);
            addPart(parts, anchor, Material.RED_CONCRETE, 0.08f, 0.45f, -0.12f, 0.20f);
            addPart(parts, anchor, Material.WHITE_CONCRETE, -0.08f, 0.93f, 0.23f, 0.22f);
            addPart(parts, anchor, Material.WHITE_CONCRETE, 0.05f, 0.86f, 0.23f, 0.22f);
            addPart(parts, anchor, Material.WHITE_CONCRETE, -0.08f, 0.79f, 0.23f, 0.22f);
            addPart(parts, anchor, Material.BLACK_CONCRETE, -0.22f, 1.20f, 0.10f, 0.07f);
            addPart(parts, anchor, Material.BLACK_CONCRETE, 0.13f, 1.20f, 0.10f, 0.07f);
        }
        npcs.put(anchor.getUniqueId(), anchor);
        visuals.put(anchor.getUniqueId(), parts);
    }

    private void addPart(List<BlockDisplay> parts, ArmorStand anchor, Material material,
                         float x, float y, float z, float scale) {
        BlockDisplay display = anchor.getWorld().spawn(anchor.getLocation(), BlockDisplay.class);
        display.setBlock(material.createBlockData());
        display.setPersistent(true);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setViewRange(1.0f);
        display.setInterpolationDuration(2);
        display.setTransformation(new Transformation(
                new Vector3f(x, y, z), new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf()));
        display.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        display.getPersistentDataContainer().set(visualOwnerKey, PersistentDataType.STRING,
                anchor.getUniqueId().toString());
        parts.add(display);
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!isLinkNpc(entity)) return;
        String type = entity.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        String link = entity.getPersistentDataContainer().get(linkKey, PersistentDataType.STRING);
        if (type == null || link == null) return;
        event.setCancelled(true);
        String title = type.equalsIgnoreCase("discord") ? "Discord" : "YouTube";
        TextComponent message = new TextComponent("اضغط هنا لفتح رابط " + title + " ➜ ");
        message.setColor(ChatColor.YELLOW);
        TextComponent clickable = new TextComponent(link);
        clickable.setColor(ChatColor.AQUA);
        clickable.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link));
        clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("فتح: " + link).color(ChatColor.GRAY).create()));
        message.addExtra(clickable);
        event.getPlayer().spigot().sendMessage(message);
        npcs.put(entity.getUniqueId(), (ArmorStand) entity);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            registerIfNpc(entity);
            registerVisual(entity);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (isLinkNpc(entity)) {
                npcs.remove(entity.getUniqueId());
                visuals.remove(entity.getUniqueId());
            }
            if (entity instanceof BlockDisplay) {
                String owner = entity.getPersistentDataContainer().get(visualOwnerKey, PersistentDataType.STRING);
                if (owner != null) {
                    try { visuals.remove(UUID.fromString(owner)); } catch (IllegalArgumentException ignored) { }
                }
            }
        }
    }

    private void registerIfNpc(Entity entity) {
        if (isLinkNpc(entity)) npcs.put(entity.getUniqueId(), (ArmorStand) entity);
    }

    private void registerVisual(Entity entity) {
        if (!(entity instanceof BlockDisplay display)) return;
        String owner = display.getPersistentDataContainer().get(visualOwnerKey, PersistentDataType.STRING);
        if (owner == null) return;
        try {
            UUID ownerId = UUID.fromString(owner);
            List<BlockDisplay> parts = visuals.computeIfAbsent(ownerId, ignored -> new ArrayList<>());
            if (parts.stream().noneMatch(existing -> existing.getUniqueId().equals(display.getUniqueId()))) parts.add(display);
        } catch (IllegalArgumentException ignored) { }
    }

    private boolean isLinkNpc(Entity entity) {
        return entity instanceof ArmorStand
                && entity.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE)
                && entity.getPersistentDataContainer().has(typeKey, PersistentDataType.STRING)
                && entity.getPersistentDataContainer().has(linkKey, PersistentDataType.STRING);
    }

    private String normalizeUrl(String input) {
        try {
            String candidate = input.trim();
            if (candidate.isEmpty()) return null;
            if (!candidate.matches("(?i)^https?://.*")) candidate = "https://" + candidate;
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null || uri.getUserInfo() != null) return null;
            return uri.toASCIIString();
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&8&m--------------- &e&lLinkNPC &8&m---------------"));
        sender.sendMessage(color("&e/NPCHERO discord <link> &7- إنشاء شخصية ديسكورد"));
        sender.sendMessage(color("&e/NPCHERO youtube <link> &7- إنشاء شخصية يوتيوب"));
        sender.sendMessage(color("&e/NPCHERO remove &7- حذف أقرب شخصية ضمن 5 بلوكات"));
        sender.sendMessage(color("&e/NPCHERO list &7- عرض الشخصيات"));
        sender.sendMessage(color("&e/NPCHERO help &7- عرض المساعدة"));
        sender.sendMessage(color("&8&m------------------------------------------"));
    }

    private String color(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("linknpc.use")) return Collections.emptyList();
        if (args.length == 1) {
            return filter(Arrays.asList("discord", "youtube", "remove", "list", "help"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("discord") || args[0].equalsIgnoreCase("youtube"))) {
            List<String> examples = args[0].equalsIgnoreCase("discord")
                    ? Arrays.asList("https://discord.gg/", "https://discord.com/")
                    : Arrays.asList("https://youtube.com/", "https://youtu.be/");
            return filter(examples, args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
