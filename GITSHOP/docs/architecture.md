# Architecture

GitShop is split into three deployable units so the storefront can live on GitHub Pages without weakening the trusted order and delivery flow.

## Frontend

- Static HTML, CSS, and JavaScript only
- Fetches catalog and order state from the backend
- Includes an admin editor for catalog items, images, descriptions, and reward commands
- Runs locally with `node serve.js`
- Deploys cleanly to GitHub Pages

## Backend

- Node.js with no external runtime dependencies
- Validates usernames, cart contents, and order numbers
- Stores orders in `backend/data/store.json`
- Stores editable catalog items in `backend/data/catalog.json`
- Exposes public shop APIs, protected admin APIs, and protected plugin APIs
- Processes signed mock webhooks before the plugin ever sees an order

## Plugin

- Paper plugin that polls asynchronously
- Claims orders instead of reading them blindly
- Executes reward commands on the main server thread
- Persists a local delivery ledger in `processed-orders.yml`
- Broadcasts a clickable shop message and can open a player-head receipt GUI

## Order Lifecycle

1. `POST /api/orders`
2. `POST /api/payments/mock/checkout`
3. `POST /api/payments/mock/complete`
4. `POST /api/webhooks/mock-payment`
5. `POST /api/plugin/orders/claim`
6. `POST /api/plugin/orders/:id/ack`

## Security Model

- The browser never decides prices or reward commands.
- Catalog item IDs are the only thing accepted from the storefront.
- Reward commands are pulled from the server-side catalog and snapshotted into the order.
- Payment status is only changed through a signed webhook path.
- Plugin access requires `X-Plugin-Token`.
- Admin access requires `X-Admin-Key`.
- Duplicate protection exists on both the backend and the plugin.
