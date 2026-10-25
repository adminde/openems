package io.openems.backend.metadata.file;

import com.google.gson.JsonObject;

import io.openems.backend.common.metadata.Edge;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.utils.JsonUtils;

public class FileEdge extends Edge {

	private final String apikey;
	private final String setupPassword;

	private final boolean isPublic;

	private JsonObject settings;

	public FileEdge(MetadataFile parent, String id, String apikey, String setupPassword, String comment, String version,
			String producttype, boolean isPublic, JsonObject settings) {
		super(parent, id, comment, version, producttype, null);
		this.isPublic = isPublic;
		this.apikey = apikey;
		this.setupPassword = setupPassword;
		this.settings = settings;
	}

	public boolean isPublic() {
		return this.isPublic;
	}

	public String getApikey() {
		return this.apikey;
	}

	public String getSetupPassword() {
		return this.setupPassword;
	}

	public JsonObject getSettings() {
		return this.settings;
	}

	public void setSettings(JsonObject settings) {
		this.settings = settings;
	}

	/**
	 * Creates a {@link FileEdge} from its JSON definition in the metadata file.
	 *
	 * @param parent the {@link MetadataFile}
	 * @param id     the Edge-ID
	 * @param json   the JSON definition of the Edge
	 * @return the {@link FileEdge}
	 * @throws OpenemsNamedException on missing mandatory properties
	 */
	public static FileEdge fromJson(MetadataFile parent, String id, JsonObject json) throws OpenemsNamedException {
		String apikey = JsonUtils.getAsString(json, "apikey");
		String setupPassword = JsonUtils.getAsOptionalString(json, "setuppassword").orElse("");

		String comment = JsonUtils.getAsString(json, "comment");
		String productType = JsonUtils.getAsOptionalString(json, "type").orElse("");

		boolean isPublic = JsonUtils.getAsOptionalBoolean(json, "public").orElse(false);

		JsonObject settings = json.has("settings") && json.get("settings").isJsonObject()
				? json.getAsJsonObject("settings")
				: null;

		return new FileEdge(parent, id, apikey, setupPassword, comment, "", productType, isPublic, settings);
	}

}
