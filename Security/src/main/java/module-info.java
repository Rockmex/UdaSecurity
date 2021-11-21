module Security {
    requires miglayout;
    requires java.desktop;
    requires Image;
    requires com.google.common;
    requires com.google.gson;
    requires java.prefs;
    opens com.udacity.security.data to com.google.gson;
}