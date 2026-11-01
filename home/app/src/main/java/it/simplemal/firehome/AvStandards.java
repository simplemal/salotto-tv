package it.simplemal.firehome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Display;
import android.view.WindowManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The audio and video standards from three sides: what the Stick can do, what the TV announces,
 * what is in use now. All of it is asked of the system at each visit: the tables here only put
 * names and a line of explanation on what it reports. Slow, so never on the main thread.
 */
@SuppressLint("InlinedApi")
final class AvStandards {
    /** A row of the page. */
    static final class Line {
        final String name;
        final String stick;
        final String tv;
        final String now;
        final String text;
        boolean opensGroup;

        Line(String name, String stick, String tv, String now, String text) {
            this.name = name;
            this.stick = stick;
            this.tv = tv;
            this.now = now;
            this.text = text;
        }
    }

    /** What is chosen in Video and Audio, already in words. */
    static final class Chosen {
        String resolution;
        String range;
        String colorFormat;
        String colorDepth;
        String surround;
    }

    /** The best one for a type: a hardware decoder beats a software one, a bigger picture a smaller one. */
    private static final class Decoder {
        final boolean hardware;
        final int width;
        final int height;
        final int rate;
        final int channels;
        final Set<Integer> profiles = new HashSet<>();

        Decoder(boolean hardware, int width, int height, int rate, int channels) {
            this.hardware = hardware;
            this.width = width;
            this.height = height;
            this.rate = rate;
            this.channels = channels;
        }

        boolean beats(Decoder other) {
            if (hardware != other.hardware) return hardware;
            return (long) width * height * rate > (long) other.width * other.height * other.rate;
        }

        String picture() { return size(height) + " " + rate + " Hz"; }
    }

    /** Type, name, explanation, in the order of the page. A type found on the Stick and not here joins the "other" row. */
    private static final Object[][] VIDEO = {
        {"video/av01", "AV1", R.string.av_video_av1},
        {"video/hevc", "H.265 (HEVC)", R.string.av_video_hevc},
        {"video/x-vnd.on2.vp9", "VP9", R.string.av_video_vp9},
        {"video/avc", "H.264 (AVC)", R.string.av_video_avc},
        {"video/mpeg2", "MPEG-2", R.string.av_video_mpeg2},
    };
    /** Type, encoding, name, explanation: these can leave the Stick as they are, for the TV or soundbar to decode. */
    private static final Object[][] SURROUND = {
        {"audio/eac3", AudioFormat.ENCODING_E_AC3, "Dolby Digital Plus", R.string.av_surround_eac3},
        {"audio/ac3", AudioFormat.ENCODING_AC3, "Dolby Digital", R.string.av_surround_ac3},
        {"audio/ac4", AudioFormat.ENCODING_AC4, "Dolby AC-4", R.string.av_surround_ac4},
        {"audio/true-hd", AudioFormat.ENCODING_DOLBY_TRUEHD, "Dolby TrueHD", R.string.av_surround_truehd},
        {"audio/vnd.dts", AudioFormat.ENCODING_DTS, "DTS", R.string.av_surround_dts},
        {"audio/vnd.dts.hd", AudioFormat.ENCODING_DTS_HD, "DTS-HD", R.string.av_surround_dts_hd},
    };
    private static final String AAC = "audio/mp4a-latm";
    private static final String DOLBY_VISION = "video/dolby-vision";
    /** Decoded PCM, not a format anyone would look for. */
    private static final String RAW = "audio/raw";
    private static final Map<String, String> OTHER_NAMES = new HashMap<>();
    static {
        OTHER_NAMES.put("video/x-vnd.on2.vp8", "VP8");
        OTHER_NAMES.put("video/mp4v-es", "MPEG-4");
        OTHER_NAMES.put("video/3gpp", "H.263");
        OTHER_NAMES.put("audio/opus", "Opus");
        OTHER_NAMES.put("audio/flac", "FLAC");
        OTHER_NAMES.put("audio/mpeg", "MP3");
        OTHER_NAMES.put("audio/vorbis", "Vorbis");
        OTHER_NAMES.put("audio/3gpp", "AMR");
        OTHER_NAMES.put("audio/amr-wb", "AMR-WB");
        OTHER_NAMES.put("audio/g711-alaw", "G.711");
        OTHER_NAMES.put("audio/g711-mlaw", "G.711");
    }
    /** No call says what is on the HDMI cable now: Fire OS writes it here, "none" for a normal picture. */
    private static final String HDR_NOW = "sys.display-hdr";
    private static final String HDR_NOW_NONE = "none";
    private static final String AUDIO_FEATURES = "audio_platform_capabilities";
    private static final String SURROUND_MODE = "encoded_surround_output";
    private static final int SURROUND_BEST = 0;
    private static final int SURROUND_DD_PLUS = 2;
    private static final int SURROUND_DD = 3;
    private static final int TEST_RATE = 48000;
    private static final int HEIGHT_4K = 2160;
    private static final int HEIGHT_8K = 4320;
    private static final int RATE_STEP = 10;

