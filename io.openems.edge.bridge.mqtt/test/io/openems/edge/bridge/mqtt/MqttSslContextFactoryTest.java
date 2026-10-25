package io.openems.edge.bridge.mqtt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcePEMEncryptorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.openems.common.exceptions.OpenemsException;

public class MqttSslContextFactoryTest {

	private static final String CA_SUBJECT = "CN=Test CA";
	private static final String CLIENT_SUBJECT = "CN=Test Client";
	private static final String PASSWORD = "secret";

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private KeyPair caKeyPair;
	private X509Certificate caCertificate;
	private KeyPair clientKeyPair;
	private X509Certificate clientCertificate;

	@Before
	public void setUp() throws Exception {
		this.caKeyPair = generateKeyPair();
		this.caCertificate = createCertificate(CA_SUBJECT, this.caKeyPair, CA_SUBJECT, this.caKeyPair.getPrivate());
		this.clientKeyPair = generateKeyPair();
		this.clientCertificate = createCertificate(CLIENT_SUBJECT, this.clientKeyPair, CA_SUBJECT,
				this.caKeyPair.getPrivate());
	}

	@Test
	public void testReadPemKeepsCertificateOrder() throws Exception {
		var chain = this.writePem("chain.pem", this.clientCertificate, this.caCertificate);

		var content = MqttSslContextFactory.readPem(chain, "");

		assertEquals(2, content.certificates().size());
		assertEquals(CLIENT_SUBJECT, content.certificates().get(0).getSubjectX500Principal().getName());
		assertEquals(CA_SUBJECT, content.certificates().get(1).getSubjectX500Principal().getName());
		assertNull(content.privateKey());
	}

	@Test
	public void testReadPemSkipsKeysWithoutPassword() throws Exception {
		var combined = this.writePem("client.pem", this.clientCertificate, this.clientKeyPair.getPrivate());

		var content = MqttSslContextFactory.readPem(combined, null);

		assertEquals(1, content.certificates().size());
		assertNull(content.privateKey());
	}

	@Test
	public void testReadPemTraditionalKey() throws Exception {
		var key = this.writePem("key.pem", this.clientKeyPair.getPrivate());

		this.assertSameKey(MqttSslContextFactory.readPem(key, "").privateKey());
	}

	@Test
	public void testReadPemPkcs8Key() throws Exception {
		var key = this.writePem("key.pem", new PemObject("PRIVATE KEY", this.clientKeyPair.getPrivate().getEncoded()));

		this.assertSameKey(MqttSslContextFactory.readPem(key, "").privateKey());
	}

	@Test
	public void testReadPemEncryptedKey() throws Exception {
		var key = this.writeEncryptedKey("key.pem", PASSWORD);

		this.assertSameKey(MqttSslContextFactory.readPem(key, PASSWORD).privateKey());
	}

	@Test
	public void testReadPemEncryptedKeyRejectsWrongPassword() throws Exception {
		var key = this.writeEncryptedKey("key.pem", PASSWORD);

		assertThrows(OpenemsException.class, () -> MqttSslContextFactory.readPem(key, "wrong"));
	}

	@Test
	public void testReadPemEncryptedKeyRequiresPassword() throws Exception {
		var key = this.writeEncryptedKey("key.pem", PASSWORD);

		var e = assertThrows(OpenemsException.class, () -> MqttSslContextFactory.readPem(key, ""));
		assertTrue(e.getMessage().contains("encrypted"));
	}

	@Test
	public void testIsPemDetectsFormat() throws Exception {
		var pem = this.writePem("ca.pem", this.caCertificate);
		var pkcs12 = this.writePkcs12TrustStore("trust.p12", PASSWORD);
		var jks = this.writeJksTrustStore("trust.jks");

		assertTrue(MqttSslContextFactory.isPem(pem));
		assertFalse(MqttSslContextFactory.isPem(pkcs12));
		assertFalse(MqttSslContextFactory.isPem(jks));
	}

	@Test
	public void testCreateSocketFactoryWithoutFilesUsesDefaults() throws Exception {
		var config = MyConfig.create().setSecureConnect(true).build();

		assertNotNull(MqttSslContextFactory.createSocketFactory(config));
	}

