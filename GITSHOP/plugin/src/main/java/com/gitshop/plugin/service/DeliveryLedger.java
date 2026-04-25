package com.gitshop.plugin.service;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeliveryLedger {
  private final File file;
  private final Set<String> deliveredOrderIds;

  public DeliveryLedger(JavaPlugin plugin) {
    this.file = new File(plugin.getDataFolder(), "processed-orders.yml");
    this.deliveredOrderIds = new HashSet<>();
    load();
  }

  public synchronized boolean hasDelivered(String orderId) {
    return deliveredOrderIds.contains(orderId);
  }

  public synchronized void markDelivered(String orderId) throws IOException {
    deliveredOrderIds.add(orderId);
    save();
  }

  private void load() {
    if (!file.exists()) {
      return;
    }

    YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
    List<String> existing = configuration.getStringList("orders");
    deliveredOrderIds.addAll(existing);
  }

  private void save() throws IOException {
    YamlConfiguration configuration = new YamlConfiguration();
    configuration.set("orders", List.copyOf(deliveredOrderIds));
    configuration.save(file);
  }
}

