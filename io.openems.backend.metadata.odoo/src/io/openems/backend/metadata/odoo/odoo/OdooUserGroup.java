package io.openems.backend.metadata.odoo.odoo;

public enum OdooUserGroup {

	// XXX PORTAL id might differ between different Odoo databases
	PORTAL(10);

	private final int groupId;

	private OdooUserGroup(int groupId) {
		this.groupId = groupId;
	}

	public int getGroupId() {
		return this.groupId;
	}

}
