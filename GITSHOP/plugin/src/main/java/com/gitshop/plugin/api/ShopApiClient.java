package com.gitshop.plugin.api;

import com.gitshop.plugin.model.DeliveryResult;
import com.gitshop.plugin.model.PendingOrder;
import com.gitshop.plugin.model.PluginSettings;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ShopApiClient {
  private static final int DEFAULT_BACKEND_PORT = 8787;

  private final PluginSettings settings;
  private final HttpClient httpClient;
  private final Gson gson;
  private volatile URI activeBaseUri;
  private volatile String activeShopUrl;

  public ShopApiClient(PluginSettings settings) {
    this.settings = settings;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4))
        .build();
    this.gson = new Gson();
    this.activeBaseUri = normalizeBaseUri(settings.baseUrl());
    this.activeShopUrl = null;
  }

  public boolean ping() throws IOException, InterruptedException {
    IOException lastError = null;

    for (URI baseUri : candidateBaseUris()) {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(resolve(baseUri, "/health"))
          .timeout(Duration.ofSeconds(6))
          .GET()
          .build();

      try {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
          captureHealthMetadata(response.body());
          activeBaseUri = baseUri;
          return true;
        }
        lastError = new IOException("Unexpected backend status " + response.statusCode() + " from " + baseUri);
      } catch (InterruptedException error) {
        throw error;
      } catch (IOException error) {
        lastError = error;
      }
    }

    if (lastError != null) {
      throw lastError;
    }

    throw new IOException("Could not reach GitShop backend");
  }

  public List<PendingOrder> claimOrders() throws IOException, InterruptedException {
    JsonObject body = new JsonObject();
    body.addProperty("serverId", settings.serverId());
    body.addProperty("limit", settings.batchSize());

    JsonObject response = sendJson(
        "/api/plugin/orders/claim",
        "POST",
        gson.toJson(body)
    );

    JsonArray ordersJson = response.getAsJsonArray("orders");
    List<PendingOrder> orders = new ArrayList<>();
    if (ordersJson == null) {
      return orders;
    }

    ordersJson.forEach(element -> orders.add(gson.fromJson(element, PendingOrder.class)));
    return orders;
  }

  public void acknowledge(String orderId, String claimToken, DeliveryResult result) throws IOException, InterruptedException {
    JsonObject body = new JsonObject();
    body.addProperty("claimToken", claimToken);
    body.addProperty("success", result.success());
    body.addProperty("notes", result.notes());
    if (!result.success()) {
      body.addProperty("failureReason", result.failureReason());
    }

    JsonArray deliveredItems = new JsonArray();
    for (String deliveredItem : result.deliveredItems()) {
      deliveredItems.add(deliveredItem);
    }
    body.add("deliveredItems", deliveredItems);

    sendJson("/api/plugin/orders/" + orderId + "/ack", "POST", gson.toJson(body));
  }

  public String currentBaseUrl() {
    return activeBaseUri.toString();
  }

  public String currentShopUrl(String fallbackUrl) {
    if (activeShopUrl == null || activeShopUrl.isBlank()) {
      return fallbackUrl;
    }
    return activeShopUrl;
  }

  public String describeCandidateBaseUrls() {
    return String.join(", ", candidateBaseUris().stream().map(URI::toString).toList());
  }

  private JsonObject sendJson(String path, String method, String requestBody) throws IOException, InterruptedException {
    IOException lastIoError = null;
    RuntimeException lastRuntimeError = null;

    for (URI baseUri : candidateBaseUris()) {
      try {
        JsonObject payload = sendJson(baseUri, path, method, requestBody);
        activeBaseUri = baseUri;
        return payload;
      } catch (InterruptedException error) {
        throw error;
      } catch (IOException error) {
        lastIoError = error;
        if (!shouldTryNextCandidate(error)) {
          throw error;
        }
      } catch (RuntimeException error) {
        lastRuntimeError = error;
      }
    }

    if (lastIoError != null) {
      throw lastIoError;
    }

    if (lastRuntimeError != null) {
      throw new IOException("Invalid backend response from " + currentBaseUrl(), lastRuntimeError);
    }

    throw new IOException("Could not reach GitShop backend");
  }

  private JsonObject sendJson(URI baseUri, String path, String method, String requestBody)
      throws IOException, InterruptedException {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(resolve(baseUri, path))
        .timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/json")
        .header("X-Plugin-Token", settings.pluginToken());

    if ("POST".equalsIgnoreCase(method)) {
      builder.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
    } else {
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    }

    HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
    if (response.statusCode() < 200 || response.statusCode() >= 300 || !payload.get("ok").getAsBoolean()) {
      String errorMessage = payload.has("error") ? payload.get("error").getAsString() : "Backend request failed";
      throw new IOException(errorMessage);
    }

    return payload;
  }

  private List<URI> candidateBaseUris() {
    Set<String> candidates = new LinkedHashSet<>();
    URI preferred = activeBaseUri == null ? normalizeBaseUri(settings.baseUrl()) : activeBaseUri;
    URI configured = normalizeBaseUri(settings.baseUrl());

    addCandidate(candidates, preferred);
    addCandidate(candidates, configured);

    int configuredPort = configured.getPort() > 0 ? configured.getPort() : DEFAULT_BACKEND_PORT;
    String scheme = configured.getScheme() == null ? "http" : configured.getScheme();
    String path = normalizeBasePath(configured.getPath());

    if (configuredPort != DEFAULT_BACKEND_PORT) {
      addCandidate(candidates, buildBaseUri(scheme, configured.getHost(), DEFAULT_BACKEND_PORT, path));
    }

    addCandidate(candidates, buildBaseUri(scheme, "127.0.0.1", DEFAULT_BACKEND_PORT, path));
    addCandidate(candidates, buildBaseUri(scheme, "localhost", DEFAULT_BACKEND_PORT, path));
    addCandidate(candidates, buildBaseUri(scheme, "host.docker.internal", DEFAULT_BACKEND_PORT, path));
    addCandidate(candidates, buildBaseUri(scheme, "gateway.docker.internal", DEFAULT_BACKEND_PORT, path));

    List<String> localIps = localIpv4Addresses();
    for (String localIp : localIps) {
      addCandidate(candidates, buildBaseUri(scheme, localIp, DEFAULT_BACKEND_PORT, path));
    }
    for (String gatewayIp : dockerGatewayCandidates(localIps)) {
      addCandidate(candidates, buildBaseUri(scheme, gatewayIp, DEFAULT_BACKEND_PORT, path));
    }

    List<URI> uris = new ArrayList<>();
    for (String candidate : candidates) {
      uris.add(URI.create(candidate));
    }
    return uris;
  }

  private static void addCandidate(Set<String> candidates, URI uri) {
    if (uri == null || uri.getHost() == null || uri.getHost().isBlank()) {
      return;
    }
    candidates.add(stripTrailingSlash(uri.toString()));
  }

  private static URI normalizeBaseUri(String baseUrl) {
    String normalized = baseUrl == null || baseUrl.isBlank()
        ? "http://127.0.0.1:8787"
        : baseUrl.trim();
    return URI.create(stripTrailingSlash(normalized));
  }

  private static URI buildBaseUri(String scheme, String host, int port, String path) {
    try {
      return new URI(scheme, null, host, port, path, null, null);
    } catch (URISyntaxException error) {
      return URI.create(scheme + "://" + host + ":" + port + normalizeBasePath(path));
    }
  }

  private static String normalizeBasePath(String path) {
    if (path == null || path.isBlank() || "/".equals(path)) {
      return "";
    }
    return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }

  private static String stripTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private static List<String> localIpv4Addresses() {
    List<String> addresses = new ArrayList<>();

    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      if (interfaces == null) {
        return addresses;
      }

      for (NetworkInterface networkInterface : Collections.list(interfaces)) {
        if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
          continue;
        }

        for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
          if (address instanceof Inet4Address ipv4 && !ipv4.isLoopbackAddress()) {
            addresses.add(ipv4.getHostAddress());
          }
        }
      }
    } catch (Exception ignored) {
      return addresses;
    }

    return addresses;
  }

  private static List<String> dockerGatewayCandidates(List<String> localIps) {
    Set<String> gateways = new LinkedHashSet<>();
    gateways.add("172.17.0.1");
    gateways.add("172.18.0.1");
    gateways.add("172.19.0.1");
    gateways.add("172.20.0.1");

    for (String ip : localIps) {
      String[] parts = ip.split("\\.");
      if (parts.length != 4) {
        continue;
      }

      try {
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        int third = Integer.parseInt(parts[2]);

        if (first == 172 && second >= 16 && second <= 31) {
          gateways.add(first + "." + second + "." + third + ".1");
        }
      } catch (NumberFormatException ignored) {
        // Skip malformed addresses.
      }
    }

    return new ArrayList<>(gateways);
  }

  private static boolean shouldTryNextCandidate(IOException error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof ConnectException
          || current instanceof ClosedChannelException
          || current instanceof UnknownHostException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static URI resolve(URI baseUri, String path) {
    return URI.create(stripTrailingSlash(baseUri.toString()) + path);
  }

  private void captureHealthMetadata(String responseBody) {
    try {
      JsonObject payload = JsonParser.parseString(responseBody).getAsJsonObject();
      if (payload.has("shopUrl")) {
        String discoveredShopUrl = payload.get("shopUrl").getAsString();
        if (discoveredShopUrl != null && !discoveredShopUrl.isBlank()) {
          activeShopUrl = discoveredShopUrl;
        }
      }
    } catch (RuntimeException ignored) {
      // Health metadata is optional; keep the plugin working even if parsing fails.
    }
  }
}
