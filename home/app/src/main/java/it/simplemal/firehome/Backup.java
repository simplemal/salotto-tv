package it.simplemal.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Saves Salotto's choices to one JSON file. The media folder is used because the Stick has no
 * document picker, and adb can reach it: `/sdcard/Android/media/<package>/`.
 */
final class Backup {
    private static final String[] STORES = {"home", "look", "weather"};
    private static final String NAME = "salotto-backup.json";

    private Backup() {}

    private static File file(Context context) {
        return Updater.shared(context, NAME);
    }

    static boolean exists(Context context) { return file(context).isFile(); }

    static void save(Context context) throws IOException, JSONException {
        JSONObject all = new JSONObject();
        for (String store : STORES) {
            JSONObject values = new JSONObject();
            for (Map.Entry<String, ?> entry : context.getSharedPreferences(store, Context.MODE_PRIVATE).getAll().entrySet()) {
                values.put(entry.getKey(), new JSONObject()
                    .put("type", entry.getValue().getClass().getSimpleName()).put("value", entry.getValue()));
            }
            all.put(store, values);
        }
        file(context).getParentFile().mkdirs();
        Files.write(file(context).toPath(), all.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    static void restore(Context context) throws IOException, JSONException {
        String text = new String(Files.readAllBytes(file(context).toPath()), StandardCharsets.UTF_8);
        JSONObject all = new JSONObject(text);
        for (String store : STORES) {
            JSONObject values = all.optJSONObject(store);
            if (values == null) continue;
            SharedPreferences.Editor editor = context.getSharedPreferences(store, Context.MODE_PRIVATE).edit().clear();
            for (Iterator<String> keys = values.keys(); keys.hasNext(); ) {
                String key = keys.next();
                JSONObject item = values.getJSONObject(key);
                switch (item.getString("type")) {
                    case "Boolean": editor.putBoolean(key, item.getBoolean("value")); break;
                    case "Integer": editor.putInt(key, item.getInt("value")); break;
                    case "Long": editor.putLong(key, item.getLong("value")); break;
                    case "Float": editor.putFloat(key, (float) item.getDouble("value")); break;
                    default: editor.putString(key, item.getString("value"));
                }
            }
            editor.apply();
        }
    }
}
