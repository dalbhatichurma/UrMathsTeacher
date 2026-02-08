package com.urmathsteacher.anticheat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class AntiCheatPlugin extends JavaPlugin implements Listener, CommandExecutor {
  private final Map<UUID, PlayerData> playerData = new HashMap<>();

  private boolean speedEnabled;
  private double speedMaxBps;
  private int speedBuffer;

  private boolean flyEnabled;
  private int flyMaxAirTicks;
  private int flyBuffer;

  private boolean reachEnabled;
  private double reachMaxDistance;
  private int reachBuffer;

  private boolean autoClickEnabled;
  private int autoClickMaxCps;
  private long autoClickSampleMs;
  private int autoClickBuffer;

  private String alertPermission;
  private boolean broadcastToConsole;
  private boolean kickEnabled;
  private int kickThreshold;
  private String kickMessage;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    loadSettings();
    getServer().getPluginManager().registerEvents(this, this);
    getCommand("anticheat").setExecutor(this);
  }

  @Override
  public void onDisable() {
    playerData.clear();
  }

  private void loadSettings() {
    FileConfiguration config = getConfig();
    speedEnabled = config.getBoolean("checks.speed.enabled", true);
    speedMaxBps = config.getDouble("checks.speed.max-blocks-per-second", 7.5);
    speedBuffer = config.getInt("checks.speed.buffer", 4);

    flyEnabled = config.getBoolean("checks.fly.enabled", true);
    flyMaxAirTicks = config.getInt("checks.fly.max-air-ticks", 12);
    flyBuffer = config.getInt("checks.fly.buffer", 3);

    reachEnabled = config.getBoolean("checks.reach.enabled", true);
    reachMaxDistance = config.getDouble("checks.reach.max-reach", 3.4);
    reachBuffer = config.getInt("checks.reach.buffer", 3);

    autoClickEnabled = config.getBoolean("checks.autoclicker.enabled", true);
    autoClickMaxCps = config.getInt("checks.autoclicker.max-cps", 16);
    autoClickSampleMs = config.getLong("checks.autoclicker.sample-ms", 1000L);
    autoClickBuffer = config.getInt("checks.autoclicker.buffer", 3);

    alertPermission = config.getString("alerts.notify-permission", "anticheat.admin");
    broadcastToConsole = config.getBoolean("alerts.broadcast-to-console", true);
    kickEnabled = config.getBoolean("punishments.kick-on-threshold", true);
    kickThreshold = config.getInt("punishments.kick-threshold", 8);
    kickMessage = config.getString("punishments.kick-message", "Unfair advantage detected.");
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    playerData.put(event.getPlayer().getUniqueId(), new PlayerData());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    playerData.remove(event.getPlayer().getUniqueId());
  }

  @EventHandler(ignoreCancelled = true)
  public void onPlayerMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    if (shouldBypass(player)) {
      return;
    }

    PlayerData data = getData(player);
    Location from = event.getFrom();
    Location to = event.getTo();
    if (to == null || from.getWorld() == null || to.getWorld() == null) {
      return;
    }

    if (speedEnabled) {
      checkSpeed(player, data, from, to);
    }

    if (flyEnabled) {
      checkFly(player, data, to);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onEntityDamage(EntityDamageByEntityEvent event) {
    Entity damager = event.getDamager();
    if (!(damager instanceof Player player)) {
      return;
    }
    if (shouldBypass(player) || !reachEnabled) {
      return;
    }
    Entity target = event.getEntity();
    if (!(target instanceof LivingEntity)) {
      return;
    }
    double distance = player.getEyeLocation().distance(target.getLocation());
    if (distance > reachMaxDistance) {
      PlayerData data = getData(player);
      int buffer = data.getReachBuffer() + 1;
      data.setReachBuffer(buffer);
      if (buffer >= reachBuffer) {
        data.setReachBuffer(0);
        registerViolation(player, data, "Reach", String.format("%.2f blocks", distance));
      }
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (!autoClickEnabled) {
      return;
    }
    Action action = event.getAction();
    if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
      return;
    }
    Player player = event.getPlayer();
    if (shouldBypass(player)) {
      return;
    }
    PlayerData data = getData(player);
    long now = System.currentTimeMillis();
    data.getClickTimestamps().addLast(now);
    pruneClicks(data, now);
    long windowSeconds = Math.max(1, TimeUnit.MILLISECONDS.toSeconds(autoClickSampleMs));
    int cps = Math.toIntExact(data.getClickTimestamps().size() / windowSeconds);
    if (cps > autoClickMaxCps) {
      int buffer = data.getAutoClickBuffer() + 1;
      data.setAutoClickBuffer(buffer);
      if (buffer >= autoClickBuffer) {
        data.setAutoClickBuffer(0);
        registerViolation(player, data, "AutoClick", cps + " CPS");
      }
    } else if (data.getAutoClickBuffer() > 0) {
      data.setAutoClickBuffer(data.getAutoClickBuffer() - 1);
    }
  }

  private void pruneClicks(PlayerData data, long now) {
    long cutoff = now - autoClickSampleMs;
    while (!data.getClickTimestamps().isEmpty() && data.getClickTimestamps().peekFirst() < cutoff) {
      data.getClickTimestamps().removeFirst();
    }
  }

  private void checkSpeed(Player player, PlayerData data, Location from, Location to) {
    if (!from.getWorld().equals(to.getWorld())) {
      return;
    }
    long now = System.currentTimeMillis();
    if (data.getLastLocation() == null) {
      data.setLastLocation(from);
      data.setLastMoveTime(now);
      return;
    }

    long deltaMs = Math.max(1, now - data.getLastMoveTime());
    double distance = from.distance(to);
    double seconds = deltaMs / 1000.0;
    double blocksPerSecond = distance / seconds;

    if (!player.isInsideVehicle() && player.getGameMode() != GameMode.CREATIVE) {
      if (blocksPerSecond > speedMaxBps) {
        int buffer = data.getSpeedBuffer() + 1;
        data.setSpeedBuffer(buffer);
        if (buffer >= speedBuffer) {
          data.setSpeedBuffer(0);
          registerViolation(player, data, "Speed", String.format("%.2f bps", blocksPerSecond));
        }
      } else if (data.getSpeedBuffer() > 0) {
        data.setSpeedBuffer(data.getSpeedBuffer() - 1);
      }
    }

    data.setLastLocation(to);
    data.setLastMoveTime(now);
  }

  private void checkFly(Player player, PlayerData data, Location to) {
    if (player.getAllowFlight() || player.isFlying() || player.getGameMode() == GameMode.CREATIVE) {
      data.setAirTicks(0);
      return;
    }

    boolean onGround = player.isOnGround();
    boolean inPassable = to.getBlock().isPassable();
    boolean inWater = to.getBlock().getType() == Material.WATER || to.getBlock().getType() == Material.BUBBLE_COLUMN;
    boolean onLadder = to.getBlock().getType() == Material.LADDER || to.getBlock().getType() == Material.VINE;

    if (!onGround && inPassable && !inWater && !onLadder) {
      data.setAirTicks(data.getAirTicks() + 1);
    } else {
      data.setAirTicks(0);
    }

    if (data.getAirTicks() > flyMaxAirTicks) {
      int buffer = data.getFlyBuffer() + 1;
      data.setFlyBuffer(buffer);
      if (buffer >= flyBuffer) {
        data.setFlyBuffer(0);
        registerViolation(player, data, "Fly", data.getAirTicks() + " air ticks");
      }
    } else if (data.getFlyBuffer() > 0) {
      data.setFlyBuffer(data.getFlyBuffer() - 1);
    }
  }

  private void registerViolation(Player player, PlayerData data, String check, String detail) {
    data.incrementViolationLevel();
    String message = String.format("[AntiCheat] %s failed %s (%s) VL=%d", player.getName(), check, detail, data.getViolationLevel());
    for (Player online : Bukkit.getOnlinePlayers()) {
      if (online.hasPermission(alertPermission)) {
        online.sendMessage(message);
      }
    }
    if (broadcastToConsole) {
      getLogger().info(message);
    }
    if (kickEnabled && data.getViolationLevel() >= kickThreshold) {
      player.kickPlayer(kickMessage);
    }
  }

  private boolean shouldBypass(Player player) {
    return player.hasPermission("anticheat.bypass");
  }

  private PlayerData getData(Player player) {
    return playerData.computeIfAbsent(player.getUniqueId(), uuid -> new PlayerData());
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!sender.hasPermission("anticheat.admin")) {
      sender.sendMessage("You do not have permission to do that.");
      return true;
    }
    if (args.length == 0) {
      sender.sendMessage("Usage: /" + label + " <reload|status>");
      return true;
    }
    if (args[0].equalsIgnoreCase("reload")) {
      reloadConfig();
      loadSettings();
      sender.sendMessage("AntiCheat config reloaded.");
      return true;
    }
    if (args[0].equalsIgnoreCase("status")) {
      sender.sendMessage("AntiCheat enabled checks: " + statusSummary());
      return true;
    }
    sender.sendMessage("Unknown subcommand. Use /" + label + " <reload|status>");
    return true;
  }

  private String statusSummary() {
    return String.format(
        "Speed=%s, Fly=%s, Reach=%s, AutoClick=%s",
        speedEnabled,
        flyEnabled,
        reachEnabled,
        autoClickEnabled);
  }
}
