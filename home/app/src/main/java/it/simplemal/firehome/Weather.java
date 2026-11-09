package it.simplemal.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.SystemClock;
import android.text.TextUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Current weather from Open-Meteo: no account, and only the chosen place's coordinates are sent. */
final class Weather {
    static final class Place {
        final String name;
        final String detail;
        final double latitude;
        final double longitude;

        Place(String name, String detail, double latitude, double longitude) {
            this.name = name;
            this.detail = detail;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private static final String FILE = "weather";
    /** In the shared folder, holding "phase/condition": shows that scene, to try all 120. */
    private static final String SCENE_TEST = "meteo-prova";
    private static final String PICTURES = "meteo-illustrato/";
    private static final String PHOTOS = "meteo-foto/";
    private static final String AERIAL = "meteo-aereo/";
    /** In the names of the aerial pictures: -32c- or -minus-7c-. */
    private static final Pattern PICTURE_TEMPERATURE = Pattern.compile("-(minus-)?(\\d+)c-");
    private static final int PLACES = 12;
    private static final String PICTURE_TABLE = "tabella.properties";
    /** What tells an illustration from a scene to draw. */
    static final String PICTURE_FILE = ".webp";
    /** Sunrise and sunset come in seconds. */
    private static final long MINUTE_S = 60;
    private static final long REFRESH_MS = 30 * Fire.MINUTE_MS;
    private static final long KEEP_MS = 6 * 60 * Fire.MINUTE_MS;
    private static final long RETRY_MS = 5 * Fire.MINUTE_MS;
    private static final int TIMEOUT_MS = 10000;
    private static final AtomicBoolean REFRESHING = new AtomicBoolean();
    private static final Map<String, Properties> PICTURE_TABLES = new java.util.concurrent.ConcurrentHashMap<>();

    private static volatile long failedAt;

    private Weather() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Each Home its own, like the time and the date: see Look.own. */
    static boolean enabled(Context context) {
        SharedPreferences saved = prefs(context);
        return saved.getBoolean(Look.own(context, "enabled"), saved.getBoolean("enabled", true));
    }
    static void setEnabled(Context context, boolean value) { prefs(context).edit().putBoolean(Look.own(context, "enabled"), value).apply(); }
    static String place(Context context) { return prefs(context).getString("place", null); }
    /** True once, the first time the Home finds no place: it is asked for only that time. */
    static boolean fahrenheit(Context context) { return prefs(context).getBoolean("fahrenheit", false); }
    static void setFahrenheit(Context context, boolean value) {
        failedAt = 0;
        prefs(context).edit().putBoolean("fahrenheit", value).remove("updated").remove("tomorrow_code").apply();
    }

    static void setPlace(Context context, Place place) {
        failedAt = 0;
        prefs(context).edit().putString("place", place.name)
            .putString("latitude", String.valueOf(place.latitude))
            .putString("longitude", String.valueOf(place.longitude))
            .remove("updated").remove("tomorrow_code").apply();
    }

    /** "phase/condition" of the scene for now, or null without a place or a first reading. */
    static String scene(Context context) {
        if (testing(context)) return Updater.read(Updater.shared(context, SCENE_TEST));
        SharedPreferences saved = prefs(context);
        if (!active(context) || !saved.contains("code")) return null;
        return phase(saved) + "/" + condition(saved.getInt("code", 0));
    }

    static boolean testing(Context context) { return Updater.shared(context, SCENE_TEST).exists(); }

    /**
     * The file for this hour and this weather, from the manifest's table, as
     * WEATHER_RASTER_USAGE.md asks. A code it does not know keeps the last valid picture, and so
     * does a moment without data. With meteo-prova, phase/condition or phase/code.
     */
    static String picture(Context context) {
        if (Look.abstractPictures(context)) return Look.abstractPicture(context);
        SharedPreferences saved = prefs(context);
        boolean photos = Look.weatherPhotos(context);
        boolean aerial = Look.weatherAerial(context);
        String folder = aerial ? AERIAL : photos ? PHOTOS : PICTURES;
        String last = aerial ? "aerial" : photos ? "photo" : "picture";
        String key = testing(context) ? scene(context)
            : active(context) && saved.contains("code") ? phase(saved) + "/" + saved.getInt("code", 0) : null;
        String path = key == null ? null : file(context, folder, key);
        if (path == null) return saved.getString(last, byClock(context, folder));
        if (!testing(context) && !path.equals(saved.getString(last, null))) {
            saved.edit().putString(last, path).apply();
        }
        return path;
    }

    static boolean weatherPack(String pack) {
        return (pack + "/").equals(PICTURES) || (pack + "/").equals(PHOTOS) || (pack + "/").equals(AERIAL);
    }

    static String tableOf(String pack) { return pack + "/" + PICTURE_TABLE; }

    /**
     * The picture of `pack` for this hour and this weather, read from its table alone: the one
     * Salotto downloads before the rest of the collection, so the Home opens on it.
     */
    static String pictureIn(Context context, String pack) {
        String folder = pack + "/";
        SharedPreferences saved = prefs(context);
        String key = active(context) && saved.contains("code") ? phase(saved) + "/" + saved.getInt("code", 0) : null;
        String path = key == null ? null : file(context, folder, key);
        return path != null ? path : byClock(context, folder);
    }

    /** A weather with two pictures takes them in turn by the day, so it does not change at every look. */
    private static String file(Context context, String folder, String key) {
        String files = pictures(context, folder).getProperty(key);
        if (files == null) return null;
        String[] choices = TextUtils.split(files, ",");
        String nearest = nearestTemperature(prefs(context), choices);
        return folder + (nearest != null ? nearest : choices[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % choices.length]);
    }

    /** The picture made for the temperature closest to the real one. Null if these were not made for one. */
    private static String nearestTemperature(SharedPreferences saved, String[] files) {
        float now = saved.getFloat("temperature", 0);
        if (saved.getBoolean("fahrenheit", false)) now = (now - 32) * 5 / 9;
        String nearest = null;
        float closest = Float.MAX_VALUE;
        for (String name : files) {
            Matcher found = PICTURE_TEMPERATURE.matcher(name);
            if (!found.find()) return null;
            int degrees = Integer.parseInt(found.group(2));
            float distance = Math.abs(now - (found.group(1) != null ? -degrees : degrees));
            if (distance < closest) {
                closest = distance;
                nearest = name;
            }
        }
        return nearest;
    }

    /** No place chosen yet: a clear sky for the hour, so the first start is not an empty screen. */
    private static String byClock(Context context, String folder) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String phase = hour < 6 || hour >= 22 ? "night" : hour < 8 ? "dawn" : hour < 15 ? "day"
            : hour < 18 ? "afternoon" : hour < 20 ? "sunset" : "evening";
        return file(context, folder, phase + "/0");
    }

