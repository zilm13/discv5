package org.ethereum.discv5.util

import io.libp2p.core.crypto.PrivKey
import io.libp2p.crypto.keys.generateSecp256k1KeyPair
import org.bouncycastle.crypto.CryptoServicesRegistrar
import java.security.SecureRandom

val PUBKEY_SIZE_BITS = 256

class KeyUtils {
    companion object {

        fun genPrivKey(rnd: SecureRandom): PrivKey {
            if (rnd != CryptoServicesRegistrar.getSecureRandom()) {
                CryptoServicesRegistrar.setSecureRandom(rnd)
            }
            return generateSecp256k1KeyPair().first
        }

        /**
         * Produces exactly 256-bit compressed public key with recovery byte omitted if presented
         */
        fun privToPubCompressed(privKey: PrivKey): ByteArray {
            val pubKey = privKey.publicKey().raw()
            val pubkeySize = PUBKEY_SIZE_BITS / Byte.SIZE_BITS
            return when (pubKey.size) {
                pubkeySize -> pubKey
                in 0 until pubkeySize -> {
                    val res = ByteArray(pubkeySize)
                    System.arraycopy(pubKey, 0, res, pubkeySize - pubKey.size, pubKey.size)
                    res
                }
                in (pubkeySize + 1)..Int.MAX_VALUE -> pubKey.takeLast(pubkeySize).toByteArray()
                else -> throw RuntimeException("Not expected")
            }
        }
    }
}