package com.cloudfuze.mft.as2.dto;

import com.cloudfuze.mft.as2.As2Identity;

public record As2IdentityView(String as2Id, String certificatePem) {

    public static As2IdentityView of(As2Identity identity) {
        return new As2IdentityView(identity.getAs2Id(), identity.getCertificatePem());
    }
}
