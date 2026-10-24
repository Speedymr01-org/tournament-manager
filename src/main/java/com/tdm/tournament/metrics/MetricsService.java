package com.tdm.tournament.metrics;

import com.tdm.tournament.TournamentPlugin;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

/**
 * bStats integration for TournamentManager.
 *
 * <p>Custom chart suppliers are evaluated asynchronously by bStats, so they only
 * read values that are already held in memory (the provider list is replaced,
 * never mutated in place, after startup discovery).</p>
 */
public class MetricsService {

    /** Plugin id from <a href="https://bstats.org/what-is-my-plugin-id">bstats.org</a>. */
    private static final int PLUGIN_ID = 33302;

    private final TournamentPlugin plugin;
    private Metrics metrics;

    public MetricsService(TournamentPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("metrics-enabled", true)) {
            plugin.getLogger().info("bStats metrics are disabled in config.yml.");
            return;
        }
        try {
            this.metrics = new Metrics(plugin, PLUGIN_ID);
            registerCharts(metrics);
            plugin.getLogger().info("bStats metrics enabled (plugin id " + PLUGIN_ID + ").");
        } catch (Exception e) {
            // Telemetry must never prevent the plugin from enabling.
            this.metrics = null;
            plugin.getLogger().warning("Could not start bStats metrics: " + e.getMessage());
        }
    }

    private void registerCharts(Metrics metrics) {
        metrics.addCustomChart(new SimplePie("minigame_providers", this::providerBucket));
    }

    private String providerBucket() {
        int count = plugin.getProviders().size();
        if (count == 0) {
            return "0";
        }
        if (count == 1) {
            return "1";
        }
        if (count == 2) {
            return "2";
        }
        return "3+";
    }

    public void shutdown() {
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
    }
}
