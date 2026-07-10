package com.cloudfuze.mft.as2;

import com.cloudfuze.mft.audit.AuditService;
import com.cloudfuze.mft.crypto.CryptoVault;
import com.cloudfuze.mft.storage.StorageService;
import com.cloudfuze.mft.tenant.TenantContext;
import com.cloudfuze.mft.transfer.Transfer;
import com.cloudfuze.mft.transfer.TransferDirection;
import com.cloudfuze.mft.transfer.TransferRepository;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.RecipientId;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.RecipientInformationStore;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientId;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The AS2 engine: sign+encrypt (CMS SignedData then EnvelopedData) and POST outbound, decrypt+
 * verify and record inbound. Certificates are exchanged and pinned out-of-band, the same
 * trust-on-first-use model already used for SFTP partner host keys — v1 deliberately has no CA
 * chain validation. The AS2 payload and the MDN receipt are both sent as plain CMS content
 * ({@code application/pkcs7-mime}); no separate MIME/mail library is needed for either.
 */
@Service
public class As2Service {

    private static final String BOUNDARY = "----cloudfuze-as2-mdn";

    private final As2IdentityRepository identities;
    private final As2PartnerRepository partners;
    private final As2CertificateUtil certUtil;
    private final CryptoVault vault;
    private final StorageService storage;
    private final TransferRepository transfers;
    private final AuditService audit;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public As2Service(As2IdentityRepository identities, As2PartnerRepository partners,
                      As2CertificateUtil certUtil, CryptoVault vault, StorageService storage,
                      TransferRepository transfers, AuditService audit) {
        this.identities = identities;
        this.partners = partners;
        this.certUtil = certUtil;
        this.vault = vault;
        this.storage = storage;
        this.transfers = transfers;
        this.audit = audit;
    }

    public record As2SendResult(boolean success, String disposition, long bytesSent) {
    }

    /** This tenant's own AS2 identity, generating one on first use. */
    @Transactional
    public As2Identity identityFor() {
        return identities.findFirstByOrderByCreatedAtAsc().orElseGet(() -> {
            String tenantId = TenantContext.get();
            String as2Id = "CLOUDFUZE-" + tenantId.substring(0, Math.min(8, tenantId.length())).toUpperCase();
            As2CertificateUtil.GeneratedIdentity gen = certUtil.generateSelfSigned(as2Id);
            As2Identity identity = new As2Identity(UUID.randomUUID(), as2Id, gen.certificatePem(),
                    vault.encrypt(gen.privateKeyPem()));
            identities.save(identity);
            audit.record("as2identity.created", "as2identity", identity.getId().toString(),
                    Map.of("as2Id", as2Id));
            return identity;
        });
    }

    /** Sign, encrypt, and POST a file to an AS2 partner; parses and verifies the synchronous MDN. */
    public As2SendResult send(As2Partner partner, Path payloadFile, String filename) {
        try {
            As2Identity identity = identityFor();
            PrivateKey ourKey = certUtil.parsePrivateKey(vault.decrypt(identity.getPrivateKeyEnc()));
            X509Certificate ourCert = certUtil.parseCertificate(identity.getCertificatePem());
            X509Certificate partnerCert = certUtil.parseCertificate(partner.getPartnerCertificatePem());

            byte[] payload = Files.readAllBytes(payloadFile);
            byte[] signed = sign(payload, ourKey, ourCert);
            byte[] encrypted = encrypt(signed, partnerCert);

            String messageId = "<" + UUID.randomUUID() + "@cloudfuze-mft>";
            HttpRequest request = HttpRequest.newBuilder(URI.create(partner.getInboundUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/pkcs7-mime; smime-type=enveloped-data; name=smime.p7m")
                    .header("AS2-From", identity.getAs2Id())
                    .header("AS2-To", partner.getPartnerAs2Id())
                    .header("Message-ID", messageId)
                    .header("Disposition-Notification-To", identity.getAs2Id())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(encrypted))
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() / 100 != 2) {
                return new As2SendResult(false, "http-" + response.statusCode(), 0);
            }
            String disposition = parseMdnDisposition(response.body(), partnerCert);
            boolean ok = disposition != null && disposition.contains("processed");
            return new As2SendResult(ok, disposition, payload.length);
        } catch (Exception e) {
            throw new IllegalStateException("AS2 send failed: " + e.getMessage(), e);
        }
    }

