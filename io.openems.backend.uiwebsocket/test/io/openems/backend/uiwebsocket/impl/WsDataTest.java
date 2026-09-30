package io.openems.backend.uiwebsocket.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.Optional;

import org.junit.Test;

import io.openems.backend.common.test.DummyUser;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;

public class WsDataTest {

	private static final String TOKEN = "token";
	private static final String REFRESH_TOKEN = "refreshToken";

	@Test
	public void test() throws OpenemsNamedException {
		var sut = new WsData(null, 10);
		assertEquals(Optional.empty(), sut.getUser(null));
		assertEquals(Optional.empty(), sut.getRefreshToken());
		assertThrows(OpenemsNamedException.class, () -> sut.assertToken());
		assertEquals("UiWebsocket.WsData [userId=UNKNOWN, token=UNKNOWN]", sut.toLogString());

		sut.setToken(TOKEN);
		sut.setRefreshToken(REFRESH_TOKEN);
		sut.setUser(DummyUser.DUMMY_GUEST);

		assertEquals(Optional.of("guest"), sut.getUserId());
		assertEquals(Optional.of(TOKEN), sut.getToken());
		assertEquals(Optional.of(REFRESH_TOKEN), sut.getRefreshToken());
		assertEquals(TOKEN, sut.assertToken());
		assertEquals("UiWebsocket.WsData [userId=guest, token=token]", sut.toLogString());

		sut.logout();

		assertEquals(Optional.empty(), sut.getToken());
		assertEquals(Optional.empty(), sut.getRefreshToken());
		assertNull(sut.getUser());
	}

}
