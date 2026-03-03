package dev.eministar.starclans.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * Utility for beautiful console logging and error handling.
 */
public final class LoggerUtil {

    private static JavaPlugin plugin;
    private static File logFolder;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    // ANSI Colors for Console
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String BOLD = "\u001B[1m";

    private static final String PREFIX = CYAN + BOLD + "StarClans " + RESET + "» ";

    public static void init(JavaPlugin javaPlugin) {
        plugin = javaPlugin;
        logFolder = new File(plugin.getDataFolder(), "logs");
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }
    }

    public static void info(String message) {
        log(BLUE + "[INFO] " + RESET + message);
    }

    public static void success(String message) {
        log(GREEN + "[SUCCESS] " + RESET + message);
    }

    public static void warn(String message) {
        log(YELLOW + "[WARN] " + RESET + message);
    }

    /**
     * Logs a user-friendly error to console and a detailed stacktrace to a file.
     *
     * @param userMessage A readable message for non-developers.
     * @param throwable   The actual error to log in the background.
     */
    public static void error(String userMessage, Throwable throwable) {
        String time = LocalDateTime.now().format(TIME_FORMAT);
        String fileName = "error_" + LocalDateTime.now().format(DATE_FORMAT) + ".log";
        File logFile = new File(logFolder, fileName);

        // Console Output (Beautiful & Clear)
        Bukkit.getConsoleSender().sendMessage(PREFIX + RED + BOLD + "[ERROR] " + RESET + RED + userMessage);
        Bukkit.getConsoleSender().sendMessage(PREFIX + RED + "Details wurden in der Datei " + WHITE + BOLD + "logs/" + fileName + RESET + RED + " gespeichert.");

        // File Output (Detailed Stacktrace)
        try (FileWriter fw = new FileWriter(logFile);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println("--- StarClans Error Log ---");
            pw.println("Time: " + time);
            pw.println("Message: " + userMessage);
            pw.println("Exception: " + throwable.getClass().getName());
            pw.println("Stacktrace:");
            throwable.printStackTrace(pw);
            pw.println("--- End of Log ---");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not write to log file!", e);
        }
    }

    /**
     * Logs a simple error message without a stacktrace (for known issues).
     */
    public static void error(String message) {
        Bukkit.getConsoleSender().sendMessage(PREFIX + RED + BOLD + "[ERROR] " + RESET + RED + message);
    }

    private static void log(String message) {
        Bukkit.getConsoleSender().sendMessage(PREFIX + message);
    }

    /**
     * Translates Bukkit ChatColor to ANSI for console if needed,
     * but here we use direct ANSI for better control in console.
     */
    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
