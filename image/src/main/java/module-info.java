module com.udacity.catpoint.image {
    requires java.desktop;
    requires org.slf4j;
    requires software.amazon.awssdk.services.rekognition;
    requires software.amazon.awssdk.auth;
    requires software.amazon.awssdk.regions;
    requires software.amazon.awssdk.core;
    exports com.udacity.catpoint.image.service to com.udacity.catpoint.security, com.udacity.catpoint.app;
}