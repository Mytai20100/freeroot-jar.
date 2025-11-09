//Make by mytai
//My repo(https://github.com/Mytai20100/freeroot-jar)
//Hmmm
package org;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class freeroot extends JavaPlugin {

    private final List<String> lastLogs = new ArrayList<>();
    private volatile boolean isInitializing = false;
    private volatile boolean isInitialized = false;
    private volatile boolean consoleLogging = true;

    // Config file
    private File configFile;
    private FileConfiguration config;

    // User session data
    private final ConcurrentHashMap<String, String> userWorkingDir = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProcessBuilder> userEnvironment = new ConcurrentHashMap<>();

    // Startup commands
    private List<String> startupCommands = new ArrayList<>();

    @Override
    public void onEnable() {
        // Load config
        loadConfig();

        // Apply saved settings
        consoleLogging = config.getBoolean("console-logging", true);
        startupCommands = config.getStringList("startup-commands");

        getLogger().info(colorize("&a[*] Freeroot Plugin Enabled!"));
        getLogger().info(colorize("&7[*] Console Logging: " + (consoleLogging ? "&aEnabled" : "&cDisabled")));
        getLogger().info(colorize("&7[*] Startup Commands: &e" + startupCommands.size()));

        // Execute startup commands
        if (!startupCommands.isEmpty()) {
            executeStartupCommands();
        }
    }

    @Override
    public void onDisable() {
        // Save config before disable
        saveConfig();
        getLogger().info(colorize("&c[*] Freeroot Plugin Disabled."));
    }

    private void loadConfig() {
        configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Set default values if not exist
        if (!config.contains("console-logging")) {
            config.set("console-logging", true);
        }
        if (!config.contains("startup-commands")) {
            config.set("startup-commands", new ArrayList<String>());
        }

        saveConfigFile();
    }

    private void saveConfigFile() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().severe("Could not save config file: " + e.getMessage());
        }
    }

    private void executeStartupCommands() {
        getLogger().info(colorize("&e[*] Executing startup commands..."));

        new BukkitRunnable() {
            @Override
            public void run() {
                for (String command : startupCommands) {
                    getLogger().info(colorize("&7[STARTUP] Executing: &f" + command));
                    executeCommand(null, command, "STARTUP_USER");

                    // Small delay between commands
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                getLogger().info(colorize("&a[+] All startup commands completed!"));
            }
        }.runTaskAsynchronously(this);
    }

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("root")) {
            String senderKey = sender.getName();

            // Handle special commands first
            if (args.length == 0) {
                sender.sendMessage(colorize("&c┌─ &lFreeroot Plugin Commands &c─┐"));
                sender.sendMessage(colorize("&7│ &f/root <command>     &7- Execute command"));
                sender.sendMessage(colorize("&7│ &f/root log          &7- View last logs"));
                sender.sendMessage(colorize("&7│ &f/root pwd          &7- Show current dir"));
                sender.sendMessage(colorize("&7│ &f/root reset        &7- Reset session"));
                sender.sendMessage(colorize("&7│ &f/root disable-log  &7- Disable logging"));
                sender.sendMessage(colorize("&7│ &f/root enable-log   &7- Enable logging"));
                sender.sendMessage(colorize("&7│ &f/root startup <cmd> &7- Add startup command"));
                sender.sendMessage(colorize("&7│ &f/root startup list &7- List startup commands"));
                sender.sendMessage(colorize("&7│ &f/root startup clear &7- Clear startup commands"));
                sender.sendMessage(colorize("&c└─────────────────────────┘"));
                return true;
            }

            // Handle startup commands
            if (args[0].equalsIgnoreCase("startup")) {
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

                // Add startup command
                String startupCmd = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                startupCommands.add(startupCmd);
                config.set("startup-commands", startupCommands);
                saveConfigFile();
                sender.sendMessage(colorize("&a[+] Added startup command: &f" + startupCmd));
                sender.sendMessage(colorize("&7    Total startup commands: &e" + startupCommands.size()));
                return true;
            }

            // Single word commands
            if (args.length == 1) {
                String arg = args[0].toLowerCase();

                if (arg.equals("log")) {
                    if (lastLogs.isEmpty()) {
                        sender.sendMessage(colorize("&7[!] No logs available."));
                    } else {
                        sender.sendMessage(colorize("&9┌─ &lLast Command Logs &9─┐"));

                        // Show current working directory
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

                if (arg.equals("pwd")) {
                    String currentDir = userWorkingDir.getOrDefault(senderKey, System.getProperty("user.dir"));
                    sender.sendMessage(colorize("&e┌─ Current Directory ─┐"));
                    sender.sendMessage(colorize("&6│ " + currentDir));
                    sender.sendMessage(colorize("&e└─────────────────────┘"));
                    return true;
                }

                if (arg.equals("reset")) {
                    userWorkingDir.remove(senderKey);
                    userEnvironment.remove(senderKey);
                    sender.sendMessage(colorize("&a[+] Session reset! Back to root directory."));
                    return true;
                }

                if (arg.equals("disable-log")) {
                    consoleLogging = false;
                    config.set("console-logging", false);
                    saveConfigFile();
                    sender.sendMessage(colorize("&a[+] Console logging &cdisabled&a and saved to config!"));
                    sender.sendMessage(colorize("&7    Commands will run silently in background."));
                    return true;
                }

                if (arg.equals("enable-log")) {
                    consoleLogging = true;
                    config.set("console-logging", true);
                    saveConfigFile();
                    sender.sendMessage(colorize("&a[+] Console logging &aenabled&a and saved to config!"));
                    sender.sendMessage(colorize("&7    Command output will be shown in console."));
                    return true;
                }
            }

            String fullCommand = String.join(" ", args);
            sender.sendMessage(colorize("&e[*] &7Executing: &f" + fullCommand));
            sender.sendMessage(colorize("&8    Status: &7Processing..."));

            // Execute command async
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

    private void executeCommand(CommandSender sender, String command, String senderKey) {
        lastLogs.clear();

        try {
            // Basic setup
            ensureBasicSetup();

            // Get current working directory for this user
            String currentDir = userWorkingDir.getOrDefault(senderKey, System.getProperty("user.dir"));

            // Handle cd command specially
            if (command.trim().startsWith("cd ")) {
                handleCdCommand(command, senderKey, currentDir, sender);
                return;
            }

            if (consoleLogging) {
                getLogger().info(colorize("&e[*] Running command: &f" + command + " &7in directory: &f" + currentDir));
            }

            // Create process with preserved working directory
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.redirectErrorStream(true);
            pb.directory(new File(currentDir));

            // Preserve environment variables if available
            if (userEnvironment.containsKey(senderKey)) {
                ProcessBuilder savedEnv = userEnvironment.get(senderKey);
                pb.environment().putAll(savedEnv.environment());
            }

            Process process = pb.start();

            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            lastLogs.add(">>> [" + currentDir + "] $ " + command);
            lastLogs.add("");

            boolean hasOutput = false;
            while ((line = reader.readLine()) != null) {
                hasOutput = true;
                // Clean ANSI codes
                String cleanLine = line.replaceAll("\\x1B\\[[0-9;]*[mGKHF]", "");
                lastLogs.add(cleanLine);

                // Conditional console logging
                if (consoleLogging) {
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

            // Update environment
            userEnvironment.put(senderKey, pb);

            // Notify completion (only if sender exists - not for startup commands)
            if (sender != null) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (exitCode == 0) {
                            sender.sendMessage(colorize("&a[+] Command completed successfully!"));
                            sender.sendMessage(colorize("&7    Use &f/root log &7to view output."));
                        } else {
                            sender.sendMessage(colorize("&c[!] Command completed with errors &7(code: &c" + exitCode + "&7)"));
                            sender.sendMessage(colorize("&7    Use &f/root log &7to view details."));
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
            // Parse cd command
            String targetDir = command.substring(3).trim();
            if (targetDir.isEmpty()) {
                targetDir = System.getProperty("user.dir"); // Go to root
            }

            // Resolve relative paths
            File newDir;
            if (targetDir.startsWith("/")) {
                newDir = new File(targetDir); // Absolute path
            } else {
                newDir = new File(currentDir, targetDir); // Relative path
            }

            // Normalize path
            String newPath = newDir.getCanonicalPath();

            // Check if directory exists
            if (newDir.exists() && newDir.isDirectory()) {
                userWorkingDir.put(senderKey, newPath);

                lastLogs.clear();
                lastLogs.add(">>> [" + currentDir + "] $ " + command);
                lastLogs.add("");
                lastLogs.add("Changed directory to: " + newPath);
                lastLogs.add("");
                lastLogs.add(">>> Exit Code: 0");

                if (sender != null) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(colorize("&a[+] Changed to directory: &f" + newPath));
                        }
                    }.runTask(this);
                }

                if (consoleLogging) {
                    getLogger().info(colorize("&a[+] Changed to directory: &f" + newPath));
                }

            } else {
                lastLogs.clear();
                lastLogs.add(">>> [" + currentDir + "] $ " + command);
                lastLogs.add("");
                lastLogs.add("bash: cd: " + targetDir + ": No such file or directory");
                lastLogs.add("");
                lastLogs.add(">>> Exit Code: 1");

                if (sender != null) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(colorize("&c[!] Directory not found: &f" ));
                        }
                    }.runTask(this);
                }

                if (consoleLogging) {
                    getLogger().info(colorize("&c[!] Directory not found: &f" + targetDir));
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

        // Log full stack trace to console
        getLogger().severe("Error executing command '" + command + "': " + e.getMessage());
        e.printStackTrace();

        if (sender != null) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    sender.sendMessage(colorize("&c[!] Command failed! Use &f/root log &cto view error details."));
                }
            }.runTask(this);
        }
    }

    private void ensureBasicSetup() {
        if (isInitialized || isInitializing) {
            return;
        }

        isInitializing = true;

        try {
            getLogger().info(colorize("&e[*] Basic setup check..."));

            // Only do essential setup if needed
            File freerootDir = new File("freeroot");
            if (!freerootDir.exists()) {
                getLogger().info(colorize("&e[*] Cloning freeroot repository..."));
                Process cloneProcess = new ProcessBuilder("git", "clone", "https://github.com/foxytouxxx/freeroot.git")
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                cloneProcess.waitFor();
            }

            isInitialized = true;
            getLogger().info(colorize("&a[+] Basic setup completed!"));

        } catch (Exception e) {
            getLogger().warning(colorize("&c[-] Basic setup failed: " + e.getMessage()));
        } finally {
            isInitializing = false;
        }
    }
}