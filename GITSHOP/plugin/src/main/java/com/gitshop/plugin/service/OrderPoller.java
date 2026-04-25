package com.gitshop.plugin.service;

import com.gitshop.plugin.api.ShopApiClient;
import com.gitshop.plugin.model.DeliveryResult;
import com.gitshop.plugin.model.PendingOrder;
import java.io.IOException;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

public final class OrderPoller implements Runnable {
  private final JavaPlugin plugin;
  private final ShopApiClient apiClient;
  private final DeliveryLedger deliveryLedger;
  private final RewardService rewardService;

  public OrderPoller(
      JavaPlugin plugin,
      ShopApiClient apiClient,
      DeliveryLedger deliveryLedger,
      RewardService rewardService
  ) {
    this.plugin = plugin;
    this.apiClient = apiClient;
    this.deliveryLedger = deliveryLedger;
    this.rewardService = rewardService;
  }

  @Override
  public void run() {
    try {
      List<PendingOrder> claimedOrders = apiClient.claimOrders();
      if (claimedOrders.isEmpty()) {
        return;
      }

      plugin.getLogger().info("Claimed " + claimedOrders.size() + " paid orders from GitShop");

      for (PendingOrder order : claimedOrders) {
        process(order);
      }
    } catch (IOException | InterruptedException error) {
      if (error instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      plugin.getLogger().warning(
          "Failed to poll GitShop backend: "
              + error.getClass().getSimpleName()
              + (error.getMessage() == null ? "" : " - " + error.getMessage())
              + " | current target: "
              + apiClient.currentBaseUrl()
              + " | candidates: "
              + apiClient.describeCandidateBaseUrls()
      );
      error.printStackTrace();
    }
  }

  private void process(PendingOrder order) {
    try {
      if (deliveryLedger.hasDelivered(order.getId())) {
        apiClient.acknowledge(order.getId(), order.getClaimToken(), DeliveryResult.success(List.of(), "Order was already recorded locally"));
        return;
      }

      DeliveryResult result = rewardService.deliver(order);
      if (result.success()) {
        deliveryLedger.markDelivered(order.getId());
      }

      apiClient.acknowledge(order.getId(), order.getClaimToken(), result);
    } catch (IOException | InterruptedException error) {
      if (error instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      plugin.getLogger().warning(
          "Failed to process order "
              + order.getOrderNumber()
              + ": "
              + error.getClass().getSimpleName()
              + (error.getMessage() == null ? "" : " - " + error.getMessage())
              + " | current target: "
              + apiClient.currentBaseUrl()
      );
      error.printStackTrace();
    }
  }
}
