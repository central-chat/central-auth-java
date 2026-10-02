# chat.central:central-auth

Mints [Central](https://central.chat) App Entry tokens: the JWT your backend
signs so the chat knows who just opened it. Server side only.

## Install

```xml
<dependency>
    <groupId>chat.central</groupId>
    <artifactId>central-auth</artifactId>
    <version>0.1.0</version>
</dependency>
```

Java ≥ 17.

## Use

```java
// The path to the certificate file you downloaded.
CentralAuth central = CentralAuth.fromFile(Path.of("./central-app-entry-<kid>.json"));

String token = central.mintToken(userId, Map.of("userName", userName));
```

The certificate file is the one you download when you generate your
business's App Entry certificate. Nothing in it is copied by hand.

`userId` is the identity Central logs in, so it must come from your own session
and never from the request: whoever can choose it signs in as that person.

The token is valid for 300 seconds, which is the protocol ceiling — mint one per
entry rather than caching it.

## Build

```sh
mvn package
```

`mvn deploy -Prelease` signs and uploads; see the folder README for what that
still needs.

## Try it

`examples/MintToken.java` mints one token so you can check a certificate without
editing anything — the path comes from the environment. It is not part of the
jar, so compile it against the built classes (use `;` instead of `:` on Windows):

```sh
mvn -q package dependency:build-classpath -Dmdep.outputFile=target/cp.txt
javac -cp "target/classes:$(cat target/cp.txt)" -d target/examples examples/MintToken.java
CENTRAL_APP_ENTRY_CERT=./central-app-entry-<kid>.json \
  java -cp "target/examples:target/classes:$(cat target/cp.txt)" MintToken
```

`CENTRAL_USER_ID` and `CENTRAL_USER_NAME` are optional.

## License

Apache-2.0