    /**
     * Process an inbound AS2 POST already bound to the correct tenant (see {@link As2Controller}).
     * Resolves the sender by their {@code AS2-From} header, decrypts with our private key, verifies
     * their signature against their pinned certificate, stores the payload, records a
     * {@code Transfer}, and returns a signed MDN to send back as the HTTP response body.
     */
    @Transactional
    public byte[] receive(String as2From, byte[] encryptedBody) {
        As2Partner sender = partners.findScopedByPartnerAs2Id(as2From)
                .orElseThrow(() -> new IllegalArgumentException("Unknown AS2 partner: " + as2From));
        As2Identity identity = identityFor();
        PrivateKey ourKey = certUtil.parsePrivateKey(vault.decrypt(identity.getPrivateKeyEnc()));
        X509Certificate ourCert = certUtil.parseCertificate(identity.getCertificatePem());
        X509Certificate senderCert = certUtil.parseCertificate(sender.getPartnerCertificatePem());

        boolean processed;
        String filename = "as2-message.bin";
        try {
            byte[] signed = decrypt(encryptedBody, ourKey, ourCert);
            byte[] original = verifyAndExtract(signed, senderCert);

            Transfer transfer = new Transfer(UUID.randomUUID(), TransferDirection.AS2_RECEIVE,
                    sender.getPartnerAs2Id(), filename, "as2:" + as2From);
            transfer.markRunning();
            Path stage = Files.createTempFile("mft-as2-recv-", ".stage");
            try {
                Files.write(stage, original);
                StorageService.StoredObject stored = storage.putFile(stage, filename, null);
                transfer.markSucceeded(stored.key(), stored.size(), stored.sha256());
            } finally {
                Files.deleteIfExists(stage);
            }
            transfers.save(transfer);
            audit.record("as2.message.received", "transfer", transfer.getId().toString(),
                    Map.of("partnerAs2Id", as2From, "bytes", original.length));
            processed = true;
        } catch (Exception e) {
            audit.record("as2.message.rejected", "as2partner", sender.getId().toString(),
                    Map.of("partnerAs2Id", as2From, "error", nz(e.getMessage())));
            processed = false;
        }

        try {
            byte[] report = buildMdnReport(identity.getAs2Id(), processed);
            return sign(report, certUtil.parsePrivateKey(vault.decrypt(identity.getPrivateKeyEnc())), ourCert);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate MDN", e);
        }
    }

    // --- CMS crypto ---

    private static byte[] sign(byte[] content, PrivateKey key, X509Certificate cert) throws Exception {
        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(key);
        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                .build(signer, cert));
        gen.addCertificates(new JcaCertStore(List.of(cert)));
        CMSSignedData signedData = gen.generate(new CMSProcessableByteArray(content), true);
        return signedData.getEncoded();
    }

    private static byte[] encrypt(byte[] content, X509Certificate recipientCert) throws Exception {
        CMSEnvelopedDataGenerator gen = new CMSEnvelopedDataGenerator();
        gen.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(recipientCert).setProvider("BC"));
        CMSEnvelopedData enveloped = gen.generate(new CMSProcessableByteArray(content),
                new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_CBC).setProvider("BC").build());
        return enveloped.getEncoded();
    }

    private static byte[] decrypt(byte[] encryptedBytes, PrivateKey ourKey, X509Certificate ourCert) throws Exception {
        CMSEnvelopedData enveloped = new CMSEnvelopedData(encryptedBytes);
        RecipientInformationStore recipients = enveloped.getRecipientInfos();
        RecipientId recipientId = new JceKeyTransRecipientId(ourCert);
        RecipientInformation recipient = recipients.get(recipientId);
        if (recipient == null) {
            throw new IllegalStateException("No matching recipient info for our certificate — wrong key?");
        }
        return recipient.getContent(new JceKeyTransEnvelopedRecipient(ourKey).setProvider("BC"));
    }

    private static byte[] verifyAndExtract(byte[] signedBytes, X509Certificate expectedSignerCert) throws Exception {
        CMSSignedData signedData = new CMSSignedData(signedBytes);
        SignerInformation signer = signedData.getSignerInfos().getSigners().iterator().next();
        boolean ok = signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC")
                .build(expectedSignerCert));
        if (!ok) {
            throw new SecurityException("AS2 signature verification failed — untrusted sender or tampered message");
        }
        CMSTypedData signedContent = signedData.getSignedContent();
        return (byte[]) signedContent.getContent();
    }

    // --- MDN (a hand-built, fixed-format multipart/report — no MIME library needed) ---

    private static byte[] buildMdnReport(String ourAs2Id, boolean processed) {
        String disposition = processed
                ? "automatic-action/MDN-sent-automatically; processed"
                : "automatic-action/MDN-sent-automatically; failed";
        String body = "--" + BOUNDARY + "\r\n"
                + "Content-Type: text/plain\r\n\r\n"
                + (processed ? "The message was received and processed successfully."
                             : "The message could not be processed.") + "\r\n"
                + "--" + BOUNDARY + "\r\n"
                + "Content-Type: message/disposition-notification\r\n\r\n"
                + "Reporting-UA: CloudFuze MFT\r\n"
                + "Final-Recipient: rfc822; " + ourAs2Id + "\r\n"
                + "Disposition: " + disposition + "\r\n"
                + "--" + BOUNDARY + "--\r\n";
        return body.getBytes(StandardCharsets.US_ASCII);
    }

    private static String parseMdnDisposition(byte[] responseBody, X509Certificate expectedSignerCert)
            throws Exception {
        byte[] report = verifyAndExtract(responseBody, expectedSignerCert);
        String text = new String(report, StandardCharsets.US_ASCII);
        for (String line : text.split("\r?\n")) {
            if (line.regionMatches(true, 0, "Disposition:", 0, 12)) {
                return line.substring(line.indexOf(':') + 1).trim();
            }
        }
        return null;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
