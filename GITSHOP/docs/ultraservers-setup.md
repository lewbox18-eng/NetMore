# UltraServers Setup

If your Minecraft server is running on UltraServers, the Paper plugin is not running on your home PC. It is running in a remote container.

That means these addresses will not work for the plugin:

- `http://127.0.0.1:8787`
- `http://localhost:8787`
- `http://192.168.x.x:8787`

Those only work when the plugin and backend are on the same local network.

## What Actually Works

The backend must be reachable on a public URL, for example:

```text
https://gitshop-api.yourdomain.com
```

Then the live Paper plugin config on UltraServers should use:

```yml
api:
  base-url: "https://gitshop-api.yourdomain.com"
  plugin-token: "change-plugin-token"
  server-id: "paper-survival-01"
  poll-interval-seconds: 20
  batch-size: 10
```

## Deploy the Backend

The backend in `/backend` now includes a `Dockerfile`, so you can deploy it on any normal Node or Docker host.

Minimum environment variables:

```text
HOST=0.0.0.0
PORT=8787
FRONTEND_ORIGIN=https://your-github-pages-site.example
PLUGIN_TOKEN=change-plugin-token
ADMIN_KEY=change-admin-key
WEBHOOK_SECRET=change-webhook-secret
SHOP_URL=https://your-shop.example.com
PAYMENT_PROVIDER=mock
```

### Docker Run Example

```bash
docker build -t gitshop-backend ./backend
docker run -d \
  --name gitshop-backend \
  -p 8787:8787 \
  -e HOST=0.0.0.0 \
  -e PORT=8787 \
  -e FRONTEND_ORIGIN=https://your-github-pages-site.example \
  -e PLUGIN_TOKEN=change-plugin-token \
  -e ADMIN_KEY=change-admin-key \
  -e WEBHOOK_SECRET=change-webhook-secret \
  -e SHOP_URL=https://your-shop.example.com \
  gitshop-backend
```

### Plain Node Example

```bash
cd backend
npm install
PORT=8787 HOST=0.0.0.0 node src/server.js
```

## What To Change On UltraServers

1. Upload the current plugin jar to the server:
   - `plugin/target/gitshop-plugin-1.0.1.jar`
2. Fully stop the Minecraft server.
3. Replace the old `GitShopBridge` jar in the UltraServers file manager.
4. Open the live file:
   - `plugins/GitShopBridge/config.yml`
5. Set `api.base-url` to your public backend URL.
6. Start the server again.

## Quick Verification

The public backend should answer:

```text
GET https://gitshop-api.yourdomain.com/health
```

Expected result:

```json
{"ok":true,"status":"healthy","paymentProvider":"mock"}
```

The Paper console should then show a successful backend connection instead of a connect timeout.
