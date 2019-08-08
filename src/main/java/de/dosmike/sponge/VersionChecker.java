package de.dosmike.sponge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.SpongeExecutorService;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.plugin.meta.PluginDependency;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.spongepowered.api.Sponge.getPluginManager;

/** fully automatic and config-less implementation of a Ore conform plugin update checker.
 * Best call conformAuto */
public class VersionChecker {

    private static final Version versionCheckerVersion = new Version("1.1");

    /** Supports version in the format major[.minor[.build[.revision[-[stage][patch]]
     * where stage is sorted alphabetically (alpha, beta, rc, release).<br>
     * you may use a underscore instead of the dash and another optional underscore/dash between stage and patch.<br>
     * sort priority is left to right. */
    public static class Version implements Comparable<Version> {

        private int[] n;
        private String s;

        private static final Pattern versionPattern = Pattern.compile("(\\d+)(?:\\.(\\d+)(?:\\.(\\d+)(?:\\.(\\d+)(?:[-_]([a-zA-Z]+)?[-_]?(\\d+)?)?)?)?)?");
        public Version (String version) {
            Matcher m = versionPattern.matcher(version);
            if (!m.matches())
                throw new IllegalArgumentException("Invalid version string");
            String major = m.group(1);
            String minor = m.group(2);
            String build = m.group(3);
            String revis = m.group(4);
            String stage = m.group(5);
            String patch = m.group(6);
            n = new int[]{
                    Integer.parseInt(major),
                    minor == null ? -1 : Integer.parseInt(minor),
                    build == null ? -1 : Integer.parseInt(build),
                    revis == null ? -1 : Integer.parseInt(revis),
                    patch == null ? -1 : Integer.parseInt(patch)
            };
            s = stage;
        }

        /** @return <i><b>Major</b></i>.Minor.Build.Revision-StagePatch */
        public int getMajor() {
            return n[0];
        }
        /** @return Major.<i><b>Minor</b></i>.Build.Revision-StagePatch or -1 if not set */
        public int getMinor() {
            return n[1];
        }
        /** @return Major.Minor.<i><b>Build</b></i>.Revision-StagePatch or -1 if not set */
        public int getBuild() {
            return n[2];
        }
        /** @return Major.Minor.Build.<i><b>Revision</b></i>-StagePatch or -1 if not set */
        public int getRevision() {
            return n[3];
        }
        /** @return Major.Minor.Build.Revision-Stage<i><b>Patch</b></i> or -1 if not set */
        public int getPatch() {
            return n[4];
        }
        /** @return Major.Minor.Build.Revision-<i><b>Stage</b></i>Patch or -1 if not set */
        public String getStage() {
            return s;
        }