	@Test
	public void testCreateSocketFactoryWithThreePemFiles() throws Exception {
		var config = MyConfig.create() //
				.setSecureConnect(true) //
				.setCaCertificateFile(this.writePem("ca.pem", this.caCertificate).toString()) //
				.setClientCertificateFile(
						this.writePem("cert.pem", this.clientCertificate, this.caCertificate).toString()) //
				.setClientKeyFile(this.writePem("key.pem", this.clientKeyPair.getPrivate()).toString()) //
				.build();

		assertNotNull(MqttSslContextFactory.createSocketFactory(config));
	}

	@Test
	public void testCreateSocketFactoryWithCombinedPemFile() throws Exception {
		var config = MyConfig.create() //
				.setSecureConnect(true) //
				.setClientCertificateFile(
						this.writePem("client.pem", this.clientCertificate, this.clientKeyPair.getPrivate()).toString()) //
				.build();

		assertNotNull(MqttSslContextFactory.createSocketFactory(config));
	}

	@Test
	public void testCreateSocketFactoryWithEncryptedKeyFile() throws Exception {
		var config = MyConfig.create() //
				.setSecureConnect(true) //
				.setClientCertificateFile(this.writePem("cert.pem", this.clientCertificate).toString()) //
				.setClientKeyFile(this.writeEncryptedKey("key.pem", PASSWORD).toString()) //
				.setClientKeyPassword(PASSWORD) //
				.build();

		assertNotNull(MqttSslContextFactory.createSocketFactory(config));
	}

	@Test
	public void testCreateSocketFactoryWithJksAndPkcs12Stores() throws Exception {
		var config = MyConfig.create() //
				.setSecureConnect(true) //
				.setCaCertificateFile(this.writeJksTrustStore("trust.jks").toString()) //
				.setClientCertificateFile(this.writePkcs12KeyStore("client.p12", PASSWORD).toString()) //
				.setClientKeyPassword(PASSWORD) //
				.build();

		assertNotNull(MqttSslContextFactory.createSocketFactory(config));
	}

	@Test
	public void testCreateSocketFactoryWithProtectedPkcs12CaFile() throws Exception {
		var config = MyConfig.create() //
				.setSecureConnect(true) //
				.setCaCertificateFile(this.writePkcs12TrustStore("trust.p12", PASSWORD).toString()) //
				.setCaCertificatePassword(PASSWORD) //
				.build();

		assertNotNull(MqttSslContextFactory.createSocketFactory(config));
	}

	@Test
	public void testCreateSocketFactoryRejectsProtectedPkcs12CaFileWithoutPassword() throws Exception {
		var config = MyConfig.create() //
				.setSecureConnect(true) //
				.setCaCertificateFile(this.writePkcs12TrustStore("trust.p12", PASSWORD).toString()) //
				.build();

		var e = assertThrows(OpenemsException.class, () -> MqttSslContextFactory.createSocketFactory(config));
		assertTrue(e.getMessage().contains("CA certificate password"));
	}

	@Test
	public void testCreateSocketFactoryRejectsWrongCaCertificatePassword() throws Exception {
		var config = MyConfig.create() //
				.setSecureConnect(true) //
				.setCaCertificateFile(this.writePkcs12TrustStore("trust.p12", PASSWORD).toString()) //
				.setCaCertificatePassword("wrong") //
				.build();

		assertThrows(OpenemsException.class, () -> MqttSslContextFactory.createSocketFactory(config));
	}

	@Test
	public void testCreateSocketFactoryRejectsCertificateWithoutKey() throws Exception {
		var config = MyConfig.create() //
				.setSecureConnect(true) //
				.setClientCertificateFile(this.writePem("cert.pem", this.clientCertificate).toString()) //
				.build();

		var e = assertThrows(OpenemsException.class, () -> MqttSslContextFactory.createSocketFactory(config));
		assertTrue(e.getMessage().contains("client key file"));
	}

	@Test
	public void testCreateSocketFactoryRejectsKeyFileWithoutCertificate() throws Exception {
		var config = MyConfig.create() //
				.setSecureConnect(true) //
				.setClientKeyFile(this.writePem("key.pem", this.clientKeyPair.getPrivate()).toString()) //
				.build();

		assertThrows(OpenemsException.class, () -> MqttSslContextFactory.createSocketFactory(config));
	}

