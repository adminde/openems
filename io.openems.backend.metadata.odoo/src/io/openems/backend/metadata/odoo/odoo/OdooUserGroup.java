package io.openems.backend.metadata.odoo.odoo;

/**
 * Odoo groups referenced by their XML-ID.
 *
 * <p>
 * The database ID of a group differs between Odoo databases, so it is resolved
 * at runtime via 'ir.model.data' (see
 * {@link OdooHandler#getOdooGroupIds(OdooUserRole)}).
 */
public enum OdooUserGroup {

	PORTAL("base", "group_portal");

	private final String module;
	private final String name;

	private OdooUserGroup(String module, String name) {
		this.module = module;
		this.name = name;
	}

	/**
	 * Gets the Odoo module of the XML-ID, e.g. 'base'.
	 *
	 * @return the module
	 */
	public String getModule() {
		return this.module;
	}

	/**
	 * Gets the name of the XML-ID within the module, e.g. 'group_portal'.
	 *
	 * @return the name
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Gets the full XML-ID, e.g. 'base.group_portal'.
	 *
	 * @return the XML-ID
	 */
	public String getXmlId() {
		return this.module + "." + this.name;
	}

}
