module com.udacity.catpoint.security {
    exports com.udacity.catpoint.security.data to com.udacity.catpoint.app;
    exports com.udacity.catpoint.security.service to com.udacity.catpoint.app;
    exports com.udacity.catpoint.security.application to com.udacity.catpoint.app;
    requires com.udacity.catpoint.image;
    requires java.desktop;
    requires java.prefs;
    requires com.google.gson;
    requires com.google.common;

    opens com.udacity.catpoint.security.data to com.google.gson;
}