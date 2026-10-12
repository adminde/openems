package io.openems.backend.metadata.odoo.odoo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.openems.backend.metadata.odoo.Field;
import io.openems.backend.metadata.odoo.odoo.Domain.Operator;

class OdooHandlerTest {

	private static final Domain NO_PARENT = new Domain(Field.Partner.PARENT, Operator.EQ, false);
	private static final Domain NO_USER = new Domain(Field.Partner.USERS, Operator.EQ, false);

	@Test
	void testGetCompanySearchDomains() {
		assertArrayEquals(new Domain[] { NO_PARENT, NO_USER, //
				new Domain(Field.Partner.NAME, Operator.EQ, "Oros Energy Europe GmbH") }, //
				OdooHandler.getCompanySearchDomains("Oros Energy Europe GmbH"));
	}

	@Test
	void testGetCompanySearchDomainsDoNotUseIsCompany() {
		// is_company is computed from the VAT since Odoo 20
		for (var domain : OdooHandler.getCompanySearchDomains("Oros Energy Europe GmbH")) {
			assertFalse(Field.Partner.IS_COMPANY.id().equals(domain.field));
			assertFalse(Field.Partner.COMPANY_NAME.id().equals(domain.field));
		}
	}

	@Test
	void testGetOtherCommercialPartnerId() {
		// contact linked to company 7 via parent_id
		assertEquals(Optional.of(7), OdooHandler.getOtherCommercialPartnerId(42, partner(7)));

		// partner without parent_id is its own commercial partner
		assertTrue(OdooHandler.getOtherCommercialPartnerId(42, partner(42)).isEmpty());

		// commercial_partner_id not set
		assertTrue(OdooHandler.getOtherCommercialPartnerId(42, partner(null)).isEmpty());
	}

	private static Map<String, Object> partner(Integer commercialPartnerId) {
		var partner = new HashMap<String, Object>();
		partner.put(Field.Partner.COMMERCIAL_PARTNER.id(),
				commercialPartnerId == null ? false : new Object[] { commercialPartnerId, "Name" });
		return partner;
	}

}
