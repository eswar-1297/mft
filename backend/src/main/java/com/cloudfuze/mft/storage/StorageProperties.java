package com.cloudfuze.mft.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the S3-compatible object store (MinIO on-prem, AWS S3 in SaaS). */
@ConfigurationProperties(prefix = "mft.storage")
public class StorageProperties {

    private String endpoint;
    private String region = "us-east-1";
    private String bucket = "mft-files";
    private String accessKey;
    private String secretKey;
    private boolean pathStyle = true;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isPathStyle() {
        return pathStyle;
    }

    public void setPathStyle(boolean pathStyle) {
        this.pathStyle = pathStyle;
    }
}
