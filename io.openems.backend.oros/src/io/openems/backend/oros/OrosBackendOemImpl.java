package io.openems.backend.oros;

import org.osgi.service.component.annotations.Component;

import io.openems.common.oem.OpenemsBackendOem;

@Component
public class OrosBackendOemImpl implements OpenemsBackendOem {

	@Override
	public String getAppCenterMasterKey() {
		return "OROS_MASTER_KEY";
	}

}
