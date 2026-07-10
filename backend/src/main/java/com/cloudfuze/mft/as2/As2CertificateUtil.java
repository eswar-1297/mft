package com.cloudfuze.mft.as2;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

/**
 * Self-signed X.509 certificate + RSA keypair generation, and PEM encode/decode, for AS2 identities.
 * AS2 partners exchange certificates out-of-band and pin them directly — the same trust-on-first-use
 * model already used for SFTP host keys. There is deliberately no CA chain to validate here (v1).
 */
@Component
public class As2CertificateUtil {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public record GeneratedIdentity(String certificatePem, String privateKeyPem) {
    }

    /** Generate a fresh self-signed AS2 identity: a 2048-bit RSA keypair and a matching certificate. */
    public GeneratedIdentity generateSelfSigned(String subjectCn) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair keyPair = gen.generateKeyPair();

            X500Name subject = new X500Name("CN=" + subjectCn);
            Instant now = Instant.now();
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    subject, // self-signed: issuer == subject
                    BigInteger.valueOf(now.toEpochMilli()),
                    Date.from(now.minusSeconds(60)),
                    Date.from(now.plusSeconds(60L * 60 * 24 * 365 * 10)), // 10-year validity
                    subject,
                    keyPair.getPublic());
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            X509CertificateHolder holder = builder.build(signer);
            X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);

            return new GeneratedIdentity(toPem(cert), toPem(keyPair.getPrivate()));
        } catch (Exception e) {
            throw new IllegalStateException("AS2 identity generation failed", e);
        }
    }

    public X509Certificate parseCertificate(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            if (!(obj instanceof X509CertificateHolder holder)) {
                throw new IllegalArgumentException("Not a certificate PEM");
            }
            return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse AS2 partner certificate: " + e.getMessage(), e);
        }
    }

    public PrivateKey parsePrivateKey(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            if (obj instanceof PEMKeyPair kp) {
                return converter.getPrivateKey(kp.getPrivateKeyInfo());
            }
            if (obj instanceof PrivateKeyInfo info) {
                return converter.getPrivateKey(info);
            }
            throw new IllegalArgumentException("Not a private key PEM");
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse AS2 private key", e);
        }
    }

    private static String toPem(Object obj) {
        try (StringWriter sw = new StringWriter(); JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(obj);
            writer.flush();
            return sw.toString();
        } catch (IOException e) {
            throw new IllegalStateException("PEM encoding failed", e);
        }
    }
}
