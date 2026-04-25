package com.gitshop.plugin.model;

import java.util.List;

public record DeliveryResult(boolean success, List<String> deliveredItems, String notes, String failureReason) {
  public static DeliveryResult success(List<String> deliveredItems, String notes) {
    return new DeliveryResult(true, deliveredItems, notes, "");
  }

  public static DeliveryResult failure(String failureReason) {
    return new DeliveryResult(false, List.of(), "", failureReason);
  }
}

