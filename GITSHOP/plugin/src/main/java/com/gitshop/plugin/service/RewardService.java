package com.gitshop.plugin.service;

import com.gitshop.plugin.model.DeliveryResult;
import com.gitshop.plugin.model.PendingOrder;
import com.gitshop.plugin.model.PendingOrder.RewardLineItem;
import com.gitshop.plugin.model.PluginSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RewardService {
  private final JavaPlugin plugin;
  private final PluginSettings settings;
  private final AnnouncementService announcementService;

  public RewardService(JavaPlugin plugin, PluginSettings settings, AnnouncementService announcementService) {
    this.plugin = plugin;
    this.settings = settings;
    this.announcementService = announcementService;
  }

  public DeliveryResult deliver(PendingOrder order) {
    try {
      return Bukkit.getScheduler().callSyncMethod(plugin, () -> performDelivery(order)).get();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return DeliveryResult.failure("Delivery was interrupted");
    } catch (ExecutionException error) {
      return DeliveryResult.failure("Delivery failed: " + error.getCause().getMessage());
    }
  }

  private DeliveryResult performDelivery(PendingOrder order) {
    List<String> executedCommands = new ArrayList<>();

    for (RewardLineItem item : order.getLineItems()) {
      List<String> rewardCommands = settings.commandsFor(item.getRewardKey());
      if (rewardCommands.isEmpty()) {
        return DeliveryResult.failure("No plugin reward mapping found for key: " + item.getRewardKey());
      }

      for (String rewardCommand : rewardCommands) {
        String resolvedCommand = rewardCommand
            .replace("{username}", order.getUsername())
            .replace("%player%", order.getUsername())
            .replace("%username%", order.getUsername())
            .replace("{player}", order.getUsername())
            .replace("{orderNumber}", order.getOrderNumber());

        boolean commandAccepted = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolvedCommand);
        if (!commandAccepted) {
          return DeliveryResult.failure("Command failed: " + resolvedCommand);
        }
        executedCommands.add(resolvedCommand);
      }
    }

    announcementService.broadcastPurchase(order);

    if (settings.openReceiptGui()) {
      Player player = Bukkit.getPlayerExact(order.getUsername());
      if (player != null && player.isOnline()) {
        announcementService.openReceiptGui(player, order);
      }
    }

    return DeliveryResult.success(executedCommands, "Fulfilled by Paper plugin");
  }
}