    /** The table of a collection still coming, read by Salotto where it just put it, checked. */
    static void knowTable(String folder, Properties table) { PICTURE_TABLES.put(folder, table); }

    static Properties pictures(Context context, String folder) {
        Properties known = PICTURE_TABLES.get(folder);
        if (known != null) return known;
        Properties table = new Properties();
        try (InputStream input = Packs.open(context, folder + PICTURE_TABLE)) {
            table.load(input);
        } catch (IOException e) {
            return table;
        }
        PICTURE_TABLES.put(folder, table);
        return table;
    }

    /** The thresholds of WEATHER_BACKGROUNDS_USAGE.md, in its order. */
    private static String phase(SharedPreferences saved) {
        return phase(System.currentTimeMillis() / 1000, saved.getLong("sunrise", 0), saved.getLong("sunset", 0),
            saved.getLong("next_sunrise", 0), saved.getBoolean("is_day", true));
    }

    static String phase(long now, long sunrise, long sunset, long next, boolean isDay) {
        if (sunrise == 0 || sunset <= sunrise) return isDay ? "day" : "night";
        if (Math.abs(now - sunrise) <= 45 * MINUTE_S || Math.abs(now - next) <= 45 * MINUTE_S) return "dawn";
        if (now >= sunset - 60 * MINUTE_S && now <= sunset + 20 * MINUTE_S) return "sunset";
        if (now > sunset + 20 * MINUTE_S && now <= sunset + 90 * MINUTE_S) return "evening";
        if (now > sunrise && now < sunset) return (now - sunrise) <= 0.6 * (sunset - sunrise) ? "day" : "afternoon";
        return "night";
    }

