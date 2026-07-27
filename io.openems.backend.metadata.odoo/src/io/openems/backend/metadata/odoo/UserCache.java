package io.openems.backend.metadata.odoo;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory cache of authenticated {@link MyUser}s, keyed by the external (e.g.
 * OpenID) user-id.
 *
 * <p>
 * Each entry carries an expiry {@link Instant}. It governs only whether
 * {@link #getUserFromExternalId(String, Instant)} still serves the entry;
 * {@link #getUserFromExternalId(String)} ignores it, so an active session is
 * never dropped just because its cached attributes are due for a refresh.
 *
 * <p>
 * Never resolve a User (blocking Odoo lookup) while holding this cache's lock:
 * callers requests first and hand the finished {@link MyUser} to
 * {@link #addOrUpdate(MyUser, Instant)}.
 */
public class UserCache {

	private record UserEntry(MyUser user, Instant expiresAt) {
	}

	private final Map<String, UserEntry> externalIdToUser = new HashMap<>();

	/**
	 * Adds or replaces the cache entry for a User.
	 *
	 * @param user      the {@link MyUser}
	 * @param expiresAt the {@link Instant} after which the entry is no longer served as fresh
	 * @return the passed User
	 */
	public synchronized MyUser addOrUpdate(MyUser user, Instant expiresAt) {
		this.externalIdToUser.put(user.getUserId(), new UserEntry(user, expiresAt));
		return user;
	}

	/**
	 * Gets a User from its external user-id, ignoring the expiry.
	 *
	 * @param externalId the external user-id
	 * @return the {@link MyUser}, or null if unknown
	 */
	public synchronized MyUser getUserFromExternalId(String externalId) {
		var entry = this.externalIdToUser.get(externalId);
		return entry == null ? null : entry.user();
	}

	/**
	 * Gets a User from its external user-id, but only if the entry has not expired.
	 *
	 * @param externalId the external user-id
	 * @param now        the current {@link Instant}
	 * @return the {@link MyUser}, or null if unknown or expired
	 */
	public synchronized MyUser getUserFromExternalId(String externalId, Instant now) {
		var entry = this.externalIdToUser.get(externalId);
		if (entry == null || !entry.expiresAt().isAfter(now)) {
			return null;
		}
		return entry.user();
	}

	/**
	 * Removes the cache entry for an external user-id.
	 *
	 * @param externalId the external user-id
	 */
	public synchronized void remove(String externalId) {
		this.externalIdToUser.remove(externalId);
	}

}
