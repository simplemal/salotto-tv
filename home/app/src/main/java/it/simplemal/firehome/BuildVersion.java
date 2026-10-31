package it.simplemal.firehome;

import android.content.Context;
import android.content.pm.PackageManager;

/** The installed version, as shown to people. */
final class BuildVersion {
    private BuildVersion() {}

    /** Name and build, as in "0.12.14 (45)". */
    @SuppressWarnings("deprecation")
    static String full(Context context) {
        try {
            int build = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
            return name(context) + " (" + build + ")";
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    static String name(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }
}
