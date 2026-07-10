package com.cloudfuze.mft.connector;

public record FtpsConnectionDetails(String host, int port, String username, String password) {

    public int portOrDefault() {
        return port > 0 ? port : 21;
    }
}