	@Test
	public void testCreateSocketFactoryRejectsKeyFileWithPkcs12Certificate() throws Exception {
		var config = MyConfig.create() //
				.setSecureConnect(true) //
				.setClientCertificateFile(this.writePkcs12KeyStore("client.p12", PASSWORD).toString()) //
				.setClientKeyFile(this.writePem("key.pem", this.clientKeyPair.getPrivate()).toString()) //
				.setClientKeyPassword(PASSWORD) //
				.build();

		assertThrows(OpenemsException.class, () -> MqttSslContextFactory.createSocketFactory(config));
	}

	@Test
	public void testCreateSocketFactoryReportsMissingFile() throws Exception {
		var missing = this.folder.getRoot().toPath().resolve("missing.pem");
		var config = MyConfig.create() //
				.setSecureConnect(true) //
				.setCaCertificateFile(missing.toString()) //
				.build();

		var e = assertThrows(OpenemsException.class, () -> MqttSslContextFactory.createSocketFactory(config));
		assertTrue(e.getMessage().contains(missing.toString()));
	}

	private void assertSameKey(PrivateKey privateKey) {
		assertNotNull(privateKey);
		assertEquals("EC", privateKey.getAlgorithm());
		assertEquals(((ECPrivateKey) this.clientKeyPair.getPrivate()).getS(), ((ECPrivateKey) privateKey).getS());
	}

	private Path writePem(String name, Object... objects) throws IOException {
		var file = this.folder.newFile(name).toPath();
		try (var writer = new JcaPEMWriter(Files.newBufferedWriter(file))) {
			for (var object : objects) {
				writer.writeObject(object);
			}
		}
		return file;
	}

	private Path writeEncryptedKey(String name, String password) throws IOException {
		var file = this.folder.newFile(name).toPath();
		var encryptor = new JcePEMEncryptorBuilder("AES-256-CBC") //
				.setProvider(new BouncyCastleProvider()) //
				.build(password.toCharArray());
		try (var writer = new JcaPEMWriter(Files.newBufferedWriter(file))) {
			writer.writeObject(this.clientKeyPair.getPrivate(), encryptor);
		}
		return file;
	}

	private Path writeJksTrustStore(String name) throws Exception {
		var store = KeyStore.getInstance("JKS");
		store.load(null, null);
		store.setCertificateEntry("ca", this.caCertificate);
		return this.storeToFile(store, name, PASSWORD);
	}

	private Path writePkcs12TrustStore(String name, String password) throws Exception {
		var store = KeyStore.getInstance("PKCS12");
		store.load(null, null);
		store.setCertificateEntry("ca", this.caCertificate);
		return this.storeToFile(store, name, password);
	}

	private Path writePkcs12KeyStore(String name, String password) throws Exception {
		var store = KeyStore.getInstance("PKCS12");
		store.load(null, null);
		store.setKeyEntry("client", this.clientKeyPair.getPrivate(), password.toCharArray(),
				new Certificate[] { this.clientCertificate, this.caCertificate });
		return this.storeToFile(store, name, password);
	}

	private Path storeToFile(KeyStore store, String name, String password) throws Exception {
		var file = this.folder.newFile(name).toPath();
		try (var out = Files.newOutputStream(file)) {
			store.store(out, password.toCharArray());
		}
		return file;
	}

	/**
	 * Generates the key pair with the BouncyCastle provider. The JDK provider omits
	 * the curve parameters from the SEC1 structure, which would render the
	 * traditional PEM encoding written by {@link JcaPEMWriter} unreadable.
	 *
	 * @return the generated key pair
	 */
	private static KeyPair generateKeyPair() throws Exception {
		var generator = KeyPairGenerator.getInstance("EC", new BouncyCastleProvider());
		generator.initialize(new ECGenParameterSpec("secp256r1"));
		return generator.generateKeyPair();
	}

	private static X509Certificate createCertificate(String subject, KeyPair keyPair, String issuer,
			PrivateKey issuerKey) throws Exception {
		var now = Instant.now();
		var builder = new JcaX509v3CertificateBuilder(new X500Name(issuer), BigInteger.valueOf(now.toEpochMilli()),
				Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(1, ChronoUnit.DAYS)),
				new X500Name(subject), keyPair.getPublic());
		var signer = new JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey);
		return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
	}

}
