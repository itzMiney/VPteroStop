package com.itzminey.vPteroStop;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Path;

@Plugin(id = "vpterostop", name = "VPteroStop", version = "1.0.0")
public class VPteroStop {

    private final ProxyServer server;
    private final Logger logger;

    private Config config;
    private String apiKey;
    private String apiUrl;

    @Inject
    public VPteroStop(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        Path pluginFolder = Path.of("plugins", "VPteroStop");
        File configFile = pluginFolder.resolve("vpterostop.conf").toFile();

        if (!configFile.exists()) {
            logger.info("vpterostop.conf not found, creating default config...");
            try {
                if (!pluginFolder.toFile().exists()) {
                    pluginFolder.toFile().mkdirs();
                }

                try (InputStream in = getClass().getResourceAsStream("/vpterostop.conf");
                     FileWriter out = new FileWriter(configFile)) {

                    if (in == null) {
                        throw new IOException("Default config resource not found!");
                    }

                    String defaultConfig = new String(in.readAllBytes());
                    out.write(defaultConfig);
                    logger.info("Default config created successfully.");
                }
            } catch (IOException e) {
                logger.error("Failed to create default config:", e);
                return;
            }
        }

        config = ConfigFactory.parseFile(configFile);

        apiKey = config.getString("api-key");
        apiUrl = config.getString("api-url");
        logger.info("Loaded API key and URL.");

        server.getCommandManager().register("vstop", new VStopCommand(apiKey, apiUrl, server));
        server.getCommandManager().register("vstart", new VStartCommand(apiKey, apiUrl, server));
        server.getCommandManager().register("vrestart", new VRestartCommand(apiKey, apiUrl, server));
    }
}
