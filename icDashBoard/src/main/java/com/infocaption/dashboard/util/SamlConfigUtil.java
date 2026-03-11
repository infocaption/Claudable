package com.infocaption.dashboard.util;

import com.onelogin.saml2.settings.Saml2Settings;
import com.onelogin.saml2.settings.SettingsBuilder;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Loads SAML2 configuration from WEB-INF/saml.properties
 * and builds OneLogin Saml2Settings objects.
 */
public class SamlConfigUtil {

    private static final String CONFIG_PATH = "/WEB-INF/saml.properties";

    /**
     * Build Saml2Settings from the saml.properties file.
     *
     * @param ctx ServletContext to read configuration from WEB-INF
     * @return configured Saml2Settings
     */
    public static Saml2Settings getSaml2Settings(ServletContext ctx) throws Exception {
        Properties props = new Properties();
        try (InputStream is = ctx.getResourceAsStream(CONFIG_PATH)) {
            if (is == null) {
                throw new IOException("SAML config not found: " + CONFIG_PATH);
            }
            props.load(is);
        }

        Map<String, Object> samlData = new HashMap<>();

        // Service Provider settings
        samlData.put("onelogin.saml2.sp.entityid", props.getProperty("sp.entityid"));
        samlData.put("onelogin.saml2.sp.assertion_consumer_service.url",
                props.getProperty("sp.assertion_consumer_service.url"));
        samlData.put("onelogin.saml2.sp.assertion_consumer_service.binding",
                "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
        samlData.put("onelogin.saml2.sp.nameidformat",
                props.getProperty("sp.nameidformat",
                        "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress"));

        // Identity Provider settings
        samlData.put("onelogin.saml2.idp.entityid", props.getProperty("idp.entityid"));
        samlData.put("onelogin.saml2.idp.single_sign_on_service.url",
                props.getProperty("idp.single_sign_on_service.url"));
        samlData.put("onelogin.saml2.idp.single_sign_on_service.binding",
                "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect");
        samlData.put("onelogin.saml2.idp.x509cert", props.getProperty("idp.x509cert"));

        // Security settings
        samlData.put("onelogin.saml2.security.want_assertions_signed",
                Boolean.parseBoolean(props.getProperty("security.want_assertions_signed", "true")));
        samlData.put("onelogin.saml2.security.want_nameid",
                Boolean.parseBoolean(props.getProperty("security.want_nameid", "true")));

        // Security settings — configurable via saml.properties.
        // Defaults to RELAXED for compatibility with current Entra ID setup (no SP signing key).
        // To harden for production, set these to "true" in saml.properties
        // (requires matching IdP config + SP signing certificate):
        //   security.authnrequest_signed=true
        //   security.want_messages_signed=true
        //   security.want_assertions_encrypted=true
        //   security.strict=true
        samlData.put("onelogin.saml2.security.authnrequest_signed",
                Boolean.parseBoolean(props.getProperty("security.authnrequest_signed", "false")));
        samlData.put("onelogin.saml2.security.want_messages_signed",
                Boolean.parseBoolean(props.getProperty("security.want_messages_signed", "false")));
        samlData.put("onelogin.saml2.security.want_assertions_encrypted",
                Boolean.parseBoolean(props.getProperty("security.want_assertions_encrypted", "false")));
        samlData.put("onelogin.saml2.strict",
                Boolean.parseBoolean(props.getProperty("security.strict", "false")));

        SettingsBuilder builder = new SettingsBuilder();
        return builder.fromValues(samlData).build();
    }
}
