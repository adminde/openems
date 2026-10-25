package io.openems.edge.bridge.mqtt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;

import io.openems.common.exceptions.OpenemsException;

/**
 * Builds the {@link SSLSocketFactory} for a TLS secured broker connection from
 * the certificate settings of a {@link Config}.
 *
 * <p>
 * The CA certificate file and the client certificate file may be PEM files or
 * PKCS12/JKS stores, detected from the file content. A PEM client certificate
 * file holds the certificate chain in leaf-first order and optionally the
 * private key, which may also come from a separate client key file. Without
 * any configured file the JVM defaults apply.
 */
public final class MqttSslContextFactory {

	/**
	 * Protects the in-memory key store that holds a PEM client identity. The store
	 * never leaves the process, so the value is irrelevant but must be non-empty
	 * for PKCS12.
	 */
	private static final char[] IN_MEMORY_STORE_PASSWORD = "openems".toCharArray();

	/**
	 * The certificates and the optional private key read from one PEM file.
	 */
	record PemContent(List<X509Certificate> certificates, PrivateKey privateKey) {
	}

	private MqttSslContextFactory() {
	}

	/**
	 * Creates a {@link SSLSocketFactory} according to the given configuration.
	 *
	 * @param config the bridge configuration
	 * @return the socket factory
	 * @throws OpenemsException if the configuration is inconsistent or a
	 *                          configured file cannot be loaded
	 */
	public static SSLSocketFactory createSocketFactory(Config config) throws OpenemsException {
		var trustManagers = createTrustManagers(config);
		var keyManagers = createKeyManagers(config);
		try {
			var sslContext = SSLContext.getInstance("TLS");
			sslContext.init(keyManagers, trustManagers, null);
			return sslContext.getSocketFactory();
		} catch (GeneralSecurityException e) {
			throw new OpenemsException("Unable to initialize TLS context: " + e.getMessage());
		}
	}

	/**
	 * Creates the {@link TrustManager}s for server verification.
	 *
	 * @param config the bridge configuration
	 * @return the trust managers or null to use the JVM defaults
	 * @throws OpenemsException if the CA certificate file cannot be loaded
	 */
	private static TrustManager[] createTrustManagers(Config config) throws OpenemsException {
		if (config.caCertificateFile().isBlank()) {
			return null;
		}
		var path = requireFile(config.caCertificateFile());
		try {
			KeyStore trustStore;
			if (isPem(path)) {
				trustStore = createEmptyKeyStore();
				var index = 0;
				for (var certificate : readPem(path, null).certificates()) {
					trustStore.setCertificateEntry("ca-" + index++, certificate);
				}
			} else {
				var password = config.caCertificatePassword();
				trustStore = loadKeyStore(path, password.isEmpty() ? null : password.toCharArray());
			}
			if (trustStore.size() == 0) {
				throw new OpenemsException("No certificate found in " + path
						+ ". A PKCS12 trust store with protected entries requires the CA certificate password");
			}
			var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			factory.init(trustStore);
			return factory.getTrustManagers();
		} catch (GeneralSecurityException | IOException e) {
			throw new OpenemsException("Unable to load CA certificate file " + path + ": " + e.getMessage());
		}
	}

	/**
	 * Creates the {@link KeyManager}s that present the client certificate.
	 *
	 * @param config the bridge configuration
	 * @return the key managers or null if no client certificate is configured
	 * @throws OpenemsException if the configuration is inconsistent or a file
	 *                          cannot be loaded
	 */
	private static KeyManager[] createKeyManagers(Config config) throws OpenemsException {
		var hasKeyFile = !config.clientKeyFile().isBlank();
		if (config.clientCertificateFile().isBlank()) {
			if (hasKeyFile) {
				throw new OpenemsException("A client key file requires a client certificate file");
			}
			return null;
		}
		var certificatePath = requireFile(config.clientCertificateFile());
		var password = config.clientKeyPassword();
		try {
			KeyStore keyStore;
			char[] keyPassword;
			if (isPem(certificatePath)) {
				var content = readPem(certificatePath, password);
				if (content.certificates().isEmpty()) {
					throw new OpenemsException("No certificate found in " + certificatePath);
				}
				var privateKey = hasKeyFile //
						? readPem(requireFile(config.clientKeyFile()), password).privateKey() //
						: content.privateKey();
				if (privateKey == null) {
					throw new OpenemsException(hasKeyFile //
							? "No private key found in " + config.clientKeyFile() //
							: "No private key found in " + certificatePath + ", configure a client key file");
				}
				keyStore = createEmptyKeyStore();
				keyStore.setKeyEntry("client", privateKey, IN_MEMORY_STORE_PASSWORD,
						content.certificates().toArray(new Certificate[0]));
				keyPassword = IN_MEMORY_STORE_PASSWORD;
			} else {
				if (hasKeyFile) {
					throw new OpenemsException(
							"A client key file cannot be combined with a PKCS12/JKS client certificate file");
				}
				keyStore = loadKeyStore(certificatePath, password.toCharArray());
				keyPassword = password.toCharArray();
			}
			var factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			factory.init(keyStore, keyPassword);
			return factory.getKeyManagers();
		} catch (GeneralSecurityException | IOException e) {
			throw new OpenemsException(
					"Unable to load client certificate file " + certificatePath + ": " + e.getMessage());
		}
	}

