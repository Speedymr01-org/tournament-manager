package com.tdm.tournament.metrics;

import com.tdm.tournament.TournamentPlugin;
import com.tdm.tournament.api.MinigameProvider;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.DrilldownPie;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * bStats integration for TournamentManager.
 *
 * <p>Custom chart suppliers are evaluated asynchronously by bStats, so they only
 * read values that are already held in memory (the provider list is replaced,
 * never mutated in place, after startup discovery).</p>
 *
 * <p>The {@code minigame_providers} chart is a drilldown pie: the outer slice is
 * the provider name, and clicking it reveals the provider versions in use, the
 * same way the bStats "Java Version" and "Operating System" charts work.</p>
 */
public class MetricsService {

    /** Plugin id from <a href="https://bstats.org/what-is-my-plugin-id">bstats.org</a>. */
    private static final int PLUGIN_ID = 33302;

    /** Keeps slice labels to a sane length for third-party providers. */
    private static final int MAX_LABEL_LENGTH = 48;

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
        // Outer slice = provider name, inner slice = provider version, mirroring
        // the bStats "Java Version" / "Operating System" drilldown charts.
        metrics.addCustomChart(new DrilldownPie("minigame_providers", this::providerDrilldown));
    }

    private Map<String, Map<String, Integer>> providerDrilldown() {
        Map<String, Map<String, Integer>> chart = new HashMap<>();

        List<MinigameProvider> providers;
        try {
            providers = plugin.getProviders();
        } catch (Exception e) {
            return Map.of("Unavailable", Map.of("error", 1));
        }

        if (providers.isEmpty()) {
            chart.put("None", Map.of("no provider installed", 1));
            return chart;
        }

        Map<MinigameProvider, String> knownVersions = plugin.getProviderVersions();
        for (MinigameProvider provider : providers) {
            try {
                String name = sliceName(provider);
                chart.computeIfAbsent(name, key -> new HashMap<>())
                        .merge(version(provider, knownVersions), 1, Integer::sum);
            } catch (Exception ignored) {
                // A broken third-party provider must not wipe out the whole chart.
            }
        }

        if (chart.isEmpty()) {
            chart.put("Unknown", Map.of("unknown", 1));
        }
        return chart;
    }

    /**
     * Provider name as shown on the chart. Custom providers are not required to
     * return a usable name, so this falls back to the display name and finally
     * the implementation class name.
     */
    private static String sliceName(MinigameProvider provider) {
        String name = provider.getPluginName();
        if (isBlank(name)) {
            name = provider.getDisplayName();
        }
        if (isBlank(name)) {
            name = provider.getClass().getSimpleName();
        }
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.length() > MAX_LABEL_LENGTH) {
            name = name.substring(0, MAX_LABEL_LENGTH);
        }
        return name.isEmpty() ? "Unknown" : name;
    }

    /**
     * Version shown when a provider slice is expanded:
     * the provider's own {@link MinigameProvider#getVersion()}, else the version
     * of the plugin that registered it, else a PluginManager lookup (covers
     * providers registered after startup discovery), else {@code "unknown"}.
     */
    private String version(MinigameProvider provider, Map<MinigameProvider, String> knownVersions) {
        String version = provider.getVersion();
        if (isBlank(version)) {
            version = knownVersions.get(provider);
        }
        if (isBlank(version)) {
            version = versionFromPluginManager(provider);
        }
        if (isBlank(version)) {
            return "unknown";
        }
        version = version.trim();
        return version.length() > MAX_LABEL_LENGTH ? version.substring(0, MAX_LABEL_LENGTH) : version;
    }

    private static String versionFromPluginManager(MinigameProvider provider) {
        try {
            Plugin owner = Bukkit.getPluginManager().getPlugin(provider.getPluginName());
            if (owner != null) {
                return owner.getPluginMeta().getVersion();
            }
        } catch (Exception ignored) {
            // Provider name is not a real plugin - fall through to "unknown".
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public void shutdown() {
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
    }
}
