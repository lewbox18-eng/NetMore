package com.gitshop.plugin.service;

import com.gitshop.plugin.model.PendingOrder;
import com.gitshop.plugin.model.PendingOrder.RewardLineItem;
import com.gitshop.plugin.model.PluginSettings;
import com.gitshop.plugin.api.ShopApiClient;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class AnnouncementService {
  private final JavaPlugin plugin;
  private final PluginSettings settings;
  private final ShopApiClient apiClient;

  public AnnouncementService(JavaPlugin plugin, PluginSettings settings, ShopApiClient apiClient) {
    this.plugin = plugin;
    this.settings = settings;
    this.apiClient = apiClient;
  }

  public void broadcastPurchase(PendingOrder order) {
    if (!settings.announcementEnabled()) {
      return;
    }

    String messageText = settings.announcementText()
        .replace("{username}", order.getUsername())
        .replace("{items}", order.describeItems());
    String shopUrl = apiClient.currentShopUrl(settings.shopUrl());

    Component message = Component.text("[Store] ", NamedTextColor.GOLD)
        .append(Component.text(messageText, NamedTextColor.WHITE))
        .append(Component.text(" "))
        .append(
            Component.text(settings.shopLinkText(), NamedTextColor.AQUA)
                .clickEvent(ClickEvent.openUrl(shopUrl))
                .hoverEvent(HoverEvent.showText(Component.text(shopUrl, NamedTextColor.GRAY)))
        );

    Bukkit.getServer().broadcast(message);
  }

  public void openReceiptGui(Player player, PendingOrder order) {
    if (!settings.openReceiptGui()) {
      return;
    }

    Inventory inventory = Bukkit.createInventory(null, 27, Component.text("Purchase Delivered", NamedTextColor.GOLD));
    inventory.setItem(13, createPlayerHead(order));

    int[] slots = {10, 11, 12, 14, 15, 16, 19, 20, 21, 22, 23};
    int index = 0;

    for (RewardLineItem item : order.getLineItems()) {
      if (index >= slots.length) {
        break;
      }
      inventory.setItem(slots[index], createRewardIcon(item));
      index += 1;
    }

    player.openInventory(inventory);
  }

  private ItemStack createPlayerHead(PendingOrder order) {
    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
    SkullMeta meta = (SkullMeta) head.getItemMeta();
    OfflinePlayer owner = Bukkit.getOfflinePlayer(order.getUsername());
    meta.setOwningPlayer(owner);
    meta.displayName(Component.text(order.getUsername() + " receipt", NamedTextColor.GOLD));
    meta.lore(List.of(
        Component.text("Order " + order.getOrderNumber(), NamedTextColor.GRAY),
        Component.text(order.describeItems(), NamedTextColor.WHITE)
    ));
    head.setItemMeta(meta);
    return head;
  }

  private ItemStack createRewardIcon(RewardLineItem lineItem) {
    Material material;
    try {
      material = Material.valueOf(lineItem.getIconMaterial());
    } catch (IllegalArgumentException error) {
      material = Material.CHEST;
    }

    ItemStack stack = new ItemStack(material);
    ItemMeta meta = stack.getItemMeta();
    meta.displayName(Component.text(lineItem.getName(), NamedTextColor.AQUA));

    List<Component> lore = new ArrayList<>();
    lore.add(Component.text(lineItem.getDescription(), NamedTextColor.GRAY));
    lore.add(Component.text("Qty: " + lineItem.getQuantity(), NamedTextColor.WHITE));
    meta.lore(lore);

    stack.setItemMeta(meta);
    return stack;
  }
}
