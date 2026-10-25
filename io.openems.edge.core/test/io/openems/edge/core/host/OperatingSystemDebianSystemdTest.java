package io.openems.edge.core.host;

import static io.openems.common.utils.JsonUtils.prettyToString;
import static io.openems.edge.core.host.OperatingSystemDebianSystemd.parseNmcliConnection;
import static io.openems.edge.core.host.OperatingSystemDebianSystemd.parseNmcliConnectionList;
import static io.openems.edge.core.host.OperatingSystemDebianSystemd.parseSystemdNetworkdConfigurationFile;
import static io.openems.edge.core.host.OperatingSystemDebianSystemd.toNetworkManagerFileFormat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.ConfigurationProperty;
import io.openems.edge.common.test.DummyUser;
import io.openems.edge.core.host.NetworkInterface.IpMasqueradeSetting;

public class OperatingSystemDebianSystemdTest {

	/*
	 * The following tests parse equivalent systemd-networkd and NetworkManager
	 * configurations and assert that both backends produce the same
	 * NetworkInterface. parseSystemdNetworkdConfigurationFile is the reference.
	 */

	@Test
	public void testDhcpWithLinkLocal() throws OpenemsNamedException {
		var nd = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=yes
				LinkLocalAddressing=yes
				""".lines().toList(), null);
		var nm = parseNmcliConnection("""
				connection.id:eth0
				connection.interface-name:eth0
				connection.type:802-3-ethernet
				ipv4.method:auto
				ipv4.addresses:
				ipv4.gateway:
				ipv4.dns:
				ipv4.route-metric:-1
				""".lines().toList(), null);

		for (var n : List.of(nd, nm)) {
			assertEquals("eth0", n.getName());
			assertTrue(n.getDhcp().getValue());
			assertTrue(n.getLinkLocalAddressing().getValue());
		}
	}

	@Test
	public void testStaticSingleAddressWithGateway() throws OpenemsNamedException {
		var nd = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=no
				LinkLocalAddressing=yes
				Gateway=192.168.100.1

				[Address]
				Address=192.168.100.100/24
				""".lines().toList(), null);
		var nm = parseNmcliConnection("""
				connection.id:eth0
				connection.interface-name:eth0
				connection.type:802-3-ethernet
				ipv4.method:manual
				ipv4.addresses:192.168.100.100/24
				ipv4.gateway:192.168.100.1
				ipv4.dns:
				ipv4.route-metric:-1
				""".lines().toList(), null);