    private static final String NONE = "—";
    private static final String NOT_NEEDED = "—";

    private final Context context;
    private final Chosen chosen;
    private final Display display;
    private final String yes;
    private final String no;
    private final String declared;
    private final String notDeclared;
    private final String unknown;
    private final String available;
    private final String selected;
    private final Map<String, Decoder> decoders = new LinkedHashMap<>();
    private final List<Line> lines = new ArrayList<>();
    private boolean opening;
    /**
     * Unless this is on the automatic choice, Android adds or removes surround formats and channels
     * on its own, so that the list no longer tells what the TV announced: it even shows AC-4 and
     * 7.1 on the capture dongle of the test Stick. The list of the TV alone is hidden from apps.
     */
    private int surroundMode;
    private Set<Integer> tvEncodings = Collections.emptySet();
    private int tvChannels;

    private AvStandards(Context context, Chosen chosen) {
        this.context = context;
        this.chosen = chosen;
        display = context.getSystemService(WindowManager.class).getDefaultDisplay();
        yes = context.getString(R.string.av_yes);
        no = context.getString(R.string.av_no);
        declared = context.getString(R.string.av_declared);
        notDeclared = context.getString(R.string.av_not_declared);
        unknown = context.getString(R.string.av_unknown);
        available = context.getString(R.string.av_available);
        selected = context.getString(R.string.av_selected);
    }

    static List<Line> read(Context context, Chosen chosen) {
        AvStandards standards = new AvStandards(context, chosen);
        standards.readDecoders();
        standards.readTvAudio();
        standards.picture();
        standards.hdr();
        standards.videoFormats();
        standards.audio();
        return standards.lines;
    }

