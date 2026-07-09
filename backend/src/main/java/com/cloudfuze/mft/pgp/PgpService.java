package com.cloudfuze.mft.pgp;

import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPBEEncryptedData;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcPBEDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.bc.BcPBEKeyEncryptionMethodGenerator;
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Real OpenPGP encryption/decryption for the transfer pipeline, using passphrase-based (PBE)
 * AES-256 with a modification-detection (integrity) packet. Output is a standards-compliant
 * OpenPGP message, so partners can decrypt it with any OpenPGP tool given the passphrase.
 *
 * <p>Passphrase-based PGP is deliberately first; public/private-key PGP (partner key management)
 * is the follow-on. Streams throughout so multi-GB files never load fully into memory.
 */
@Service
public class PgpService {

    private static final int BUFFER = 1 << 16;

    /** Encrypt {@code in} → {@code out} as an OpenPGP message locked with {@code passphrase}. */
    public void encrypt(Path in, Path out, char[] passphrase) {
        try (OutputStream fileOut = Files.newOutputStream(out)) {
            PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(
                    new BcPGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
                            .setWithIntegrityPacket(true));
            encGen.addMethod(new BcPBEKeyEncryptionMethodGenerator(passphrase));

            try (OutputStream encOut = encGen.open(fileOut, new byte[BUFFER])) {
                PGPCompressedDataGenerator compGen =
                        new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP);
                try (OutputStream compOut = compGen.open(encOut)) {
                    PGPLiteralDataGenerator litGen = new PGPLiteralDataGenerator();
                    try (OutputStream litOut = litGen.open(compOut, PGPLiteralData.BINARY,
                            in.getFileName().toString(), Files.size(in), new java.util.Date());
                         InputStream fileIn = Files.newInputStream(in)) {
                        fileIn.transferTo(litOut);
                    }
                }
            }
        } catch (IOException | PGPException e) {
            throw new PgpException("PGP encryption failed: " + e.getMessage(), e);
        }
    }

    /** Decrypt a passphrase-locked OpenPGP message {@code in} → {@code out}. */
    public void decrypt(Path in, Path out, char[] passphrase) {
        try (InputStream fileIn = PGPUtil.getDecoderStream(Files.newInputStream(in))) {
            JcaPGPObjectFactory factory = new JcaPGPObjectFactory(fileIn);
            Object first = factory.nextObject();
            PGPEncryptedDataList encList = (first instanceof PGPEncryptedDataList list)
                    ? list
                    : (PGPEncryptedDataList) factory.nextObject();

            PGPPBEEncryptedData enc = (PGPPBEEncryptedData) encList.get(0);
            try (InputStream clear = enc.getDataStream(
                    new BcPBEDataDecryptorFactory(passphrase, new BcPGPDigestCalculatorProvider()))) {
                Object message = new JcaPGPObjectFactory(clear).nextObject();
                if (message instanceof PGPCompressedData compressed) {
                    message = new JcaPGPObjectFactory(compressed.getDataStream()).nextObject();
                }
                if (!(message instanceof PGPLiteralData literal)) {
                    throw new PgpException("Unexpected PGP message structure", null);
                }
                try (InputStream literalIn = literal.getInputStream();
                     OutputStream fileOut = Files.newOutputStream(out)) {
                    literalIn.transferTo(fileOut);
                }
            }
            if (enc.isIntegrityProtected() && !enc.verify()) {
                throw new PgpException("PGP integrity check failed — message was tampered with", null);
            }
        } catch (IOException | PGPException e) {
            throw new PgpException("PGP decryption failed: " + e.getMessage(), e);
        }
    }

    public static class PgpException extends RuntimeException {
        public PgpException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