		for (var n : List.of(nd, nm)) {
			assertEquals("eth0", n.getName());
			assertFalse(n.getDhcp().getValue());
			assertEquals(Set.of("192.168.100.100/24"), addressStrings(n));
			assertEquals("192.168.100.1", n.getGateway().getValue().getHostAddress());
		}
	}

	@Test
	public void testStaticMultipleAddresses() throws OpenemsNamedException {
		var nd = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=no
				LinkLocalAddressing=yes
				Address=192.168.100.100/24
				Address=10.4.0.1/24
				Gateway=10.4.0.2
				""".lines().toList(), null);
		var nm = parseNmcliConnection("""
				connection.id:eth0
				connection.interface-name:eth0
				connection.type:802-3-ethernet
				ipv4.method:manual
				ipv4.addresses:192.168.100.100/24, 10.4.0.1/24
				ipv4.gateway:10.4.0.2
				ipv4.dns:
				ipv4.route-metric:-1
				""".lines().toList(), null);

		for (var n : List.of(nd, nm)) {
			assertEquals("eth0", n.getName());
			assertFalse(n.getDhcp().getValue());
			assertEquals(Set.of("192.168.100.100/24", "10.4.0.1/24"), addressStrings(n));
			assertEquals("10.4.0.2", n.getGateway().getValue().getHostAddress());
		}
	}

	@Test
	public void testLinkLocalOnly() throws OpenemsNamedException {
		var nd = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=no
				LinkLocalAddressing=yes
				""".lines().toList(), null);
		var nm = parseNmcliConnection("""
				connection.id:eth0
				connection.interface-name:eth0
				connection.type:802-3-ethernet
				ipv4.method:link-local
				ipv4.addresses:
				ipv4.gateway:
				ipv4.dns:
				ipv4.route-metric:-1
				""".lines().toList(), null);

		for (var n : List.of(nd, nm)) {
			assertEquals("eth0", n.getName());
			assertFalse(n.getDhcp().getValue());
			assertTrue(n.getLinkLocalAddressing().getValue());
		}
	}

	@Test
	public void testDhcpWithCustomRouteMetric() throws OpenemsNamedException {
		var nd = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=yes

				[DHCP]
				RouteMetric=216
				""".lines().toList(), null);
		var nm = parseNmcliConnection("""
				connection.id:eth0
				connection.interface-name:eth0
				connection.type:802-3-ethernet
				ipv4.method:auto
				ipv4.addresses:
				ipv4.gateway:
				ipv4.dns:
				ipv4.route-metric:216
				""".lines().toList(), null);

		for (var n : List.of(nd, nm)) {
			assertEquals("eth0", n.getName());
			assertTrue(n.getDhcp().getValue());
			assertEquals(216, n.getDhcpRouteMetric().getValue().intValue());
		}
	}

	@Test
	public void testStaticWithDnsAndMetric() throws OpenemsNamedException {
		var nd = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=no
				DNS=10.0.0.1
				LinkLocalAddressing=yes
				Gateway=10.0.10.10
				Address=10.4.0.1/16

				[DHCP]
				RouteMetric=520
				""".lines().toList(), null);
		var nm = parseNmcliConnection("""
				connection.id:eth0
				connection.interface-name:eth0
				connection.type:802-3-ethernet
				ipv4.method:manual
				ipv4.addresses:10.4.0.1/16
				ipv4.gateway:10.0.10.10
				ipv4.dns:10.0.0.1
				ipv4.route-metric:520
				""".lines().toList(), null);

		for (var n : List.of(nd, nm)) {
			assertFalse(n.getDhcp().getValue());
			assertEquals("10.0.0.1", n.getDns().getValue().getHostAddress());
			assertEquals("10.0.10.10", n.getGateway().getValue().getHostAddress());
			assertEquals(520, n.getDhcpRouteMetric().getValue().intValue());
			assertEquals(Set.of("10.4.0.1/16"), addressStrings(n));
		}
	}

	/*
	 * NetworkManager-specific tests.
	 */

	@Test
	public void testNmcliLabelsFirstManualAddressAsStatic() throws OpenemsNamedException {
		final var n = parseNmcliConnection("""
				connection.id:eth1
				connection.interface-name:eth1
				connection.type:802-3-ethernet
				ipv4.method:manual
				ipv4.addresses:192.168.1.1/24, 10.4.0.1/24
				ipv4.gateway:
				ipv4.dns:
				ipv4.route-metric:1024
				""".lines().toList(), null);

		assertEquals("static", addressLabel(n, "192.168.1.1/24"));
		assertEquals("", addressLabel(n, "10.4.0.1/24"));
	}

	@Test
	public void testNmcliKeepsAddressesUnlabeledWithDhcp() throws OpenemsNamedException {
		final var n = parseNmcliConnection("""
				connection.id:eth0
				connection.interface-name:eth0
				connection.type:802-3-ethernet
				ipv4.method:auto
				ipv4.addresses:10.4.0.1/24
				ipv4.gateway:
				ipv4.dns:
				ipv4.route-metric:-1
				""".lines().toList(), null);

		assertEquals("", addressLabel(n, "10.4.0.1/24"));
	}

	@Test
	public void testNetworkManagerFileWritesStaticAddressFirst() throws OpenemsNamedException {
		final var addresses = new LinkedHashSet<Inet4AddressWithSubnetmask>();
		addresses.add(Inet4AddressWithSubnetmask.fromString("", "10.4.0.1/24"));
		addresses.add(Inet4AddressWithSubnetmask.fromString("static", "192.168.1.1/24"));
		final var iface = new NetworkInterface<>("eth1", //
				ConfigurationProperty.of(false), //
				ConfigurationProperty.of(false), //
				ConfigurationProperty.asNotSet(), //
				ConfigurationProperty.asNotSet(), //
				ConfigurationProperty.of(addresses), //
				ConfigurationProperty.asNotSet(), //
				ConfigurationProperty.asNotSet(), //
				ConfigurationProperty.asNotSet(), //
				ConfigurationProperty.asNotSet(), //
				ConfigurationProperty.asNotSet(), //
				ConfigurationProperty.asNotSet(), //
				null);

		final var lines = toNetworkManagerFileFormat(DummyUser.DUMMY_INSTALLER, iface);

		assertTrue(lines.contains("method=manual"));
		assertTrue(lines.contains("address1=192.168.1.1/24"));
		assertTrue(lines.contains("address2=10.4.0.1/24"));
	}

	@Test
	public void testNetworkManagerFileKeepsAdditionalAddressesWithDhcp() throws OpenemsNamedException {
		final var iface = new NetworkInterface<>("eth0", //
				ConfigurationProperty.of(true), //
				ConfigurationProperty.of(true), //
				ConfigurationProperty.asNotSet(), //
				ConfigurationProperty.asNotSet(), //
				ConfigurationProperty.of(Set.of(Inet4AddressWithSubnetmask.fromString("", "10.4.0.1/24"))), //
				ConfigurationProperty.asNotSet(), //
				ConfigurationProperty.asNotSet(), //
				ConfigurationProperty.asNotSet(), //
				ConfigurationProperty.asNotSet(), //
				ConfigurationProperty.asNotSet(), //
				ConfigurationProperty.asNotSet(), //
				null);

		final var lines = toNetworkManagerFileFormat(DummyUser.DUMMY_INSTALLER, iface);

		assertTrue(lines.contains("method=auto"));
		assertTrue(lines.contains("address1=10.4.0.1/24"));
	}

	@Test
	public void testNmcliTakesFirstDnsEntry() throws OpenemsNamedException {
		final var n = parseNmcliConnection("""
				connection.id:eth0
				connection.interface-name:eth0
				connection.type:802-3-ethernet
				ipv4.method:manual
				ipv4.addresses:192.168.100.100/24
				ipv4.gateway:192.168.100.1
				ipv4.dns:8.8.8.8,1.1.1.1
				ipv4.route-metric:-1
				""".lines().toList(), null);

		assertEquals("8.8.8.8", n.getDns().getValue().getHostAddress());
	}

	@Test
	public void testNmcliLeavesDefaultRouteMetricUnset() throws OpenemsNamedException {
		final var n = parseNmcliConnection("""
				connection.id:eth0
				connection.interface-name:eth0
				connection.type:802-3-ethernet
				ipv4.method:auto
				ipv4.addresses:
				ipv4.gateway:
				ipv4.dns:
				ipv4.route-metric:-1
				""".lines().toList(), null);

		assertFalse(n.getDhcpRouteMetric().isSet());
		assertFalse(n.getGateway().isSet());
		assertFalse(n.getDns().isSet());
		assertFalse(n.getAddresses().isSet());
	}

	@Test
	public void testNmcliSkipsNonEthernetConnections() throws OpenemsNamedException {
		final var lines = """
				connection.id:WLAN
				connection.interface-name:wlan0
				connection.type:802-11-wireless
				ipv4.method:auto
				ipv4.addresses:
				ipv4.gateway:
				ipv4.dns:
				ipv4.route-metric:-1
				""".lines().toList();

		assertNull(parseNmcliConnection(lines, null));
	}

	@Test
	public void testNmcliFallsBackToIdWhenInterfaceNameMissing() throws OpenemsNamedException {
		final var n = parseNmcliConnection("""
				connection.id:eth0
				connection.interface-name:
				connection.type:802-3-ethernet
				ipv4.method:auto
				ipv4.addresses:
				ipv4.gateway:
				ipv4.dns:
				ipv4.route-metric:-1
				""".lines().toList(), null);

		assertEquals("eth0", n.getName());
	}

	@Test
	public void testNmcliConnectionList() {
		final var connections = parseNmcliConnectionList("""
				eth0:0b1f6e3c-1111-4a2b-9c3d-000000000001:802-3-ethernet:/etc/NetworkManager/system-connections/eth0.nmconnection
				Wired connection 1:0b1f6e3c-1111-4a2b-9c3d-000000000002:802-3-ethernet:/run/NetworkManager/system-connections/Wired connection 1.nmconnection
				lo:0b1f6e3c-1111-4a2b-9c3d-000000000003:loopback:
				""".lines().toList());

		assertEquals(3, connections.size());

		final var eth0 = connections.get(0);
		assertEquals("eth0", eth0.name());
		assertEquals("0b1f6e3c-1111-4a2b-9c3d-000000000001", eth0.uuid());
		assertTrue(eth0.isEthernet());
		assertEquals(new File("/etc/NetworkManager/system-connections/eth0.nmconnection"), eth0.filename());

		final var wired = connections.get(1);
		assertEquals("Wired connection 1", wired.name());
		assertTrue(wired.isEthernet());
		assertEquals(new File("/run/NetworkManager/system-connections/Wired connection 1.nmconnection"),
				wired.filename());

		final var lo = connections.get(2);
		assertEquals("lo", lo.name());
		assertFalse(lo.isEthernet());
		assertNull(lo.filename());
	}

	@Test
	public void testNmcliConnectionListUnescapesColons() {
		// nmcli escapes colons inside values with a backslash in terse mode
		final var connections = parseNmcliConnectionList(List.of(//
				"eth0:0b1f6e3c-1111-4a2b-9c3d-000000000001:802-3-ethernet:/etc/NetworkManager/system-connections/a\\:b.nmconnection"));

		assertEquals(1, connections.size());
		assertEquals(new File("/etc/NetworkManager/system-connections/a:b.nmconnection"),
				connections.get(0).filename());
	}

	/*
	 * systemd-networkd-specific tests.
	 */

	@Test
	public void testSystemdNetworkdPreservesAddressLabel() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=yes
				LinkLocalAddressing=yes

				[Address]
				Address=192.168.100.100/24
				Label=normal
				""".lines().toList(), null);

		assertEquals("eth0", n.getName());
		assertEquals(true, n.getDhcp().getValue());
		assertEquals(true, n.getLinkLocalAddressing().getValue());
		assertEquals("192.168.100.100/24", n.getAddresses().getValue().toArray()[0].toString());

		assertEquals("""
				{
				  "dhcp": true,
				  "linkLocalAddressing": true,
				  "addresses": [
				    {
				      "label": "normal",
				      "address": "192.168.100.100",
				      "subnetmask": "255.255.255.0"
				    }
				  ]
				}""", prettyToString(n.toJson()));

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdMultipleAddressesWithMixedLabels() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=yes
				LinkLocalAddressing=yes

				[Address]
				Address=192.168.100.100/24
				Label=normal

				[Address]
				Address=192.168.123.123/24
				Label=
				""".lines().toList(), null);

		assertEquals("eth0", n.getName());
		assertEquals(true, n.getDhcp().getValue());
		assertEquals(true, n.getLinkLocalAddressing().getValue());
		{
			var address = (Inet4AddressWithSubnetmask) n.getAddresses().getValue().toArray()[0];
			assertEquals("192.168.100.100/24", address.toString());
			assertEquals("normal", address.getLabel());
		}
		{
			var address = (Inet4AddressWithSubnetmask) n.getAddresses().getValue().toArray()[1];
			assertEquals("192.168.123.123/24", address.toString());
			assertEquals("", address.getLabel());
		}

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdLabelBeforeAddressIsIgnored() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=yes
				LinkLocalAddressing=yes

				[Address]
				Address=192.168.100.100/24
				Label=fallback

				[Address]
				Label=foo
				Address=192.168.123.123/24
				""".lines().toList(), null);
		{
			var address = (Inet4AddressWithSubnetmask) n.getAddresses().getValue().toArray()[0];
			assertEquals("192.168.100.100/24", address.toString());
			assertEquals("fallback", address.getLabel());
		}
		{
			var address = (Inet4AddressWithSubnetmask) n.getAddresses().getValue().toArray()[1];
			assertEquals("192.168.123.123/24", address.toString());
			assertEquals("", address.getLabel()); // NOTE: if Label is before Address, it is ignored
		}

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdWildcardInterfaceName() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=enx*

				[Network]
				DHCP=yes
				""".lines().toList(), null);

		assertEquals("enx*", n.getName());
		assertEquals(true, n.getDhcp().getValue());

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdStaticIpWithRouteGateway() throws OpenemsNamedException {
		// Addresses are declared in [Network], but the Gateway lives in a separate
		// [Route] block and no Metric is set.
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=no
				LinkLocalAddressing=yes
				Address=192.168.100.100/24
				Address=10.4.0.1/24

				[Route]
				Gateway=10.4.0.2
				""".lines().toList(), null);

		assertEquals("eth0", n.getName());
		assertEquals(false, n.getDhcp().getValue());
		assertEquals(true, n.getLinkLocalAddressing().getValue());
		assertEquals("192.168.100.100/24", n.getAddresses().getValue().toArray()[0].toString());
		assertEquals("10.4.0.1/24", n.getAddresses().getValue().toArray()[1].toString());
		var currentRoutes = new HashSet<>(n.getRoutes().getValue());
		assertTrue(currentRoutes.stream().anyMatch(t -> Objects.equals(t.getRouteGateway(), "10.4.0.2")));
		assertTrue(currentRoutes.stream().anyMatch(t -> Objects.equals(t.getRouteMetric(), null)));

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdStaticIpWithNetworkGateway() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=no
				LinkLocalAddressing=yes
				Address=192.168.100.100/24
				Address=10.4.0.1/24
				Gateway=10.4.0.2
				""".lines().toList(), null);

		assertEquals("eth0", n.getName());
		assertEquals(false, n.getDhcp().getValue());
		assertEquals(true, n.getLinkLocalAddressing().getValue());
		assertEquals("192.168.100.100/24", n.getAddresses().getValue().toArray()[0].toString());
		assertEquals("10.4.0.1/24", n.getAddresses().getValue().toArray()[1].toString());
		assertEquals("10.4.0.2", n.getGateway().getValue().getHostAddress());

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdDhcpRouteMetric() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=yes

				[DHCP]
				RouteMetric=216
				""".lines().toList(), null);

		assertEquals("eth0", n.getName());
		assertEquals(true, n.getDhcp().getValue());
		assertEquals(216, n.getDhcpRouteMetric().getValue().intValue());
	}

	@Test
	public void testSystemdNetworkdRouteOnlyWithGatewayAndMetric() throws OpenemsNamedException {
		// Files without a [Match] block are accepted by the parser; the resulting
		// NetworkInterface has a null name. The caller (readNetworkdConfig) would
		// normally reject this via TypeUtils.assertNull.
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Network]
				DHCP=no
				DNS=10.0.0.1
				LinkLocalAddressing=yes

				[Route]
				Gateway=10.0.10.10
				Metric=520

				[Address]
				Address=10.4.0.1/16
				""".lines().toList(), null);

		assertNull(n.getName());
		assertEquals(false, n.getDhcp().getValue());
		var currentRoutes = new HashSet<>(n.getRoutes().getValue());
		assertTrue(currentRoutes.stream().anyMatch(t -> Objects.equals(t.getRouteGateway(), "10.0.10.10")));
		assertTrue(currentRoutes.stream().anyMatch(t -> Objects.equals(t.getRouteMetric(), 520)));

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdMultipleRouteSections() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth1

				[Network]
				DHCP=no
				Address=172.23.21.1/24
				Address=192.168.0.11/24

				[Route]
				Gateway=172.23.21.254
				Destination=172.23.22.0/24
				GatewayOnLink=yes

				[Route]
				Gateway=172.23.21.254
				Destination=172.23.23.0/24
				GatewayOnLink=yes

				[Route]
				Gateway=172.23.21.254
				Destination=172.23.24.0/24
				GatewayOnLink=yes
				""".lines().toList(), null);

		assertEquals(false, n.getDhcp().getValue());
		var currentRoutes = new HashSet<>(n.getRoutes().getValue());
		var destinations = currentRoutes.stream()//
				.map(Routes::getRouteDestination)//
				.filter(Objects::nonNull)//
				.collect(Collectors.toSet());
		assertEquals(//
				Set.of("172.23.23.0/24", "172.23.24.0/24", "172.23.22.0/24"), destinations);

		assertTrue(currentRoutes.stream().anyMatch(t -> Objects.equals(t.getRouteGateway(), "172.23.21.254")));
		assertTrue(currentRoutes.stream().anyMatch(t -> Objects.equals(t.isRouteGatewayOnLink(), true)));

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth1", json).toJson());
	}

	@Test
	public void testSystemdNetworkdGatewayDestinationAndGatewayOnLink() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth0

				[Network]
				DHCP=no
				LinkLocalAddressing=yes
				Address=192.168.100.100/24
				Address=172.23.20.1/24
				Destination=0.0.0.0/0
				GatewayOnLink=yes
				Gateway=172.23.20.254
				""".lines().toList(), null);

		assertEquals(false, n.getDhcp().getValue());
		assertEquals(true, n.getLinkLocalAddressing().getValue());
		assertEquals(//
				Set.of(//
						Inet4AddressWithSubnetmask.fromString("", "0.0.0.0/0")),
				n.getDestination().getValue());
		assertEquals(true, n.getGatewayOnLink().getValue());
		assertEquals("172.23.20.254", n.getGateway().getValue().getHostAddress());

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth0", json).toJson());
	}

	@Test
	public void testSystemdNetworkdDhcpRouteMetricEth2() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile("""
				[Match]
				Name=eth2

				[Network]
				DHCP=yes

				[DHCP]
				RouteMetric=1024

				[Address]
				Address=172.25.21.1/24
				""".lines().toList(), null);

		assertEquals(true, n.getDhcp().getValue());
		assertEquals(1024, n.getDhcpRouteMetric().getValue().intValue());

		var json = n.toJson();
		assertEquals(json, NetworkInterface.from("eth2", json).toJson());
	}

	@Test
	public void testSystemdNetworkdIpv4Forwarding() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile(List.of(//
				"[Match]", //
				"Name=eth0", //
				"", //
				"[Network]", //
				"IPv4Forwarding=yes" //
		), null);

		assertEquals("eth0", n.getName());
		assertTrue(n.getIpv4Forwarding().getValue());
	}

	@Test
	public void testSystemdNetworkdIpMasquerade() throws OpenemsNamedException {
		final var n = parseSystemdNetworkdConfigurationFile(List.of(//
				"[Match]", //
				"Name=eth0", //
				"", //
				"[Network]", //
				"IPMasquerade=ipv4" //
		), null);

		assertEquals("eth0", n.getName());
		assertEquals(IpMasqueradeSetting.IP_V4, n.getIpMasquerade().getValue());
	}

	@Test
	public void testUpdateFromAppliesChangedFields() throws OpenemsNamedException {
		var n1 = parseSystemdNetworkdConfigurationFile(Lists.newArrayList(//
				"[Match]", //
				"Name=eth0", //
				"", //
				"[Network]", //
				"DHCP=yes", //
				"LinkLocalAddressing=yes", //
				"", //
				"[Address]", //
				"Address=192.168.100.100/24", //
				"Label=normal" //
		), null);

		assertTrue(n1.getDhcp().getValue());

		var n2 = parseSystemdNetworkdConfigurationFile(Lists.newArrayList(//
				"[Match]", //
				"Name=eth0", //
				"", //
				"[Network]", //
				"DHCP=no", //
				"LinkLocalAddressing=yes", //
				"IPv4Forwarding=yes", //
				"IPMasquerade=ipv4", //
				"", //
				"[Address]", //
				"Address=192.168.100.100/24", //
				"Label=normal" //
		), null);

		assertTrue(n1.updateFrom(n2));

		assertFalse(n1.getDhcp().getValue());
		assertTrue(n1.getIpv4Forwarding().getValue());
		assertEquals(IpMasqueradeSetting.IP_V4, n1.getIpMasquerade().getValue());
	}

	private static String addressLabel(NetworkInterface<?> n, String address) {
		return n.getAddresses().getValue().stream() //
				.filter(a -> a.toString().equals(address)) //
				.map(Inet4AddressWithSubnetmask::getLabel) //
				.findFirst() //
				.orElseThrow();
	}

	private static Set<String> addressStrings(NetworkInterface<?> n) {
		return n.getAddresses().getValue().stream() //
				.map(Inet4AddressWithSubnetmask::toString) //
				.collect(Collectors.toSet());
	}
}
