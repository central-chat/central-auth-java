package chat.central.auth;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static com.nimbusds.jose.util.JSONObjectUtils.parse;

/** Mints Central App Entry tokens from the key file the console downloads; the key is ES256, as the console generates it. */
public final class CentralAuth {

    private static final String AUDIENCE = "central:app-entry";

    /** Central refuses a token whose exp is more than 300s past its iat. */
    private static final int TTL_SECONDS = 300;

    private final Map<String, Object> bundle;

    private CentralAuth(final Map<String, Object> bundle) {
        for (final String field : new String[]{"kid", "alg", "iss", "privateJwk", "centralEncryptionKey"}) {
            if (bundle.get(field) == null) throw new IllegalArgumentException("CentralAuth bundle is missing " + field);
        }
        this.bundle = bundle;
    }
    /** Reads the certificate file the console downloaded. */
    public static CentralAuth fromFile(final Path path) throws Exception {
        return fromString(Files.readString(path));
    }

    /** Reads the certificate file's JSON content, e.g. from an env var or a secret manager. */
    public static CentralAuth fromString(final String json) throws Exception {
        return new CentralAuth(parse(json));
    }

    public String mintToken(final String userId) throws Exception {
        return mintToken(userId, Map.of());
    }

    /**
     * Mints the entry token for one person: a JWS signed with your key, wrapped in a JWE only Central can open.
     *
     * <p>{@code userId} is the identity Central logs in, so it must come from your own session and never from
     * the request; {@code claims} takes {@code userName}.
     */
    @SuppressWarnings("unchecked")
    public String mintToken(final String userId, final Map<String, Object> claims) throws Exception {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("mintToken needs a userId");

        final Object userName = claims == null ? null : claims.get("userName");

        final String kid = (String) bundle.get("kid");
        final String alg = (String) bundle.get("alg");
        final Map<String, Object> recipient = (Map<String, Object>) bundle.get("centralEncryptionKey");
        final Instant now = Instant.now();

        final JWTClaimsSet.Builder claimSet = new JWTClaimsSet.Builder()
                .issuer((String) bundle.get("iss"))
                .subject(userId)
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(TTL_SECONDS)))
                .claim("chat_account", kid);
        // `userName` travels as the `name` claim, which is what Central reads.
        if (userName != null) claimSet.claim("name", userName);

        final SignedJWT jws = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.parse(alg)).keyID(kid).type(JOSEObjectType.JWT).build(),
                claimSet.build());
        jws.sign(new ECDSASigner(JWK.parse((Map<String, Object>) bundle.get("privateJwk")).toECKey()));

        // bid/cid ride the JWE header so the embedded widget can route on the token
        // alone, without the page repeating the ids.
        final JWEObject jwe = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.parse((String) recipient.get("alg")), EncryptionMethod.A256GCM)
                        .contentType("JWT")
                        .keyID((String) recipient.get("kid"))
                        .customParams(Map.of("bid", bundle.get("iss"), "cid", kid))
                        .build(),
                new Payload(jws.serialize()));
        jwe.encrypt(new ECDHEncrypter((ECKey) JWK.parse((Map<String, Object>) recipient.get("publicJwk"))));
        return jwe.serialize();
    }
}