        /** @return Major.Minor.Build.Revision-StagePatch up until the last missing elements counting from right.<br>
         *          e.g. Major 1, Minor 2, Stage Release would translate to 1.2-Release, Major 1, Revision 5 would translate to 1.0.0.5
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(n[0]);
            if (n[1]>=0) { sb.append('.'); sb.append(n[1]); }
            else if (n[2]>0 || n[3]>0) { sb.append(".0"); }
            if (n[2]>=0) { sb.append('.'); sb.append(n[2]); }
            else if (n[3]>0) { sb.append(".0"); }
            if (n[3]>=0) { sb.append('.'); sb.append(n[3]); }
            if (n[4]>=0 || s!=null) {
                sb.append('-');
                if (s!=null) sb.append(s);
                if (n[4]>=0) sb.append(n[4]);
            }
            return sb.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(n, s);
        }

        @Override
        public int compareTo(Version o) {
            int c;
            if ((c=Integer.compare(n[0], o.n[0]))!=0) return c;
            if ((c=Integer.compare(n[1], o.n[1]))!=0) return c;
            if ((c=Integer.compare(n[2], o.n[2]))!=0) return c;
            if ((c=Integer.compare(n[3], o.n[3]))!=0) return c;
            if ((c=s.compareTo(o.s))!=0) return c;
            if ((c=Integer.compare(n[4], o.n[4]))!=0) return c;
            return 0;
        }
    }

    private static Set<String> allowedVersionChecking = new HashSet<>();
    /** By Ore Guidelines your are NOT allowed to call this without passing a config value to @enabled
     * @param pluginID the plugins that's config this originates from
     * @param enabled HAS to be configurationNode.getBoolean(false)
     */
    public static void setVersionCheckingEnabled(String pluginID, boolean enabled) {
        if (enabled) allowedVersionChecking.add(pluginID);
        else allowedVersionChecking.remove(pluginID);
    }
    /** In case a manual check wan't to validate */
    public static boolean isVersionCheckingAllowed(String pluginId) {
        return allowedVersionChecking.contains(pluginId);
    }
    /** checks if the specified config allows version checking. if so, performs version check on provided executor
     * @param instance the plugin instance to check for updates
     * @param configDir where to search for the configuration (private plugin directory is recommended)
     * @param configName the name of the config file, if configDir is the public config dir it's recommended to start the filename with the plugin id
     */
    public static void conformAuto(Object instance, Path configDir, String configName) {
        configDir.toFile().mkdirs();
        PluginContainer plugin = getPluginManager().fromInstance(instance).orElseThrow(()->new IllegalArgumentException("Passed onject is not a plugin"));
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .setPath(configDir.resolve(configName))
                .build();
        try {
            CommentedConfigurationNode root = loader.load(ConfigurationOptions.defaults());
            if (root.getNode("enabled").isVirtual()) {
                root = loader.createEmptyNode(ConfigurationOptions.defaults());
                CommentedConfigurationNode node = root.getNode("enabled");
                node.setComment("It's strongly recommended to enable automatic version checking,\n" +
                        "This will also inform you about changes in dependencies.\n" +
                        "Set this value to true to allow this Plugin to check for Updates on Ore");
                node.setValue(false);
                loader.save(root);
                setVersionCheckingEnabled(plugin.getId(), false);
            } else {
                setVersionCheckingEnabled(plugin.getId(), root.getNode("enabled").getBoolean(false));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Task.builder().name("VersionChecker").async().execute(()->checkPluginVersion(plugin)).submit(instance);
    }

    public static void checkVersion(Object instance) {
        PluginContainer plugin = getPluginManager().fromInstance(instance).orElseThrow(()->new IllegalArgumentException("Passed onject is not a plugin"));
        checkPluginVersion(plugin);
    }

    public static void checkPluginVersion(PluginContainer plugin) {
        plugin.getLogger().info("Searching for updates...");
        if (allowedVersionChecking.contains(plugin.getId()))
            _checkPluginVersion(plugin);
        else {
            plugin.getLogger().warn("The automatic Version Checker for "+plugin.getName()+"("+plugin.getId()+") is disabled!");
            plugin.getLogger().warn("It is strongly recommended to activate the Version Checker to keep your server secure and up to date!");
        }
    }

    private static void _checkPluginVersion(PluginContainer plugin) {
        Version currentVersion = new Version(plugin.getVersion().get());
        URL apiURL;
        JsonReader reader=null;
        try {
            apiURL = new URL("https://ore.spongepowered.org/api/v1/projects/"+plugin.getId());
            HttpsURLConnection connection = (HttpsURLConnection)apiURL.openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Version Checker ("+versionCheckerVersion.toString()+" by DosMike)/Plugin "+plugin.getName()+"("+plugin.getId()+" "+plugin.getVersion()+")");
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (connection.getResponseCode() != 200) throw new RuntimeException();
            reader = new JsonReader(new InputStreamReader(connection.getInputStream()));
            JsonParser parser = new JsonParser();
            JsonElement root = parser.parse(reader);
            JsonObject recommended = root.getAsJsonObject().get("recommended").getAsJsonObject();

            Version recommendedVersion = new Version(recommended.get("name").getAsString());
            if (recommendedVersion.compareTo(currentVersion)>0) {

                plugin.getLogger().warn("Update Found: "+plugin.getName()+"("+plugin.getId()+") Version "+recommendedVersion.toString()+" is available on Ore!");

                String[] updateText = recommended.get("description").getAsString().split("(?:\\r)?\\n");
                for (String line : updateText)
                    plugin.getLogger().warn("  "+line);

                _checkPluginDependencies(plugin, recommended.get("dependencies").getAsJsonArray());
            }
            connection.disconnect();
        } catch (Exception e) {
            plugin.getLogger().warn("VersionChecker for "+plugin.getId()+" could not connect to ORE");
        } finally {
            try { reader.close(); } catch (Exception ignore) {}
        }
    }

    private static void _checkPluginDependencies(PluginContainer plugin, JsonArray dependencies) {
        Map<String, Version> newDeps = new HashMap<>();
        Set<String> ignore = new HashSet<>();
        for (int i = 0; i < dependencies.size(); i++) {
            JsonObject obj = dependencies.get(i).getAsJsonObject();
            String pluginId = obj.get("pluginId").getAsString();
            String versionString = obj.get("version").getAsString();
            Version version;
            try {
                if (versionString.startsWith("[") || versionString.startsWith("(")) {
                    String[] versionBounds = versionString.substring(1).split(",");
                    version = new Version(versionBounds[0]);
                } else {
                    version = new Version(versionString);
                }
            } catch (IllegalArgumentException e) { //strange version string
                ignore.add(pluginId);
                continue;
            }
            newDeps.put( pluginId, version);
        }
        for (String pluginId : newDeps.keySet()) {
            Optional<PluginContainer> dep = getPluginManager().getPlugin(pluginId);
            if (!dep.isPresent()) {
                plugin.getLogger().warn("> New Dependency: "+pluginId+" version "+newDeps.get(pluginId).toString());
            } else {
                try {
                    Version depCurrentVersion = new Version(dep.get().getVersion().get());
                    if (depCurrentVersion.compareTo(newDeps.get(pluginId))<0) {
                        plugin.getLogger().warn("> Requires Dependency Update: "+pluginId+" to version "+newDeps.get(pluginId).toString());
                    }
                } catch (Exception e) {}
            }
        }
        for (PluginDependency dep : plugin.getDependencies()) {
            if (!newDeps.containsKey(dep.getId()) && !ignore.contains(dep.getId())) {
                plugin.getLogger().warn("> Old Dependency: "+dep.getId()+" no longer required in "+plugin.getId()+" version "+plugin.getVersion());
            }
        }
    }

}
