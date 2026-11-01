package it.simplemal.firehome;

import android.app.ActivityManager;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.WindowManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** What the device says about itself, readable by any app. */
final class DeviceInfo {
    /** Speed and address of the Wi-Fi in use. */
    @SuppressWarnings("deprecation")
    static String wifiLine(Context context) {
        android.net.wifi.WifiInfo info = context.getSystemService(android.net.wifi.WifiManager.class).getConnectionInfo();
        return info.getLinkSpeed() + " Mbit/s · " + android.text.format.Formatter.formatIpAddress(info.getIpAddress());
    }

    private static final long GB = 1024L * 1024 * 1024;

    private static final String DEVICE_NAME = "device_name";
    private static final String BLUETOOTH_NAME = "bluetooth_name";

    private static volatile String fireOs;

    private DeviceInfo() {}

    /** Reading the version starts a process: this does it once, ahead of the page that shows it. */
    static void prepare() { Background.run(DeviceInfo::fireOs); }

    static String name(Context context) {
        String name = Settings.Global.getString(context.getContentResolver(), DEVICE_NAME);
        return name != null ? name : Build.MODEL;
    }

    /** Fire OS keeps the name in two places, and Bluetooth holds a copy of its own. */
    @SuppressLint("MissingPermission")
    static boolean setName(Context context, String name) {
        try {
            Settings.Global.putString(context.getContentResolver(), DEVICE_NAME, name);
            Settings.Secure.putString(context.getContentResolver(), BLUETOOTH_NAME, name);
        } catch (SecurityException e) {
            return false;
        }
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        if (bluetooth != null) bluetooth.setName(name);
        return true;
    }

    static List<String[]> system(Context context) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{context.getString(R.string.device_name), name(context)});
        rows.add(new String[]{context.getString(R.string.device_model), Build.MODEL});
        rows.add(new String[]{context.getString(R.string.device_system), fireOs()});
        rows.add(new String[]{"Android", Build.VERSION.RELEASE});
        rows.add(new String[]{context.getString(R.string.device_screen), screen(context)});
        rows.add(new String[]{context.getString(R.string.device_memory), memory(context)});
        rows.add(new String[]{context.getString(R.string.device_storage), storage(context)});
        rows.add(new String[]{context.getString(R.string.device_on_for), uptime(context)});
        return rows;
    }

    static List<String[]> network(Context context) {
        List<String[]> rows = new ArrayList<>();
        WifiManager wifi = context.getSystemService(WifiManager.class);
        WifiInfo info = wifi.getConnectionInfo();
        int address = info.getIpAddress();
        rows.add(new String[]{context.getString(R.string.device_ip), address == 0 ? context.getString(R.string.panel_not_connected)
            : String.format(Locale.ROOT, "%d.%d.%d.%d", address & 255, address >> 8 & 255, address >> 16 & 255, address >> 24 & 255)});
        rows.add(new String[]{context.getString(R.string.device_signal), address == 0 ? "—" : info.getRssi() + " dBm"});
        rows.add(new String[]{context.getString(R.string.device_speed), address == 0 ? "—" : info.getLinkSpeed() + " Mbps"});
        return rows;
    }

    /** Fire OS keeps its version in a system property, without the build number it stays short. */
    private static String fireOs() {
        if (fireOs == null) fireOs = readFireOs();
        return fireOs;
    }

    private static String readFireOs() {
        try {
            Process process = new ProcessBuilder("getprop", "ro.build.version.name").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String name = reader.readLine();
                if (name != null && !name.isEmpty()) return name.split(" \\(")[0];
            } finally {
                process.destroy();
            }
        } catch (IOException e) {
            return Build.DISPLAY;
        }
        return Build.DISPLAY;
    }

    private static String screen(Context context) {
        Display display = context.getSystemService(WindowManager.class).getDefaultDisplay();
        Display.Mode mode = display.getMode();
        return String.format(Locale.ROOT, "%d×%d, %.0f Hz", mode.getPhysicalWidth(), mode.getPhysicalHeight(),
            mode.getRefreshRate());
    }

    private static String memory(Context context) {
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        context.getSystemService(ActivityManager.class).getMemoryInfo(memory);
        return context.getString(R.string.device_free_of, (double) memory.availMem / GB, (double) memory.totalMem / GB);
    }

    private static String storage(Context context) {
        StatFs disk = new StatFs(context.getFilesDir().getPath());
        return context.getString(R.string.device_free_of, (double) disk.getAvailableBytes() / GB,
            (double) disk.getTotalBytes() / GB);
    }

    private static String uptime(Context context) {
        long minutes = SystemClock.elapsedRealtime() / Fire.MINUTE_MS;
        return minutes >= 60 ? context.getString(R.string.device_uptime_hours, minutes / 60, minutes % 60)
            : context.getString(R.string.device_uptime_minutes, minutes);
    }
}
