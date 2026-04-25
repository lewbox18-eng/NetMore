package com.gitshop.plugin.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.net.URI;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;

public record PluginSettings(
    String baseUrl,
    String pluginToken,
    String serverId,
    int pollIntervalSeconds,
    int batchSize,
    boolean announcementEnabled,
    String announcementText,
    String shopUrl,
    String shopLinkText,
    boolean openReceiptGui,
    Map<String, List<String>> rewardCommands
) {
  public static PluginSettings from(FileConfiguration config) {
    return new PluginSettings(
        normalizeBaseUrl(config.getString("api.base-url", "http://127.0.0.1:8787")),
        config.getString("api.plugin-token", "change-plugin-token"),
        config.getString("api.server-id", "paper-survival-01"),
        Math.max(5, config.getInt("api.poll-interval-seconds", 20)),
        Math.max(1, config.getInt("api.batch-size", 10)),
        config.getBoolean("announcement.enabled", true),
        config.getString("announcement.text", "Player {username} has bought {items}!"),
        normalizeShopUrl(config.getString("announcement.shop-url", "https://your-shop.example.com")),
        config.getString("announcement.shop-link-text", "Visit our shop"),
        config.getBoolean("announcement.open-receipt-gui", true),
        loadRewardCommands(config.getConfigurationSection("rewards"))
    );
  }

  public List<String> commandsFor(String rewardKey) {
    return rewardCommands.getOrDefault(rewardKey, List.of());
  }

  private static Map<String, List<String>> loadRewardCommands(ConfigurationSection rewardsSection) {
    if (rewardsSection == null) {
      return Map.of();
    }

    Map<String, List<String>> rewards = new LinkedHashMap<>();
    for (String rewardKey : rewardsSection.getKeys(false)) {
      List<String> commands = new ArrayList<>();
      ConfigurationSection rewardSection = rewardsSection.getConfigurationSection(rewardKey);
      if (rewardSection != null) {
        commands.addAll(rewardSection.getStringList("commands"));
      } else {
        commands.addAll(rewardsSection.getStringList(rewardKey));
      }

      commands.removeIf(command -> command == null || command.trim().isEmpty());
      rewards.put(rewardKey, List.copyOf(commands));
    }

    return Map.copyOf(rewards);
  }

  private static String normalizeBaseUrl(String baseUrl) {
    String normalized = normalizeHttpUrl(baseUrl, "http://127.0.0.1:8787");

    try {
      URI parsed = URI.create(normalized);
      if (!"localhost".equalsIgnoreCase(parsed.getHost())) {
        return normalized;
      }

      return new URI(
          parsed.getScheme(),
          parsed.getUserInfo(),
          "127.0.0.1",
          parsed.getPort(),
          parsed.getPath(),
          parsed.getQuery(),
          parsed.getFragment()
      ).toString();
    } catch (IllegalArgumentException | java.net.URISyntaxException error) {
      return normalized;
    }
  }

  private static String normalizeShopUrl(String shopUrl) {
    return normalizeHttpUrl(shopUrl, "https://your-shop.example.com");
  }

  private static String normalizeHttpUrl(String value, String fallback) {
    String candidate = value == null || value.isBlank() ? fallback : value.trim();
    if (!hasScheme(candidate)) {
      candidate = guessScheme(candidate) + "://" + candidate;
    }

    try {
      URI parsed = URI.create(candidate);
      if (parsed.getHost() == null || parsed.getHost().isBlank()) {
        return fallback;
      }

      return parsed.toString();
    } catch (IllegalArgumentException error) {
      return fallback;
    }
  }

  private static boolean hasScheme(String value) {
    int schemeSeparator = value.indexOf("://");
    if (schemeSeparator <= 0) {
      return false;
    }

    for (int index = 0; index < schemeSeparator; index += 1) {
      char character = value.charAt(index);
      if (!Character.isLetterOrDigit(character) && character != '+' && character != '-' && character != '.') {
        return false;
      }
    }

    return true;
  }

  private static String guessScheme(String hostOrUrl) {
    String host = hostOrUrl;
    int slashIndex = host.indexOf('/');
    if (slashIndex >= 0) {
      host = host.substring(0, slashIndex);
    }

    if (host.startsWith("localhost")
        || host.startsWith("127.")
        || host.startsWith("10.")
        || host.startsWith("192.168.")
        || isPrivate172Range(host)) {
      return "http";
    }

    return "https";
  }

  private static boolean isPrivate172Range(String host) {
    if (!host.startsWith("172.")) {
      return false;
    }

    String[] segments = host.split("\\.");
    if (segments.length < 2) {
      return false;
    }

    try {
      int secondOctet = Integer.parseInt(segments[1]);
      return secondOctet >= 16 && secondOctet <= 31;
    } catch (NumberFormatException error) {
      return false;
    }
  }
}
