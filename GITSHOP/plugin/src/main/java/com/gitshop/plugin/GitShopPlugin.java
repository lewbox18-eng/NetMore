package com.gitshop.plugin;

import com.gitshop.plugin.api.ShopApiClient;
import com.gitshop.plugin.model.PluginSettings;
import com.gitshop.plugin.service.AnnouncementService;
import com.gitshop.plugin.service.DeliveryLedger;
import com.gitshop.plugin.service.OrderPoller;
import com.gitshop.plugin.service.RewardService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class GitShopPlugin extends JavaPlugin {
  private PluginSettings settings;
  private ShopApiClient apiClient;
  private DeliveryLedger deliveryLedger;
  private AnnouncementService announcementService;
  private RewardService rewardService;
  private BukkitTask pollTask;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
      getLogger().warning("Could not create plugin data folder");
    }

    getLogger().info("GitShopBridge data folder: " + getDataFolder().getAbsolutePath());

    deliveryLedger = new DeliveryLedger(this);
    reloadServices();
    registerCommand();
    schedulePollingTask();
    testBackendConnectivity();

    getLogger().info("GitShopBridge enabled for server id " + settings.serverId());
  }

  @Override
  public void onDisable() {
    if (pollTask != null) {
      pollTask.cancel();
    }
  }

  private void reloadServices() {
    reloadConfig();
    settings = PluginSettings.from(getConfig());
    apiClient = new ShopApiClient(settings);
    announcementService = new AnnouncementService(this, settings, apiClient);
    rewardService = new RewardService(this, settings, announcementService);
    getLogger().info("Configured backend URL: " + settings.baseUrl());
    getLogger().info("Configured storefront URL: " + settings.shopUrl());
    getLogger().info("Backend candidate URLs: " + apiClient.describeCandidateBaseUrls());
  }

  private void schedulePollingTask() {
    if (pollTask != null) {
      pollTask.cancel();
    }

    long periodTicks = settings.pollIntervalSeconds() * 20L;
    pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
        this,
        new OrderPoller(this, apiClient, deliveryLedger, rewardService),
        40L,
        periodTicks
    );
  }

  private void testBackendConnectivity() {
    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
      try {
        if (apiClient.ping()) {
          getLogger().info("Successfully reached GitShop backend at " + apiClient.currentBaseUrl());
          getLogger().info("Storefront URL for announcements: " + apiClient.currentShopUrl(settings.shopUrl()));
        }
      } catch (Exception error) {
        getLogger().warning(
            "Could not reach GitShop backend: "
                + error.getClass().getSimpleName()
                + (error.getMessage() == null ? "" : " - " + error.getMessage())
                + " | candidates: "
                + apiClient.describeCandidateBaseUrls()
        );
        error.printStackTrace();
      }
    });
  }

  private void registerCommand() {
    PluginCommand command = getCommand("gitshopsync");
    if (command == null) {
      getLogger().warning("gitshopsync command is missing from plugin.yml");
      return;
    }

    command.setExecutor((sender, ignored, label, args) -> {
      if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
        reloadServices();
        schedulePollingTask();
        sender.sendMessage("[GitShop] Configuration reloaded.");
        return true;
      }

      Bukkit.getScheduler().runTaskAsynchronously(this, new OrderPoller(this, apiClient, deliveryLedger, rewardService));
      sender.sendMessage("[GitShop] Manual order sync started.");
      return true;
    });
  }
}
