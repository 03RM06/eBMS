package gov.brgy.ebms.desktop.core;

import javafx.beans.binding.StringBinding;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Locale;
import java.util.ResourceBundle;

public class I18n {

    private static final ObjectProperty<Locale> locale =
        new SimpleObjectProperty<>(Locale.ENGLISH);
    private static ResourceBundle bundle =
        ResourceBundle.getBundle("i18n/ui", Locale.ENGLISH);

    private I18n() {}

    public static void setLocale(String lang) {
        Locale l = "fil".equals(lang) ? Locale.forLanguageTag("fil") : Locale.ENGLISH;
        bundle = ResourceBundle.getBundle("i18n/ui", l);
        locale.set(l);
        Session.get().setLocale(lang);
    }

    public static Locale currentLocale() {
        return locale.get();
    }

    public static ObjectProperty<Locale> localeProperty() {
        return locale;
    }

    public static StringBinding tr(String key) {
        return Bindings.createStringBinding(() -> {
            try { return bundle.getString(key); } catch (Exception e) { return key; }
        }, locale);
    }

    public static String get(String key) {
        try { return bundle.getString(key); } catch (Exception e) { return key; }
    }
}
