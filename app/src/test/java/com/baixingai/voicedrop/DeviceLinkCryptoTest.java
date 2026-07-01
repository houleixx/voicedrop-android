package com.baixingai.voicedrop;

import com.baixingai.voicedrop.data.DeviceLinkCrypto;

import org.junit.Test;

import static org.junit.Assert.*;

public class DeviceLinkCryptoTest {
    @Test
    public void roundTripsAnonToken() throws Exception {
        DeviceLinkCrypto.Keypair receiver = DeviceLinkCrypto.newKeypair();
        DeviceLinkCrypto.Blob blob = DeviceLinkCrypto.encrypt("anon_roundtrip_demo", receiver.publicKeyB64);

        assertEquals("anon_roundtrip_demo", DeviceLinkCrypto.decrypt(blob.epkB64, blob.sealedB64, receiver.privateKeyB64));
    }
}
