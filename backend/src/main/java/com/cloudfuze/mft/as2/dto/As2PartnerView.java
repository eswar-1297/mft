package com.cloudfuze.mft.as2.dto;

import com.cloudfuze.mft.as2.As2Partner;

import java.time.Instant;
import java.util.UUID;

public record As2PartnerView(UUID id, String name, String partnerAs2Id, String partnerCertificatePem,
                             String inboundUrl, Instant createdAt) {

    public static As2PartnerView of(As2Partner p) {
        return new As2PartnerView(p.getId(), p.getName(), p.getPartnerAs2Id(),
                p.getPartnerCertificatePem(), p.getInboundUrl(), p.getCreatedAt());
    }
}
