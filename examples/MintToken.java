// Mints one token, to check a certificate end to end without editing any code:
// the path comes from the environment. See the README for how to compile it
// against the built jar.
//
// CENTRAL_USER_ID and CENTRAL_USER_NAME are optional. The token prints to
// stdout and lasts 300 seconds.
import chat.central.auth.CentralAuth;

import java.nio.file.Path;
import java.util.Map;

public final class MintToken {
    public static void main(final String[] args) throws Exception {
        final String certPath = System.getenv("CENTRAL_APP_ENTRY_CERT");
        if (certPath == null || certPath.isBlank()) {
            System.err.println("Set CENTRAL_APP_ENTRY_CERT to the certificate file the console downloaded.");
            System.exit(1);
        }

        final CentralAuth central = CentralAuth.fromFile(Path.of(certPath));
        final String userName = System.getenv("CENTRAL_USER_NAME");

        System.out.println(central.mintToken(
                System.getenv().getOrDefault("CENTRAL_USER_ID", "example-user"),
                userName == null ? Map.of() : Map.of("userName", userName)));
    }
}
