package com.cloudfuze.mft.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Sign, encrypt, and send a stored object to a saved AS2 partner. */
public record As2SendRequest(@NotBlank String storageKey, @NotNull UUID as2PartnerId) {
}
