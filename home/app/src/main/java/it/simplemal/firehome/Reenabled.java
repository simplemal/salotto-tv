package it.simplemal.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What Fire OS turned back on, usually with an update. Reading this needs no adb, so nothing is
 * asked on the TV until the person chooses to turn it off again.
 */
final class Reenabled {
    private static final String[] LISTS = {"disattiva-pubblicita.txt", "disattiva-alexa.txt"};
    private static final int[] GROUPS = {R.string.reenabled_group_ads, R.string.reenabled_group_alexa};
    private static final String FILE = "reenabled";
    private static final String LEFT_ON = "left_on";

    private Reenabled() {}

    /** A list mostly on was never turned off by this person: it stays out of this. */
    static ArrayList<String> find(Context context) {
        Set<String> leftOn = prefs(context).getStringSet(LEFT_ON, new HashSet<>());
        ArrayList<String> found = new ArrayList<>();
        for (String list : LISTS) {
            List<String> on = new ArrayList<>();
            int total = 0;
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(context.getAssets().open(list)))) {
                for (String line; (line = lines.readLine()) != null; ) {
                    String name = line.trim();
                    if (name.isEmpty() || name.startsWith("#") || !present(context, name)) continue;
                    total++;
                    if (isOn(context, name)) on.add(name);
                }
            } catch (IOException e) {
                continue;
            }
            if (on.size() * 2 > total) continue;
            for (String name : on) {
                if (!leftOn.contains(name)) found.add(name);
            }
        }
        if (present(context, Fire.QUICK_MENU) && isOn(context, Fire.QUICK_MENU) && !leftOn.contains(Fire.QUICK_MENU)) {
            found.add(Fire.QUICK_MENU);
        }
        return found;
    }

    /** They are not asked about again; anything else turned on later is. */
    static void leaveOn(Context context, List<String> names) {
        Set<String> leftOn = new HashSet<>(prefs(context).getStringSet(LEFT_ON, new HashSet<>()));
        leftOn.addAll(names);
        prefs(context).edit().putStringSet(LEFT_ON, leftOn).apply();
    }

    /** What a person can read. Many parts of Fire OS have only a code name: those are just counted. */
    static TreeSet<String> labels(Context context, List<String> names) {
        TreeSet<String> labels = new TreeSet<>();
        for (String name : names) {
            String label = label(context, name);
            if (label != null) labels.add(label);
        }
        return labels;
    }

    /** For the page that can only tell: what each list is about, in words anyone knows. */
    static TreeSet<String> groups(Context context, List<String> names) {
        TreeSet<String> groups = new TreeSet<>();
        for (int index = 0; index < LISTS.length; index++) {
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(context.getAssets().open(LISTS[index])))) {
                for (String line; (line = lines.readLine()) != null; ) {
                    if (names.contains(line.trim())) groups.add(context.getString(GROUPS[index]));
                }
            } catch (IOException e) {
                continue;
            }
        }
        return groups;
    }

    private static String label(Context context, String name) {
        if (Fire.QUICK_MENU.equals(name)) return context.getString(R.string.reenabled_quick_menu);
        PackageManager manager = context.getPackageManager();
        try {
            String label = manager.getApplicationLabel(manager.getApplicationInfo(name, 0)).toString();
            return label.contains(".") ? null : label;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static boolean present(Context context, String name) {
        try {
            context.getPackageManager().getApplicationEnabledSetting(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isOn(Context context, String name) {
        int state = context.getPackageManager().getApplicationEnabledSetting(name);
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
