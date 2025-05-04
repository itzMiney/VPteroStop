package com.itzminey.vPteroStop;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;

import net.kyori.adventure.text.format.NamedTextColor;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;
import java.util.stream.Collectors;

public class VStopCommand implements SimpleCommand {

    private final String apiKey;
    private final String apiUrl;
    private final ProxyServer proxyServer;

    public VStopCommand(String apiKey, String apiUrl, ProxyServer server) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.proxyServer = server;
    }

    @Override
    public java.util.List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 0 || args.length == 1) {
            String current = args.length == 1 ? args[0].toLowerCase() : "";
            return proxyServer.getAllServers().stream()
                    .map(server -> server.getServerInfo().getName())
                    .filter(name -> name.toLowerCase().startsWith(current))
                    .collect(Collectors.toList());
        }

        return java.util.Collections.emptyList();
    }

    public void execute (Invocation invocation){

        CommandSource source = invocation.source();

        String requiredPermission = "vPteroStop.vstop";

        if (!source.hasPermission(requiredPermission)) {
            source.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();

        if (args.length != 1) {
            source.sendMessage(Component.text("Usage: /vstop <server_name>").color(NamedTextColor.GRAY));
            return;
        }

        String serverName = args[0];
        Optional<RegisteredServer> serverOpt = proxyServer.getServer(serverName);
        if (serverOpt.isEmpty()) {
            source.sendMessage(Component.text("Server not found: " + serverName).color(NamedTextColor.RED));
            return;
        }

        int port = serverOpt.get().getServerInfo().getAddress().getPort();

        try {
            URL url = new URL(apiUrl + "/api/client");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.connect();

            if (conn.getResponseCode() != 200) {
                source.sendMessage(Component.text("API request failed with code: " + conn.getResponseCode()).color(NamedTextColor.RED));
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String responseBody = reader.lines().collect(Collectors.joining());
            reader.close();

            JSONObject responseJson = new JSONObject(responseBody);
            JSONArray servers = responseJson.getJSONArray("data");

            String matchingIdentifier = null;

            for (int i = 0; i < servers.length(); i++) {
                JSONObject server = servers.getJSONObject(i);
                JSONObject attributes = server.getJSONObject("attributes");
                JSONArray allocations = attributes.getJSONObject("relationships")
                        .getJSONObject("allocations")
                        .getJSONArray("data");

                for (int j = 0; j < allocations.length(); j++) {
                    JSONObject allocation = allocations.getJSONObject(j).getJSONObject("attributes");
                    int allocationPort = allocation.getInt("port");

                    if (allocationPort == port) {
                        matchingIdentifier = attributes.getString("identifier");
                        break;
                    }
                }
            }

            if (matchingIdentifier == null) {
                source.sendMessage(Component.text("No matching server found for port: " + port).color(NamedTextColor.RED));
                return;
            }

            URL stopUrl = new URL(apiUrl + "/api/client/servers/" + matchingIdentifier + "/power");
            HttpURLConnection stopConn = (HttpURLConnection) stopUrl.openConnection();
            stopConn.setRequestMethod("POST");
            stopConn.setRequestProperty("Authorization", "Bearer " + apiKey);
            stopConn.setRequestProperty("Content-Type", "application/json");
            stopConn.setDoOutput(true);

            String postData = "{\"signal\": \"stop\"}";
            stopConn.getOutputStream().write(postData.getBytes());

            int stopCode = stopConn.getResponseCode();
            if (stopCode == 204) {
                source.sendMessage(Component.text("Stop signal sent to server " + serverName).color(NamedTextColor.GREEN));
            } else {
                source.sendMessage(Component.text("Failed to stop server. HTTP " + stopCode).color(NamedTextColor.RED));
            }

        } catch (Exception e) {
            source.sendMessage(Component.text("Error: " + e.getMessage()).color(NamedTextColor.RED));
            e.printStackTrace();
        }
    }
}