package com.cloudfuze.mft.oidc;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mft.oidc")
public class OidcProperties {

    private boolean enabled = false;
    private String issuer = "";
    private String audience = "";

    public boolean isEnabled() {
        return enabled && issuer != null && !issuer.isBlank();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }
}
