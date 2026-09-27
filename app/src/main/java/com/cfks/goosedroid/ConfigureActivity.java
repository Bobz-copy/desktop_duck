/**
 * @Author
 * @AIDE AIDE+
 */
package com.cfks.goosedroid;

import android.content.*;
import android.util.Log;

import java.io.*;
import java.util.*;

public class ConfigureActivity {
    private static final String TAG = "ConfigureActivity";
    private static final Object FILE_LOCK = new Object();
    private final Context context;
    private Properties properties;

    public ConfigureActivity(Context context) {
        super();
        this.context = context;
        this.properties = new Properties();
    }

    /**
     * Save properties to file using try-with-resources for proper cleanup.
     * @param filename Path to the file
     * @param properties Properties to save
     * @throws IOException if file cannot be written
     */
    public void saveFiletoSD(String filename, Properties properties) throws IOException {
        // Validate inputs
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }
        if (properties == null) {
            throw new IllegalArgumentException("Properties cannot be null");
        }

        synchronized (FILE_LOCK) {
            // Merge: las claves que este llamador no conoce se conservan.
            Properties merged = new Properties();
            File target = new File(filename);
            if (target.exists()) {
                try (InputStream in = new BufferedInputStream(new FileInputStream(target))) {
                    merged.load(in);
                } catch (IOException | IllegalArgumentException e) {
                    Log.w(TAG, "Config existente ilegible; se reescribe: " + filename, e);
                }
            }
            for (String key : properties.stringPropertyNames()) {
                merged.setProperty(key, properties.getProperty(key));
            }

            // Si el proceso muere a mitad, el archivo original queda intacto
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            merged.store(buffer, null);
            try {
                AtomicFiles.write(target, buffer.toByteArray());
            } catch (IOException e) {
                Log.e(TAG, "Error saving config file: " + filename, e);
                throw e;
            }
        }
    }

    /**
     * Read properties from file using try-with-resources for proper cleanup.
     * @param filename Path to the file
     * @throws IOException if file cannot be read
     */
    public void readFromSD(String filename) throws IOException {
        // Validate input
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }

        // Initialize properties
        properties = new Properties();

        // Validate file exists and is readable
        File file = new File(filename);
        if (!file.exists()) {
            throw new FileNotFoundException("Config file not found: " + filename);
        }
        if (!file.canRead()) {
            throw new IOException("Config file is not readable: " + filename);
        }

        // Validate file size (prevent loading huge files)
        long maxSize = 1024 * 1024; // 1MB max
        if (file.length() > maxSize) {
            throw new IOException("Config file too large: " + file.length() + " bytes (max: " + maxSize + ")");
        }

        // Use try-with-resources to ensure stream is closed
        try (FileInputStream fileInputStream = new FileInputStream(file);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {

            properties.load(bufferedInputStream);

        } catch (IOException e) {
            Log.e(TAG, "Error reading config file: " + filename, e);
            throw e; // Re-throw to let caller handle
        }
    }

    /**
     * Get a property value by key.
     * @param key The property key
     * @return The property value, or null if not found
     */
    public String getIniKey(String key) {
        if (properties == null) {
            Log.w(TAG, "Properties not loaded - call readFromSD() first");
            return null;
        }
        if (key == null) {
            return null;
        }
        if (!properties.containsKey(key)) {
            return null;
        }
        return String.valueOf(properties.get(key));
    }

    /**
     * Check if a property exists.
     * @param key The property key
     * @return true if the property exists
     */
    public boolean hasKey(String key) {
        return properties != null && key != null && properties.containsKey(key);
    }

    /**
     * Get property with default value if not found.
     * @param key The property key
     * @param defaultValue Value to return if key not found
     * @return The property value or default
     */
    public String getIniKey(String key, String defaultValue) {
        String value = getIniKey(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Set a property value.
     * @param key The property key
     * @param value The property value
     */
    public void setIniKey(String key, String value) {
        if (properties == null) {
            properties = new Properties();
        }
        if (key != null && value != null) {
            properties.setProperty(key, value);
        }
    }

    /**
     * Get the properties object.
     * @return The properties
     */
    public Properties getProperties() {
        return properties;
    }
}
