# R8 rules for the release build.
#
# Room, Hilt/Dagger, CameraX, ML Kit and Compose ship their own consumer rules, so almost nothing is needed here.
# Add a rule only when a minified build is seen to fail at run time, and say why next to it.

# Readable crash reports: keep file names and line numbers, hide the original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Enum names are stored in the database and in backups as text (Enum.name / valueOf), so they must not be renamed.
-keepclassmembers enum com.kirana.fefoguard.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepnames enum com.kirana.fefoguard.**

# Room creates the generated FefoGuardDatabase_Impl by reflection through its no-argument constructor. A minified build
# without this rule crashed at launch with NoSuchMethodException (seen on the emulator).
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}

# ML Kit finds its parts through registrar classes that are named only in the manifest and created by reflection.
# Without this rule R8 removes them and BarcodeScanning.getClient fails when the Scan screen opens (seen on the emulator).
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
