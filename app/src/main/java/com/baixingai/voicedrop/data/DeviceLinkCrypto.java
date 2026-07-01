package com.baixingai.voicedrop.data;

import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;

import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class DeviceLinkCrypto {
    private static final byte[] SALT = "voicedrop-device-link/v1".getBytes();
    private static final byte[] INFO = "anon-token".getBytes();
    private static final SecureRandom RANDOM = new SecureRandom();

    private DeviceLinkCrypto() {}

    public static Keypair newKeypair() {
        X25519PrivateKeyParameters priv = new X25519PrivateKeyParameters(RANDOM);
        byte[] pub = new byte[32];
        priv.generatePublicKey().encode(pub, 0);
        byte[] rawPriv = new byte[32];
        priv.encode(rawPriv, 0);
        return new Keypair(b64(rawPriv), b64(pub));
    }

    public static Blob encrypt(String token, String toPubB64) throws Exception {
        X25519PrivateKeyParameters eph = new X25519PrivateKeyParameters(RANDOM);
        byte[] ephPub = new byte[32];
        eph.generatePublicKey().encode(ephPub, 0);
        byte[] shared = shared(eph, new X25519PublicKeyParameters(unb64(toPubB64), 0));
        byte[] key = hkdf(shared);
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        byte[] encrypted = cipher.doFinal(token.getBytes("UTF-8"));
        byte[] combined = new byte[nonce.length + encrypted.length];
        System.arraycopy(nonce, 0, combined, 0, nonce.length);
        System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
        return new Blob(b64(ephPub), b64(combined));
    }

    public static String decrypt(String epkB64, String sealedB64, String privateKeyB64) throws Exception {
        X25519PrivateKeyParameters priv = new X25519PrivateKeyParameters(unb64(privateKeyB64), 0);
        byte[] shared = shared(priv, new X25519PublicKeyParameters(unb64(epkB64), 0));
        byte[] key = hkdf(shared);
        byte[] combined = unb64(sealedB64);
        byte[] nonce = new byte[12];
        byte[] encrypted = new byte[combined.length - 12];
        System.arraycopy(combined, 0, nonce, 0, 12);
        System.arraycopy(combined, 12, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        return new String(cipher.doFinal(encrypted), "UTF-8");
    }

    private static byte[] shared(X25519PrivateKeyParameters priv, X25519PublicKeyParameters pub) {
        X25519Agreement agreement = new X25519Agreement();
        agreement.init(priv);
        byte[] shared = new byte[agreement.getAgreementSize()];
        agreement.calculateAgreement(pub, shared, 0);
        return shared;
    }

    private static byte[] hkdf(byte[] shared) {
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(shared, SALT, INFO));
        byte[] key = new byte[32];
        hkdf.generateBytes(key, 0, key.length);
        return key;
    }

    private static String b64(byte[] data) {
        String encoded = new String(org.bouncycastle.util.encoders.Base64.encode(data));
        return encoded.replace('+', '-').replace('/', '_').replace("=", "");
    }

    private static byte[] unb64(String value) {
        String normalized = value.replace('-', '+').replace('_', '/');
        int padding = (4 - normalized.length() % 4) % 4;
        StringBuilder padded = new StringBuilder(normalized);
        for (int i = 0; i < padding; i++) padded.append('=');
        return org.bouncycastle.util.encoders.Base64.decode(padded.toString());
    }

    public static final class Keypair {
        public final String privateKeyB64;
        public final String publicKeyB64;

        public Keypair(String privateKeyB64, String publicKeyB64) {
            this.privateKeyB64 = privateKeyB64;
            this.publicKeyB64 = publicKeyB64;
        }
    }

    public static final class Blob {
        public final String epkB64;
        public final String sealedB64;

        public Blob(String epkB64, String sealedB64) {
            this.epkB64 = epkB64;
            this.sealedB64 = sealedB64;
        }
    }
}
