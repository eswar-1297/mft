package com.cloudfuze.mft.as2.dto;

import jakarta.validation.constraints.NotBlank;

public record As2PartnerRequest(@NotBlank String name, @NotBlank String partnerAs2Id,
                                @NotBlank String partnerCertificatePem, @NotBlank String inboundUrl) {
}
