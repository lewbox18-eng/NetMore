# Database Schema

GitShop uses a JSON-backed store by default so the full order lifecycle is easy to inspect while developing.

## Store File

```json
{
  "sequences": {
    "order": 7,
    "payment": 7
  },
  "orders": [
    {
      "id": "ord_abc123",
      "orderNumber": "ORD-20260425-00007",
      "username": "PlayerOne",
      "usernameNormalized": "playerone",
      "lineItems": [
        {
          "productId": "vip-rank",
          "name": "VIP Rank",
          "description": "Permanent VIP rank with chat flair, /hat, and 2 extra homes.",
          "category": "Ranks",
          "quantity": 1,
          "priceCents": 999,
          "icon": "VIP",
          "accent": "#d95b37",
          "iconMaterial": "GOLDEN_HELMET",
          "rewardCommands": [
            "lp user {username} parent add vip"
          ]
        }
      ],
      "totals": {
        "currency": "USD",
        "itemCount": 1,
        "subtotalCents": 999
      },
      "payment": {
        "provider": "mock",
        "status": "paid",
        "reference": "MOCK-000007",
        "paidAt": "2026-04-25T09:32:00.000Z"
      },
      "delivery": {
        "status": "delivered",
        "claimedBy": null,
        "claimToken": null,
        "claimedAt": null,
        "deliveredAt": "2026-04-25T09:33:12.000Z",
        "failureReason": null,
        "retryCount": 0,
        "receipt": {
          "deliveredItems": [
            "lp user PlayerOne parent add vip"
          ],
          "notes": "Fulfilled by Paper plugin"
        }
      },
      "source": {
        "ipAddress": "::1",
        "userAgent": "Mozilla/5.0",
        "origin": "http://localhost:8080"
      },
      "createdAt": "2026-04-25T09:31:40.000Z",
      "updatedAt": "2026-04-25T09:33:12.000Z"
    }
  ],
  "logs": [
    {
      "id": "log_abc123",
      "scope": "plugin",
      "event": "delivered",
      "details": {
        "orderId": "ord_abc123",
        "orderNumber": "ORD-20260425-00007"
      },
      "timestamp": "2026-04-25T09:33:12.000Z"
    }
  ]
}
```

## Notes

- `payment.status` is the trusted signal for whether the order can be fulfilled.
- `delivery.status` tracks `pending`, `claimed`, `delivered`, or `failed`.
- `claimToken` is temporary and prevents one plugin instance from acknowledging another instance's claim.
- `rewardCommands` are snapped from the catalog at order time so product changes do not rewrite past purchases.
