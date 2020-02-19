package org.ethereum.discv5.util

import io.libp2p.core.crypto.PrivKey
import io.libp2p.core.crypto.PubKey
import io.libp2p.crypto.SECP_256K1_ALGORITHM
import io.libp2p.crypto.keys.Secp256k1PrivateKey
import io.libp2p.crypto.keys.Secp256k1PublicKey
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECKeyGenerationParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import java.security.SecureRandom

val PUBKEY_SIZE_BITS = 256

class KeyUtils {
    companion object {
        fun genPrivKey(rnd: SecureRandom): PrivKey {
            return generateSecp256k1KeyPair(rnd).first
        }

        private fun generateSecp256k1KeyPair(rnd: SecureRandom): Pair<PrivKey, PubKey> = with(ECKeyPairGenerator()) {
            val domain = SECNamedCurves.getByName(SECP_256K1_ALGORITHM).let {
                ECDomainParameters(it.curve, it.g, it.n, it.h)
            }
            init(ECKeyGenerationParameters(domain, rnd))
            val keypair = generateKeyPair()

            val privateKey = keypair.private as ECPrivateKeyParameters
            return Pair(
                Secp256k1PrivateKey(privateKey),
                Secp256k1PublicKey(keypair.public as ECPublicKeyParameters)
            )
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
