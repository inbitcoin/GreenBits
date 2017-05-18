package com.greenaddress.greenapi;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.LazyECPoint;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.spongycastle.util.encoders.Base64;
import org.spongycastle.asn1.ASN1Integer;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.List;

public abstract class ISigningWallet {
    private static final String TAG = ISigningWallet.class.getSimpleName();

    protected static final int HARDENED = 0x80000000;

    public abstract boolean requiresPrevoutRawTxs(); // FIXME: Get rid of this

    public DeterministicKey getMyPublicKey(final int subAccount) {
        return getMyKey(subAccount).getPubKey();
    }
    protected abstract ISigningWallet getMyKey(final int subAccount);
    public abstract DeterministicKey getMyPublicKey(final int subAccount, final Integer pointer);
    public abstract List<byte[]> signTransaction(PreparedTransaction ptx);
    // FIXME: This is only needed until the challenge RPC is unified
    public abstract Object[] getChallengeArguments();
    public abstract String[] signChallenge(final String challengeString, final String[] challengePath);

    /**
     * Derived key from url and index
     * @param uri String callback uri
     * @param index Integer index, useful for multiple login on the same uri
     * @return DeterministicKey derived key
     * @throws IOException on possible IO exception during uri byte conversation
     */
    public abstract ISigningWallet getBitIdWallet(final String uri, final Integer index) throws IOException;

    public abstract ECKey.ECDSASignature signMessage(String message);
    protected abstract ISigningWallet derive(Integer childNumber);
    public abstract DeterministicKey getPubKey();

    public static byte[] getTxSignature(final ECKey.ECDSASignature sig) {
        final TransactionSignature txSig = new TransactionSignature(sig, Transaction.SigHash.ALL, false);
        return txSig.encodeToBitcoin();
    }

    public static ECKey.ECDSASignature base64ToECDSASignature(String value) {
        byte[] rawSignature = Base64.decode(value);
        byte[] conversion = new byte[32];
        // rawSignature.length == 1 + 32 + 32
        System.arraycopy(rawSignature, 1, conversion, 0, 32);
        BigInteger r = new BigInteger(conversion);
        System.arraycopy(rawSignature, 1 + 32, conversion, 0, 32);
        BigInteger s = new BigInteger(conversion);
        return new ECKey.ECDSASignature(r, s);
    }

    public static String ECDSASignatureToBase64(ECKey.ECDSASignature value, String message, ECKey pubkey) {
        byte[] data = Utils.formatMessageForSigning(message);
        Sha256Hash hash = Sha256Hash.twiceOf(data);
        final ASN1Integer r = new ASN1Integer(value.r);
        final ASN1Integer s = new ASN1Integer(value.s);
        final ECKey.ECDSASignature signature = new ECKey.ECDSASignature(
                r.getPositiveValue(), s.getPositiveValue());
        // Now we have to work backwards to figure out the recId needed to recover the signature.
        int recId = -1;
        LazyECPoint pub = new LazyECPoint(ECKey.CURVE.getCurve(), pubkey.getPubKey());

        for (int i = 0; i < 4; i++) {
            ECKey k = ECKey.recoverFromSignature(i, signature, hash, true/* isCompressed */);
            if(k == null) {
                continue;
            }
            LazyECPoint kPub = new LazyECPoint(ECKey.CURVE.getCurve(), k.getPubKey());
            if (kPub.equals(pub)) {
                recId = i;
                break;
            }
        }
        if (recId == -1) {
            // FIXME: assert?
            throw new RuntimeException("Could not construct a recoverable key. This should never happen.");
        }
        int headerByte = recId + 27 + 4 /* 4 -> isCompressed */;
        byte[] sigData = new byte[65];  // 1 header + 32 bytes for R + 32 bytes for S
        sigData[0] = (byte)headerByte;
        System.arraycopy(Utils.bigIntegerToBytes(signature.r, 32), 0, sigData, 1, 32);
        System.arraycopy(Utils.bigIntegerToBytes(signature.s, 32), 0, sigData, 33, 32);
        return new String(Base64.encode(sigData), Charset.forName("UTF-8"));
    }
}
