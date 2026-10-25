package io.openems.backend.authentication.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.openems.common.bridge.http.api.BridgeHttp;
import io.openems.common.bridge.http.api.HttpError;
import io.openems.common.bridge.http.api.HttpMethod;
import io.openems.common.bridge.http.api.HttpResponse;
import io.openems.common.bridge.http.api.UrlBuilder;
import io.openems.common.bridge.http.dummy.DummyBridgeHttpBundle;
import io.openems.common.types.HttpStatus;

public class KeycloakApiTest {

	private static final String ISSUER_URL = "https://auth.example.org/realms/test";

	@Test
	public void testLogout() throws Exception {
		final var testBundle = DummyBridgeHttpBundle.of();
		final var bridge = testBundle.bridgeFactory().get();

		final var called = new AtomicReference<BridgeHttp.Endpoint>();
		testBundle.fetcher().addSingleUseEndpointHandler(endpoint -> {
			called.set(endpoint);
			return new HttpResponse<>(HttpStatus.NO_CONTENT, null);
		});

		KeycloakApi.logout(bridge, ISSUER_URL, "client", "secret", "refresh").get();

		final var endpoint = called.get();
		assertEquals(ISSUER_URL + "/protocol/openid-connect/logout", endpoint.url());
		assertEquals(HttpMethod.POST, endpoint.method());
		assertEquals(Map.of(//
				"client_id", "client", //
				"client_secret", "secret", //
				"refresh_token", "refresh"), UrlBuilder.decodeFormUrlencodedBody(endpoint.body()));
	}

	@Test
	public void testLogoutRejected() {
		final var testBundle = DummyBridgeHttpBundle.of();
		final var bridge = testBundle.bridgeFactory().get();

		testBundle.forceNextFailedResult(new HttpError.ResponseError(HttpStatus.BAD_REQUEST,
				"{\"error\":\"invalid_grant\",\"error_description\":\"Session not active\"}"));

		final var e = assertThrows(ExecutionException.class,
				() -> KeycloakApi.logout(bridge, ISSUER_URL, "client", "secret", "refresh").get());
		assertInstanceOf(HttpError.ResponseError.class, e.getCause());
	}

}
