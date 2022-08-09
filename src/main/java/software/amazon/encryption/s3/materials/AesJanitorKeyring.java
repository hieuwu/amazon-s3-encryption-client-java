package software.amazon.encryption.s3.materials;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import software.amazon.encryption.s3.S3EncryptionClientException;
import software.amazon.encryption.s3.algorithms.AlgorithmSuite;

/**
 * This is the AES Janitor keyring because it can open many doors with one key
 */
public class AesJanitorKeyring extends S3JanitorKeyring {

    private static final String KEY_ALGORITHM = "AES";

    private static final DecryptDataKeyStrategy AES = new DecryptDataKeyStrategy() {
        @Override
        public boolean isLegacy() {
            return true;
        }

        @Override
        public String keyProviderId() {
            return "AES";
        }

        @Override
        public byte[] decryptDataKey(Key unwrappingKey, DecryptionMaterials materials, EncryptedDataKey encryptedDataKey) throws GeneralSecurityException {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, unwrappingKey);

            return cipher.doFinal(encryptedDataKey.ciphertext());
        }
    };

    private static final DecryptDataKeyStrategy AES_WRAP = new DecryptDataKeyStrategy() {
        @Override
        public boolean isLegacy() {
            return true;
        }

        @Override
        public String keyProviderId() {
            return "AESWrap";
        }

        @Override
        public byte[] decryptDataKey(Key unwrappingKey, DecryptionMaterials materials, EncryptedDataKey encryptedDataKey) throws GeneralSecurityException {
            final String cipherAlgorithm = "AESWrap";
            final Cipher cipher = Cipher.getInstance(cipherAlgorithm);
            cipher.init(Cipher.UNWRAP_MODE, unwrappingKey);

            Key plaintextKey = cipher.unwrap(encryptedDataKey.ciphertext(), cipherAlgorithm, Cipher.SECRET_KEY);
            return plaintextKey.getEncoded();
        }
    };

    private static final DataKeyStrategy AES_GCM = new DataKeyStrategy() {

        private static final String KEY_PROVIDER_ID = "AES/GCM";
        private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
        private static final int NONCE_LENGTH_BYTES = 12;
        private static final int TAG_LENGTH_BYTES = 16;
        private static final int TAG_LENGTH_BITS = TAG_LENGTH_BYTES * 8;

        @Override
        public boolean isLegacy() {
            return false;
        }

        @Override
        public String keyProviderId() {
            return KEY_PROVIDER_ID;
        }

        @Override
        public byte[] encryptDataKey(SecureRandom secureRandom, Key wrappingKey,
                EncryptionMaterials materials)
                throws GeneralSecurityException {
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, nonce);

            final Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, gcmParameterSpec, secureRandom);

            AlgorithmSuite algorithmSuite = materials.algorithmSuite();
            cipher.updateAAD(algorithmSuite.cipherName().getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(materials.plaintextDataKey());

            // The encrypted data key is the nonce prepended to the ciphertext
            byte[] encodedBytes = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, encodedBytes, 0, nonce.length);
            System.arraycopy(ciphertext, 0, encodedBytes, nonce.length, ciphertext.length);

            return encodedBytes;
        }

        @Override
        public byte[] decryptDataKey(Key unwrappingKey, DecryptionMaterials materials, EncryptedDataKey encryptedDataKey) throws GeneralSecurityException {
            byte[] encodedBytes = encryptedDataKey.ciphertext();
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            byte[] ciphertext = new byte[encodedBytes.length - nonce.length];

            System.arraycopy(encodedBytes, 0, nonce, 0, nonce.length);
            System.arraycopy(encodedBytes, nonce.length, ciphertext, 0, ciphertext.length);

            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, nonce);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, unwrappingKey, gcmParameterSpec);

            AlgorithmSuite algorithmSuite = materials.algorithmSuite();
            cipher.updateAAD(algorithmSuite.cipherName().getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(ciphertext);
        }
    };

    private static final Map<String, DecryptDataKeyStrategy> DECRYPT_STRATEGIES = new HashMap<>();
    static {
        DECRYPT_STRATEGIES.put(AES.keyProviderId(), AES);
        DECRYPT_STRATEGIES.put(AES_WRAP.keyProviderId(), AES_WRAP);
        DECRYPT_STRATEGIES.put(AES_GCM.keyProviderId(), AES_GCM);
    }

    private final SecretKey _wrappingKey;

    private AesJanitorKeyring(Builder builder) {
        super(builder);
        _wrappingKey = builder._wrappingKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected EncryptDataKeyStrategy encryptStrategy() {
        return AES_GCM;
    }

    @Override
    protected Key wrappingKey() {
        return _wrappingKey;
    }

    @Override
    protected Map<String, DecryptDataKeyStrategy> decryptStrategies() {
        return DECRYPT_STRATEGIES;
    }

    @Override
    protected Key unwrappingKey() {
        return _wrappingKey;
    }

    public static class Builder extends S3JanitorKeyring.Builder<S3JanitorKeyring> {
        private SecretKey _wrappingKey;

        private Builder() {
            super();
        }

        public Builder wrappingKey(SecretKey wrappingKey) {
            if (!wrappingKey.getAlgorithm().equals(KEY_ALGORITHM)) {
                throw new S3EncryptionClientException("Invalid algorithm: " + wrappingKey.getAlgorithm() + ", expecting " + KEY_ALGORITHM);
            }
            _wrappingKey = wrappingKey;
            return this;
        }

        public AesJanitorKeyring build() {
            return new AesJanitorKeyring(this);
        }
    }
}