    /** Whole sentences, each its own string, one after the other: the empty ones leave no trace. */
    private static String sentences(String... parts) {
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (text.length() > 0) text.append(' ');
            text.append(part);
        }
        return text.toString();
    }

    private void add(String name, String stick, String tv, String now, String text) {
        Line line = new Line(name, stick, tv, now, text);
        line.opensGroup = opening;
        opening = false;
        lines.add(line);
    }

    private void readDecoders() {
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder()) continue;
            boolean hardware = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info.isHardwareAccelerated();
            for (String type : info.getSupportedTypes()) {
                Decoder decoder = decoder(info, type, hardware);
                Decoder known = decoders.get(type);
                if (known != null) {
                    decoder.profiles.addAll(known.profiles);
                    known.profiles.addAll(decoder.profiles);
                }
                if (known == null || decoder.beats(known)) decoders.put(type, decoder);
            }
        }
    }

    private static Decoder decoder(MediaCodecInfo info, String type, boolean hardware) {
        CodecCapabilities capabilities = info.getCapabilitiesForType(type);
        VideoCapabilities video = capabilities.getVideoCapabilities();
        int[] best = video != null ? bestPicture(video) : new int[3];
        int channels = capabilities.getAudioCapabilities() != null ? capabilities.getAudioCapabilities().getMaxInputChannelCount() : 0;
        Decoder decoder = new Decoder(hardware, best[0], best[1], best[2], channels);
        for (CodecProfileLevel level : capabilities.profileLevels) decoder.profiles.add(level.profile);
        return decoder;
    }

    /** Width, height and frames at the largest 16:9 picture. Decoders claim odd margins, like 64 for a 60 Hz chip. */
    private static int[] bestPicture(VideoCapabilities video) {
        int height = Math.min(video.getSupportedHeights().getUpper(), video.getSupportedWidths().getUpper() * 9 / 16);
        int width = height * 16 / 9;
        if (!video.isSizeSupported(width, height)) return new int[]{width, height, 0};
        int rate = (int) Math.floor(video.getSupportedFrameRatesFor(width, height).getUpper());
        return new int[]{width, height, rate - rate % RATE_STEP};
    }

    private boolean plays(String type, int profile) {
        Decoder decoder = decoders.get(type);
        return decoder != null && decoder.profiles.contains(profile);
    }

    private void readTvAudio() {
        surroundMode = Settings.Global.getInt(context.getContentResolver(), SURROUND_MODE, SURROUND_BEST);
        Set<Integer> encodings = new HashSet<>();
        for (AudioDeviceInfo device : context.getSystemService(AudioManager.class).getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int type = device.getType();
            if (type != AudioDeviceInfo.TYPE_HDMI && type != AudioDeviceInfo.TYPE_HDMI_ARC) continue;
            for (int encoding : device.getEncodings()) encodings.add(encoding);
            for (int count : device.getChannelCounts()) tvChannels = Math.max(tvChannels, count);
        }
        tvEncodings = encodings;
    }

    private void picture() {
        Map<Integer, Display.Mode> sizes = new TreeMap<>(Collections.reverseOrder());
        for (Display.Mode mode : display.getSupportedModes()) {
            Display.Mode known = sizes.get(mode.getPhysicalHeight());
            if (known == null || mode.getRefreshRate() > known.getRefreshRate()) sizes.put(mode.getPhysicalHeight(), mode);
        }
        List<Display.Mode> modes = new ArrayList<>(sizes.values());
        Display.Mode top = modes.remove(0);
        String tv = size(top.getPhysicalHeight()) + " " + hz(top.getRefreshRate());
        List<String> names = new ArrayList<>();
        for (Display.Mode mode : modes) {
            names.add(context.getString(R.string.av_size_at_rate, size(mode.getPhysicalHeight()), hz(mode.getRefreshRate())));
        }
        Display.Mode mode = display.getMode();
        Decoder best = bestVideo();
        add(context.getString(R.string.av_resolution), best != null ? best.picture() : NONE, tv,
            size(mode.getPhysicalHeight()) + " " + hz(mode.getRefreshRate()),
            sentences(context.getString(R.string.av_resolution_text),
                names.isEmpty() ? "" : context.getString(R.string.av_tv_also, TextUtils.join(", ", names)),
                context.getString(R.string.av_chosen_in_video, chosen.resolution)));
        boolean tenBit = plays("video/hevc", CodecProfileLevel.HEVCProfileMain10);
        add(context.getString(R.string.av_color), tenBit ? "10 bit" : "8 bit", display.isWideColorGamut() ? declared : notDeclared,
            chosen.colorFormat + ", " + chosen.colorDepth,
            sentences(context.getString(R.string.av_color_text),
                display.isWideColorGamut() ? "" : context.getString(R.string.av_tv_no_wide_color)));
    }

    private Decoder bestVideo() {
        Decoder best = null;
        for (Object[] format : VIDEO) {
            Decoder decoder = decoders.get(format[0]);
            if (decoder != null && (best == null || decoder.beats(best))) best = decoder;
        }
        return best;
    }

    private void hdr() {
        Set<Integer> tv = new HashSet<>();
        Display.HdrCapabilities shown = display.getHdrCapabilities();
        if (shown != null) {
            for (int type : shown.getSupportedHdrTypes()) tv.add(type);
        }
        String active = property(HDR_NOW).toLowerCase(Locale.ROOT);
        boolean known = !active.isEmpty();
        boolean on = known && !active.equals(HDR_NOW_NONE);
        boolean hdr10 = plays("video/hevc", CodecProfileLevel.HEVCProfileMain10HDR10)
            || plays("video/x-vnd.on2.vp9", CodecProfileLevel.VP9Profile2HDR)
            || plays("video/av01", CodecProfileLevel.AV1ProfileMain10HDR10);
        boolean hdr10Plus = plays("video/hevc", CodecProfileLevel.HEVCProfileMain10HDR10Plus)
            || plays("video/x-vnd.on2.vp9", CodecProfileLevel.VP9Profile2HDR10Plus)
            || plays("video/av01", CodecProfileLevel.AV1ProfileMain10HDR10Plus);
        boolean hlg = plays("video/hevc", CodecProfileLevel.HEVCProfileMain10);
        boolean vision = decoders.containsKey(DOLBY_VISION);
        String both = context.getString(R.string.av_sdr_and_hdr);
        add(context.getString(R.string.av_dynamic_range), hdr10 || hlg || vision ? both : "SDR",
            tv.isEmpty() ? context.getString(R.string.av_sdr_only) : both,
            !known ? unknown : on ? "HDR" : "SDR",
            sentences(context.getString(R.string.av_dynamic_range_text),
                tv.isEmpty() ? context.getString(R.string.av_tv_no_hdr) : "",
                context.getString(R.string.av_chosen_in_video, chosen.range)));
        opening = true;
        hdrLine("HDR10", hdr10, tv, Display.HdrCapabilities.HDR_TYPE_HDR10, known, on && active.contains("10") && !plus(active),
            context.getString(R.string.av_hdr10_text));
        hdrLine("HDR10+", hdr10Plus, tv, Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS, known, on && plus(active),
            context.getString(R.string.av_hdr10_plus_text));
        hdrLine("HLG", hlg, tv, Display.HdrCapabilities.HDR_TYPE_HLG, known, on && active.contains("hlg"),
            context.getString(R.string.av_hlg_text));
        hdrLine("Dolby Vision", vision, tv, Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION, known, on && active.contains("dolby"),
            context.getString(R.string.av_dolby_vision_text));
    }

    private static boolean plus(String active) { return active.contains("+") || active.contains("plus"); }

    private void hdrLine(String name, boolean stick, Set<Integer> tv, int type, boolean known, boolean on, String text) {
        boolean announced = tv.contains(type);
        add(name, stick ? yes : no, announced ? declared : notDeclared, !known ? unknown : on ? yes : no,
            sentences(text, announced ? "" : context.getString(R.string.av_tv_does_not_declare, name)));
    }

    private void videoFormats() {
        opening = true;
        Set<String> listed = new HashSet<>(Arrays.asList(DOLBY_VISION, RAW, AAC));
        for (Object[] format : VIDEO) {
            String type = (String) format[0];
            listed.add(type);
            Decoder decoder = decoders.get(type);
            add((String) format[1], decoder != null ? decoder.picture() : no, NOT_NEEDED, NONE,
                sentences(context.getString((Integer) format[2]), decoder != null ? "" : context.getString(R.string.av_no_decoder),
                    context.getString(R.string.av_tv_not_needed)));
        }
        for (Object[] format : SURROUND) listed.add((String) format[0]);
        other("video/", context.getString(R.string.av_other_video), listed);
    }

    /** Whatever else the Stick decodes: nothing it reports stays out of the page. */
    private void other(String kind, String name, Set<String> listed) {
        Set<String> names = new LinkedHashSet<>();
        for (String type : decoders.keySet()) {
            if (!type.startsWith(kind) || listed.contains(type)) continue;
            String known = OTHER_NAMES.get(type);
            names.add(known != null ? known : type.substring(kind.length()));
        }
        if (names.isEmpty()) return;
        add(name, context.getResources().getQuantityString(R.plurals.av_formats, names.size(), names.size()), NOT_NEEDED, NONE,
            context.getString(R.string.av_decoded_by_stick, TextUtils.join(", ", names)));
    }

    private void audio() {
        opening = true;
        int stickChannels = 0;
        for (Object[] format : SURROUND) {
            Decoder decoder = decoders.get(format[0]);
            if (decoder != null) stickChannels = Math.max(stickChannels, decoder.channels);
        }
        String name = context.getString(R.string.av_audio_channels);
        String tv = surroundMode != SURROUND_BEST ? unknown : tvChannels > 0 ? channels(tvChannels) : notDeclared;
        add(name, stickChannels > 0 ? channels(stickChannels) : NONE, tv, chosen.surround,
            sentences(context.getString(R.string.av_channels_text), tvSentence(tv, context.getString(R.string.av_tv_no_channels))));
        atmos();
        Set<String> listed = new HashSet<>(Arrays.asList(RAW, AAC));
        for (Object[] format : SURROUND) {
            String type = (String) format[0];
            int encoding = (Integer) format[1];
            listed.add(type);
            name = (String) format[2];
            tv = tvSays(encoding);
            String now = surroundNow(type, encoding);
            boolean decoded = decoders.containsKey(type);
            add(name, decoded ? yes : no, tv, now, sentences(context.getString((Integer) format[3]),
                decoded ? "" : context.getString(R.string.av_no_decoder),
                tvSentence(tv, context.getString(R.string.av_tv_does_not_declare, name)),
                now.equals(available) ? context.getString(R.string.av_available_text)
                    : now.equals(selected) ? context.getString(R.string.av_selected_text) : ""));
        }
        Decoder aac = decoders.get(AAC);
        add("AAC", aac != null ? channels(aac.channels) : no, NOT_NEEDED, NONE, context.getString(R.string.av_aac_text));
        other("audio/", context.getString(R.string.av_other_audio), listed);
    }

    private void atmos() {
        JSONObject features = atmosFeatures();
        String tv = tvSays(AudioFormat.ENCODING_E_AC3_JOC, AudioFormat.ENCODING_DOLBY_MAT);
        String name = "Dolby Atmos";
        add(name, features != null ? yes : no, tv, features != null && features.optBoolean("enabled") ? available : no,
            sentences(context.getString(R.string.av_atmos_text), tvSentence(tv, context.getString(R.string.av_tv_does_not_declare, name))));
    }

    /** Fire OS keeps here what its audio can do; Atmos has no decoder of its own in the list. */
    private JSONObject atmosFeatures() {
        String features = Settings.Global.getString(context.getContentResolver(), AUDIO_FEATURES);
        if (features == null) return null;
        try {
            return new JSONObject(features).getJSONObject("audiocaps").optJSONObject("atmos");
        } catch (JSONException e) {
            return null;
        }
    }

    private String tvSays(int... encodings) {
        if (surroundMode != SURROUND_BEST) return unknown;
        for (int encoding : encodings) {
            if (tvEncodings.contains(encoding)) return declared;
        }
        return notDeclared;
    }

    /** `missing` is what to say if the TV does not announce it. */
    private String tvSentence(String tv, String missing) {
        if (tv.equals(notDeclared)) return missing;
        if (tv.equals(unknown)) return context.getString(R.string.av_surround_fixed, chosen.surround);
        return "";
    }

    private String surroundNow(String type, int encoding) {
        boolean chosenOne = surroundMode == SURROUND_DD_PLUS && type.equals("audio/eac3")
            || surroundMode == SURROUND_DD && type.equals("audio/ac3");
        if (chosenOne) return selected;
        return direct(encoding) ? available : no;
    }

    /** Whether the audio system would take this format straight to the HDMI output right now. */
    private static boolean direct(int encoding) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        AudioFormat format = new AudioFormat.Builder().setEncoding(encoding).setSampleRate(TEST_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1).build();
        return AudioTrack.isDirectPlaybackSupported(format,
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build());
    }

    private static String size(int height) {
        return height >= HEIGHT_8K ? "8K" : height >= HEIGHT_4K ? "4K" : height + "p";
    }

    private static String hz(float rate) {
        int whole = Math.round(rate);
        return Math.abs(rate - whole) < 0.01f ? whole + " Hz" : String.format(Locale.getDefault(), "%.2f Hz", rate);
    }

    private String channels(int count) {
        return count > 2 ? count - 1 + ".1" : context.getString(count == 2 ? R.string.av_stereo : R.string.av_mono);
    }

    /** Readable by any app; empty if a later Fire OS closes it. */
    private static String property(String name) {
        try {
            Process process = new ProcessBuilder("getprop", name).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String value = reader.readLine();
                return value != null ? value.trim() : "";
            } finally {
                process.destroy();
            }
        } catch (IOException e) {
            return "";
        }
    }
}
