package software.amazon.encryption.s3.materials;

import software.amazon.encryption.s3.algorithms.AlgorithmSuite;

public class DefaultMaterialsManager implements MaterialsManager {
    private final Keyring _keyring;


    public DefaultMaterialsManager(Keyring keyring) {
        _keyring = keyring;
    }

    public EncryptionMaterials getEncryptionMaterials(EncryptionMaterialsRequest request) {
        EncryptionMaterials materials = EncryptionMaterials.builder()
                .algorithmSuite(AlgorithmSuite.ALG_AES_256_GCM_IV12_TAG16_NO_KDF)
                .encryptionContext(request.encryptionContext())
                .build();

        return _keyring.onEncrypt(materials);
    }

    public DecryptionMaterials decryptMaterials(DecryptMaterialsRequest request) {
        DecryptionMaterials materials = DecryptionMaterials.builder()
                .algorithmSuite(request.algorithmSuite())
                .encryptionContext(request.encryptionContext())
                .build();

        return _keyring.onDecrypt(materials, request.encryptedDataKeys());
    }

}