    /** The table of WEATHER_BACKGROUND_INDEX.md. An unknown code keeps the sky, without phenomena. */
    private static String condition(int code) {
        switch (code) {
            case 1: return "mostly_clear";
            case 2: return "partly_cloudy";
            case 3: return "overcast";
            case 45: return "fog";
            case 48: return "freezing_fog";
            case 51: case 56: return "light_drizzle";
            case 53: case 57: return "moderate_drizzle";
            case 55: return "heavy_drizzle";
            case 61: case 66: return "light_rain";
            case 63: return "moderate_rain";
            case 65: case 67: return "heavy_rain";
            case 71: case 77: return "light_snow";
            case 73: return "moderate_snow";
            case 75: return "heavy_snow";
            case 80: return "light_rain_showers";
            case 81: case 82: return "heavy_rain_showers";
            case 85: return "light_snow_showers";
            case 86: return "heavy_snow_showers";
            case 95: case 96: case 97: case 99: return "thunderstorm";
            default: return "clear_sky";
        }
    }

    /**
     * Read from the network with a place, when the Home writes it or a weather backdrop follows it.
     * With the weather off the Home, its backdrop gave way to another one and the place was asked
     * for again at every press (2026-11-09); off, with any other backdrop, nothing is asked.
     * On the glass and the wheel Homes the switch is Large: off, today's symbol stays small next to
     * the buttons, so it is read all the same (2026-11-09).
     */
    static boolean active(Context context) {
        return place(context) != null && (enabled(context) || Look.weatherChosen(context) || !Look.clean(context));
    }

    /** Never two readings at once, and offline a failure waits a while before the next try. */
    static boolean due(Context context) {
        SharedPreferences saved = prefs(context);
        if (REFRESHING.get() || failedAt != 0 && SystemClock.elapsedRealtime() - failedAt < RETRY_MS) return false;
        return active(context) && (!saved.contains("sunset")
            || System.currentTimeMillis() - saved.getLong("updated", 0) > REFRESH_MS);
    }

    private static boolean recent(Context context) {
        return active(context)
            && System.currentTimeMillis() - prefs(context).getLong("updated", 0) <= KEEP_MS;
    }

    /** The current weather's symbol alone, or null when there is nothing recent to show. */
    static String symbol(Context context) {
        return recent(context) ? symbol(prefs(context).getInt("code", 0)) : null;
    }

    /** The line for the Home, or null when there is nothing recent to show. */
    static String line(Context context) {
        SharedPreferences saved = prefs(context);
        if (!recent(context)) return null;
        int code = saved.getInt("code", 0);
        return String.format(Locale.getDefault(), "%s  %d°  %s · %s", symbol(code),
            Math.round(saved.getFloat("temperature", 0)), description(context, code), place(context));
    }

    /** Tomorrow's forecast for the Home, or null when there is nothing recent to show. */
    static String tomorrow(Context context) {
        SharedPreferences saved = prefs(context);
        if (!recent(context) || !saved.contains("tomorrow_code")) return null;
        int code = saved.getInt("tomorrow_code", 0);
        return context.getString(R.string.weather_tomorrow, symbol(code),
            Math.round(saved.getFloat("tomorrow_max", 0)), Math.round(saved.getFloat("tomorrow_min", 0)),
            description(context, code));
    }

    /** One day of the forecast on the circular Home. */
    static final class Day {
        final String name;
        final String symbol;
        final int highest;
        final int lowest;

        Day(String name, String symbol, int highest, int lowest) {
            this.name = name;
            this.symbol = symbol;
            this.highest = highest;
            this.lowest = lowest;
        }
    }

    /** Code, highest and lowest of each day, today first. */
    private static String days(JSONObject daily) throws JSONException {
        StringBuilder days = new StringBuilder();
        JSONArray codes = daily.getJSONArray("weather_code");
        for (int index = 0; index < codes.length(); index++) {
            if (index > 0) days.append(';');
            days.append(codes.getInt(index)).append(',')
                .append(Math.round(daily.getJSONArray("temperature_2m_max").getDouble(index))).append(',')
                .append(Math.round(daily.getJSONArray("temperature_2m_min").getDouble(index)));
        }
        return days.toString();
    }

