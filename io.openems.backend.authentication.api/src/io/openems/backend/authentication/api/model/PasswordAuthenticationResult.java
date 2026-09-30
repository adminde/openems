package io.openems.backend.authentication.api.model;

/**
 * Result of a successful user authentication.
 *
 * <p>
 * The refreshToken is null when the authentication provider does not issue one,
 * for example for a plain session token.
 */
public record PasswordAuthenticationResult(String userId, String login, String token, String refreshToken) {

	public PasswordAuthenticationResult(String userId, String login, String token) {
		this(userId, login, token, null);
	}

}
