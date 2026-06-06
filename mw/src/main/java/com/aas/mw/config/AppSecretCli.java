package com.aas.mw.config;

public final class AppSecretCli {

    private AppSecretCli() {
    }

    public static void main(String[] args) {
        if (args.length < 2 || !"encrypt".equalsIgnoreCase(args[0])) {
            System.err.println("Usage: java ... AppSecretCli encrypt <plaintext>");
            System.exit(1);
        }
        String masterKey = System.getenv(AppSecretCrypto.ENV_MASTER_KEY);
        try {
            AppSecretCrypto.validateMasterKey(masterKey);
            System.out.println(AppSecretCrypto.encrypt(args[1], masterKey));
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }
    }
}
