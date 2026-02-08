package com.urmathsteacher.anticheat;

import java.util.ArrayDeque;
import java.util.Deque;
import org.bukkit.Location;

public class PlayerData {
  private Location lastLocation;
  private long lastMoveTime;
  private int speedBuffer;
  private int flyBuffer;
  private int reachBuffer;
  private int autoClickBuffer;
  private int airTicks;
  private int violationLevel;
  private final Deque<Long> clickTimestamps = new ArrayDeque<>();

  public Location getLastLocation() {
    return lastLocation;
  }

  public void setLastLocation(Location lastLocation) {
    this.lastLocation = lastLocation;
  }

  public long getLastMoveTime() {
    return lastMoveTime;
  }

  public void setLastMoveTime(long lastMoveTime) {
    this.lastMoveTime = lastMoveTime;
  }

  public int getSpeedBuffer() {
    return speedBuffer;
  }

  public void setSpeedBuffer(int speedBuffer) {
    this.speedBuffer = speedBuffer;
  }

  public int getFlyBuffer() {
    return flyBuffer;
  }

  public void setFlyBuffer(int flyBuffer) {
    this.flyBuffer = flyBuffer;
  }

  public int getReachBuffer() {
    return reachBuffer;
  }

  public void setReachBuffer(int reachBuffer) {
    this.reachBuffer = reachBuffer;
  }

  public int getAutoClickBuffer() {
    return autoClickBuffer;
  }

  public void setAutoClickBuffer(int autoClickBuffer) {
    this.autoClickBuffer = autoClickBuffer;
  }

  public int getAirTicks() {
    return airTicks;
  }

  public void setAirTicks(int airTicks) {
    this.airTicks = airTicks;
  }

  public int getViolationLevel() {
    return violationLevel;
  }

  public void incrementViolationLevel() {
    this.violationLevel++;
  }

  public Deque<Long> getClickTimestamps() {
    return clickTimestamps;
  }
}
