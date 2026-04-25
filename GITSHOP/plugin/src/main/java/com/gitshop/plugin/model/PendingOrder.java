package com.gitshop.plugin.model;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class PendingOrder {
  private String id;
  private String orderNumber;
  private String username;
  private String claimToken;
  private List<RewardLineItem> lineItems = List.of();

  public String getId() {
    return id;
  }

  public String getOrderNumber() {
    return orderNumber;
  }

  public String getUsername() {
    return username;
  }

  public String getClaimToken() {
    return claimToken;
  }

  public List<RewardLineItem> getLineItems() {
    return lineItems == null ? List.of() : Collections.unmodifiableList(lineItems);
  }

  public String describeItems() {
    return getLineItems().stream()
        .map(item -> item.getQuantity() > 1 ? item.getQuantity() + "x " + item.getName() : item.getName())
        .collect(Collectors.joining(", "));
  }

  public static final class RewardLineItem {
    private String productId;
    private String name;
    private String description;
    private String category;
    private int quantity;
    private int priceCents;
    private String icon;
    private String accent;
    private String iconMaterial;
    private String rewardKey;

    public String getProductId() {
      return productId;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public String getCategory() {
      return category;
    }

    public int getQuantity() {
      return quantity;
    }

    public int getPriceCents() {
      return priceCents;
    }

    public String getIcon() {
      return icon;
    }

    public String getAccent() {
      return accent;
    }

    public String getIconMaterial() {
      return iconMaterial;
    }

    public String getRewardKey() {
      return rewardKey == null ? "" : rewardKey;
    }
  }
}
