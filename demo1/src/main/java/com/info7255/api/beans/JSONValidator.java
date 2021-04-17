package com.info7255.api.beans;

import java.io.InputStream;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Service;

@Service
public class JSONValidator {
	//private final static Schema schema = loadSchema();

//    private static Schema loadSchema() {
//        InputStream inputStream = Validator.class.getResourceAsStream("/jsonSchema.json");
//        System.out.println(inputStream.toString());
//        JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
//        Schema schema = SchemaLoader.load(rawSchema);
//        return schema;
//    }
//	
//	public static Boolean isJSONValid(JSONObject object) throws IOException, ProcessingException {
//		try {
//			schema.validate(object);
//			return true;
//		}
//		catch (ValidationException e) {
//			e.printStackTrace();
//			return false;
//		}
//		
//	}
	public JSONObject validateSchema(String json) throws JSONException, ValidationException {

		InputStream schemaStream = JSONValidator.class.getResourceAsStream("/schema.json");

		JSONObject jsonSchema = new JSONObject(new JSONTokener(schemaStream));

		JSONObject jsonSubject = new JSONObject(new JSONTokener(json));

		Schema schema = SchemaLoader.load(jsonSchema);
		schema.validate(jsonSubject);

		return jsonSubject;

	}
}
