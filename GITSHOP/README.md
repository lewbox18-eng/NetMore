# GitShop

GitShop is a full foundation for a GitHub-hosted game shop that connects a static storefront, a backend order/payment API, and a Minecraft Paper plugin that fulfills purchases in-game.

If your Minecraft server is hosted remotely on a provider like UltraServers, the backend must be deployed on a public URL. A plugin running in a remote container cannot reach a backend running only on your home PC.

## Project Layout

```text
/frontend   Static storefront and admin UI, suitable for GitHub Pages
/backend    Node.js API, file-backed order store, webhook handler
/plugin     Paper plugin that claims, delivers, and acknowledges purchases
/docs       Architecture notes and schema reference
```

## Core Flow

1. A player opens the frontend shop, adds products to the cart, and enters their Minecraft username.
2. The frontend sends the checkout request to the backend.
3. The backend validates the username, generates or validates the order number, snapshots the purchased catalog items, and stores the new order.
4. The frontend triggers the mock payment flow.
5. The backend processes the signed payment webhook and marks the order as `paid`.
6. The Paper plugin polls the backend, claims paid orders, delivers rewards, broadcasts the purchase, opens a head-based receipt GUI when possible, and acknowledges delivery.
7. The backend marks the order as `delivered` and keeps logs for admin review.
8. Admins can open `/admin.html` to edit catalog items, descriptions, images, prices, and reward keys.

## Why This Shape Works Well

- `frontend` stays fully static, so it can be deployed to GitHub Pages.
- `backend` owns all trusted business logic: pricing, order creation, payment status, delivery state.
- `plugin` never trusts the browser. It only trusts the backend with a shared plugin token.
- Orders are claimed before delivery, which prevents duplicate fulfillment when multiple pollers are running.
- Failed deliveries can be reset from the admin view and retried safely.
- Catalog items can be edited from the admin view without touching source files by hand.

## Local Setup

### 1. Run the backend

```powershell
cd backend
node src/server.js
```

The API starts on `http://localhost:8787` by default.

### 2. Run the frontend

```powershell
cd frontend
node serve.js
```

The storefront starts on `http://localhost:8080`.

Open `http://localhost:8080/admin.html` and sign in with the configured `ADMIN_KEY` to edit catalog items, descriptions, images, prices, and reward keys.

### 3. Build the Paper plugin

```powershell
cd plugin
mvn -q -DskipTests package
```

The built jar will be placed in `plugin/target/`.

## GitHub Pages Deployment

1. Push the repository to GitHub.
2. Publish the `frontend` folder through GitHub Pages.
3. Update [`frontend/assets/config.js`](/C:/Users/user/Downloads/GITSHOP/frontend/assets/config.js) so `apiBaseUrl` points at your deployed backend.
4. Set the backend `FRONTEND_ORIGIN` environment variable to your GitHub Pages URL.

## Backend Configuration

Copy [`backend/.env.example`](/C:/Users/user/Downloads/GITSHOP/backend/.env.example) to `backend/.env` if you want environment-based configuration.

Key values:

- `PORT`: API port
- `FRONTEND_ORIGIN`: allowed browser origin
- `PLUGIN_TOKEN`: shared secret for the Paper plugin
- `ADMIN_KEY`: shared secret for the admin view
- `WEBHOOK_SECRET`: signature key for payment webhooks
- `SHOP_URL`: public storefront URL used in plugin announcements

## Plugin Installation

1. Build the plugin jar from `/plugin`.
2. Drop the jar into your Paper server `plugins` folder.
3. Start the server once to generate `plugins/GitShopBridge/config.yml`.
4. Update the API base URL, plugin token, shop URL, and `rewards:` command mappings in the generated config. For local same-machine testing, prefer `http://127.0.0.1:8787` over `http://localhost:8787`.
5. Restart the server.

## Remote Host Note

If you are using UltraServers or another hosted panel:

- do not point the plugin at `localhost`
- do not point the plugin at `127.0.0.1`
- do not point the plugin at your home LAN IP like `192.168.x.x`

Use a public backend URL instead, then set:

```yml
api:
  base-url: "https://your-public-backend.example"
```

There is a dedicated setup guide here:

- [`docs/ultraservers-setup.md`](/C:/Users/user/Downloads/GITSHOP/docs/ultraservers-setup.md)

## API Overview

- `GET /api/catalog`
- `POST /api/orders`
- `GET /api/orders/:id/verify`
- `POST /api/payments/mock/checkout`
- `POST /api/payments/mock/complete`
- `POST /api/webhooks/mock-payment`
- `POST /api/plugin/orders/claim`
- `POST /api/plugin/orders/:id/ack`
- `GET /api/admin/catalog`
- `POST /api/admin/catalog`
- `PUT /api/admin/catalog/:id`
- `DELETE /api/admin/catalog/:id`
- `GET /api/admin/orders`
- `POST /api/admin/orders/:id/resend`

## Database Shape

The default store is JSON-backed for easy local development and clear version control:

```json
{
  "sequences": {
    "order": 0,
    "payment": 0
  },
  "orders": [],
  "logs": []
}
```

Each order stores:

- `username`
- `orderNumber`
- `lineItems`
- `payment.status`
- `delivery.status`
- `timestamp fields`

Catalog items store:

- `id`
- `name`
- `description`
- `priceCents`
- `imageUrl`
- `rewardKey`

See [`docs/database-schema.md`](/C:/Users/user/Downloads/GITSHOP/docs/database-schema.md) for the complete example shape.