	/**
	 * Detects whether a certificate file is PEM encoded. PKCS12 files are DER
	 * encoded and start with a SEQUENCE tag, JKS files start with a fixed magic
	 * number. Any other content is treated as PEM text.
	 *
	 * @param path the file
	 * @return true for PEM, false for a binary key store
	 * @throws IOException if the file cannot be read
	 */
	static boolean isPem(Path path) throws IOException {
		try (var in = Files.newInputStream(path)) {
			var header = in.readNBytes(4);
			if (header.length == 0) {
				return true;
			}
			var isDer = (header[0] & 0xFF) == 0x30;
			var isJks = header.length == 4 //
					&& (header[0] & 0xFF) == 0xFE && (header[1] & 0xFF) == 0xED //
					&& (header[2] & 0xFF) == 0xFE && (header[3] & 0xFF) == 0xED;
			return !isDer && !isJks;
		}
	}

	/**
	 * Reads all X.509 certificates and the first private key from a PEM file.
	 * Supports PKCS#8 and traditional OpenSSL (RSA, EC, DSA) keys, plain or
	 * encrypted.
	 *
	 * @param path     the PEM file
	 * @param password the password for an encrypted key, empty for a plain key,
	 *                 null to skip private keys altogether
	 * @return the certificates in file order and the private key or null
	 * @throws OpenemsException if the file cannot be read or decrypted
	 */
	static PemContent readPem(Path path, String password) throws OpenemsException {
		var certificateConverter = new JcaX509CertificateConverter();
		var keyConverter = new JcaPEMKeyConverter();
		var certificates = new ArrayList<X509Certificate>();
		PrivateKey privateKey = null;
		try (var parser = new PEMParser(Files.newBufferedReader(path))) {
			Object object;
			while ((object = parser.readObject()) != null) {
				if (object instanceof X509CertificateHolder holder) {
					certificates.add(certificateConverter.getCertificate(holder));
				} else if (password != null && privateKey == null) {
					privateKey = toPrivateKey(object, keyConverter, password, path);
				}
			}
		} catch (IOException | GeneralSecurityException | OperatorCreationException | PKCSException e) {
			throw new OpenemsException("Unable to read PEM file " + path + ": " + e.getMessage());
		}
		return new PemContent(certificates, privateKey);
	}

	private static PrivateKey toPrivateKey(Object object, JcaPEMKeyConverter converter, String password, Path path)
			throws OpenemsException, IOException, OperatorCreationException, PKCSException {
		if (object instanceof PEMKeyPair keyPair) {
			return converter.getPrivateKey(keyPair.getPrivateKeyInfo());
		}
		if (object instanceof PrivateKeyInfo keyInfo) {
			return converter.getPrivateKey(keyInfo);
		}
		if (object instanceof PEMEncryptedKeyPair encrypted) {
			var decryptor = new JcePEMDecryptorProviderBuilder() //
					.setProvider(new BouncyCastleProvider()) //
					.build(requirePassword(password, path));
			return converter.getPrivateKey(encrypted.decryptKeyPair(decryptor).getPrivateKeyInfo());
		}
		if (object instanceof PKCS8EncryptedPrivateKeyInfo encrypted) {
			var decryptor = new JceOpenSSLPKCS8DecryptorProviderBuilder() //
					.setProvider(new BouncyCastleProvider()) //
					.build(requirePassword(password, path));
			return converter.getPrivateKey(encrypted.decryptPrivateKeyInfo(decryptor));
		}
		return null;
	}

	private static char[] requirePassword(String password, Path path) throws OpenemsException {
		if (password.isEmpty()) {
			throw new OpenemsException("Private key in " + path + " is encrypted but no password is configured");
		}
		return password.toCharArray();
	}

	private static Path requireFile(String configuredPath) throws OpenemsException {
		var path = Path.of(configuredPath);
		if (!Files.isRegularFile(path)) {
			throw new OpenemsException("File not found: " + path);
		}
		return path;
	}

	/**
	 * Loads a PKCS12 or JKS key store file. The default key store type reads both
	 * formats.
	 *
	 * @param path     the key store file
	 * @param password the key store password, null to skip the integrity check
	 * @return the loaded key store
	 * @throws GeneralSecurityException if the store cannot be decoded
	 * @throws IOException              if the file cannot be read or the password
	 *                                  is wrong
	 */
	private static KeyStore loadKeyStore(Path path, char[] password) throws GeneralSecurityException, IOException {
		var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		try (var in = Files.newInputStream(path)) {
			keyStore.load(in, password);
		}
		return keyStore;
	}

	private static KeyStore createEmptyKeyStore() throws GeneralSecurityException, IOException {
		var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, null);
		return keyStore;
	}

}
