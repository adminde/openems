package io.openems.backend.metadata.odoo;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.time.Instant;

import org.junit.Test;

import com.google.gson.JsonObject;

import io.openems.common.session.Language;
import io.openems.common.session.Role;

public class UserCacheTest {

	private static MyUser newUser(String externalId, String login) {
		return new MyUser(1, externalId, login, "Name", "", Language.DEFAULT, Role.OWNER, false, new JsonObject());
	}

	@Test
	public void testKeyedByExternalId() {
		var cache = new UserCache();
		var user = newUser("sub-1", "alice@example.com");
		cache.addOrUpdate(user, Instant.now().plusSeconds(60));

		assertSame(user, cache.getUserFromExternalId("sub-1"));
		assertNull(cache.getUserFromExternalId("alice@example.com"));
		assertNull(cache.getUserFromExternalId("unknown"));
	}

	@Test
	public void testFreshRespectsExpiryWhileRegistryIgnoresIt() {
		var cache = new UserCache();
		var now = Instant.now();
		var user = newUser("sub-1", "alice@example.com");

		cache.addOrUpdate(user, now.minusSeconds(1));
		assertNull(cache.getUserFromExternalId("sub-1", now));
		// The registry read still returns the User, so an active session is not dropped.
		assertSame(user, cache.getUserFromExternalId("sub-1"));

		cache.addOrUpdate(user, now.plusSeconds(60));
		assertSame(user, cache.getUserFromExternalId("sub-1", now));
	}

	@Test
	public void testRemove() {
		var cache = new UserCache();
		var user = newUser("sub-1", "alice@example.com");
		cache.addOrUpdate(user, Instant.now().plusSeconds(60));

		cache.remove("sub-1");
		assertNull(cache.getUserFromExternalId("sub-1"));
		assertNull(cache.getUserFromExternalId("sub-1", Instant.now()));
	}

}