    /** Today and the next days, or an empty list when there is nothing recent to show. */
    static List<Day> days(Context context) {
        List<Day> days = new ArrayList<>();
        String saved = prefs(context).getString("days", null);
        if (!recent(context) || saved == null) return days;
        Calendar day = Calendar.getInstance();
        SimpleDateFormat weekday = new SimpleDateFormat("EEE", Locale.getDefault());
        for (String one : saved.split(";")) {
            String[] parts = one.split(",");
            if (parts.length != 3) return new ArrayList<>();
            String name = days.isEmpty() ? context.getString(R.string.weather_today) : weekday.format(day.getTime());
            name = name.replace(".", "");
            name = name.substring(0, 1).toUpperCase(Locale.getDefault()) + name.substring(1);
            days.add(new Day(name, symbol(Integer.parseInt(parts[0])), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        return days;
    }

    /** Blocking: call it away from the main thread. A failure keeps the last reading. */
    static void refresh(Context context) throws IOException, JSONException {
        if (!REFRESHING.compareAndSet(false, true)) return;
        // A failure until proved otherwise, whichever way this ends.
        failedAt = SystemClock.elapsedRealtime();
        try {
            SharedPreferences saved = prefs(context);
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + saved.getString("latitude", "")
                + "&longitude=" + saved.getString("longitude", "") + "&current=temperature_2m,weather_code,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset&forecast_days=4"
                + "&timezone=auto&timeformat=unixtime"
                + (saved.getBoolean("fahrenheit", false) ? "&temperature_unit=fahrenheit" : "");
            JSONObject forecast = new JSONObject(get(url));
            JSONObject current = forecast.getJSONObject("current");
            JSONObject daily = forecast.getJSONObject("daily");
            saved.edit().putFloat("temperature", (float) current.getDouble("temperature_2m"))
                .putInt("code", current.getInt("weather_code"))
                .putBoolean("is_day", current.getInt("is_day") == 1)
                .putLong("sunrise", daily.getJSONArray("sunrise").getLong(0))
                .putLong("sunset", daily.getJSONArray("sunset").getLong(0))
                .putLong("next_sunrise", daily.getJSONArray("sunrise").getLong(1))
                .putInt("tomorrow_code", daily.getJSONArray("weather_code").getInt(1))
                .putFloat("tomorrow_max", (float) daily.getJSONArray("temperature_2m_max").getDouble(1))
                .putFloat("tomorrow_min", (float) daily.getJSONArray("temperature_2m_min").getDouble(1))
                .putString("days", days(daily))
                .putLong("updated", System.currentTimeMillis()).apply();
            failedAt = 0;
        } finally {
            REFRESHING.set(false);
        }
    }

    /** Blocking: call it away from the main thread. */
    /**
     * The service ranks the whole world by size, so a town of ours can come 91st for the first letters
     * of its name. It is asked for everything it has, and the places of the Stick's own country go first.
     */
    static List<Place> search(Context context, String name) throws IOException, JSONException {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=100&language=" + Language.code(context) + "&format=json&name="
            + URLEncoder.encode(name, StandardCharsets.UTF_8.name());
        JSONArray results = new JSONObject(get(url)).optJSONArray("results");
        List<Place> places = new ArrayList<>();
        if (results == null) return places;
        // The Stick's own: Salotto's language says nothing about where it is.
        String country = Resources.getSystem().getConfiguration().getLocales().get(0).getCountry();
        int ours = 0;
        for (int index = 0; index < results.length(); index++) {
            JSONObject found = results.getJSONObject(index);
            List<String> detail = new ArrayList<>();
            if (found.has("admin1")) detail.add(found.getString("admin1"));
            if (found.has("country")) detail.add(found.getString("country"));
            Place place = new Place(found.getString("name"), TextUtils.join(", ", detail),
                found.getDouble("latitude"), found.getDouble("longitude"));
            if (country.equals(found.optString("country_code"))) places.add(ours++, place);
            else places.add(place);
        }
        return new ArrayList<>(places.subList(0, Math.min(PLACES, places.size())));
    }

    private static String get(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
            return body.toString();
        } finally {
            connection.disconnect();
        }
    }

    /** WMO weather codes, as Open-Meteo reports them. */
    private static String description(Context context, int code) {
        if (code == 0) return context.getString(R.string.weather_clear);
        if (code == 1) return context.getString(R.string.weather_mostly_clear);
        if (code == 2) return context.getString(R.string.weather_partly_cloudy);
        if (code == 3) return context.getString(R.string.weather_overcast);
        if (code <= 48) return context.getString(R.string.weather_fog);
        if (code <= 57) return context.getString(R.string.weather_drizzle);
        if (code <= 67) return context.getString(R.string.weather_rain);
        if (code <= 77) return context.getString(R.string.weather_snow);
        if (code <= 82) return context.getString(R.string.weather_showers);
        if (code <= 86) return context.getString(R.string.weather_snow_showers);
        return context.getString(R.string.weather_thunderstorm);
    }

    /** A character that WeatherIcon.in draws as the icon: every text holding one goes through it. */
    private static String symbol(int code) { return WeatherIcon.of(code); }
}
