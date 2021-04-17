package com.info7255.api.controller;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import org.everit.json.schema.ValidationException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.info7255.api.beans.JSONValidator;
import com.info7255.api.beans.JedisBean;
import com.info7255.api.util.PlanUtils;

@RestController
public class PlanController {

	@Autowired
	JSONValidator jsonValidator;

	@Autowired
	JedisBean jedisBean;

	Map<String, Object> res = new HashMap<String, Object>();

	@PostMapping(value = "/plan", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> createPlan(@RequestBody String plan, @RequestHeader HttpHeaders headers)
			throws JSONException, NoSuchAlgorithmException {
		res.clear();
		if (!PlanUtils.authorize(headers)) {
			res.put("message", "Authorization Failed.. !");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(res);
		}

		if (plan == null || plan.isEmpty()) {
			res.put("error", "Plan is empty");
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
		}

		JSONObject jsonPlan = new JSONObject(plan);

		try {
			jsonValidator.validateSchema(plan);
		} catch (ValidationException e) {
			res.put("error", e.getErrorMessage());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
		}

		String key = jsonPlan.get("objectType").toString() + "_" + jsonPlan.get("objectId");

		if (jedisBean.isPlanExist(key)) {
			res.put("message", "This plan already exist! ");
			return ResponseEntity.status(HttpStatus.CONFLICT).body(res);
		}

		jedisBean.insertUtil(jsonPlan, key);

		String eTag = PlanUtils.hashString(plan);
		res.put("message", "Plan Created Successfully!");
		return ResponseEntity.status(HttpStatus.CREATED).eTag(eTag).body(res);
	}

	@GetMapping(path = "/{objectType}/{objectId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> getPlan(@PathVariable String objectType, @PathVariable String objectId,
			@RequestHeader HttpHeaders headers) throws NoSuchAlgorithmException {
		res.clear();
		if (!PlanUtils.authorize(headers)) {
			res.put("message", "Authorization Failed.. !");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(res);
		}

		if (!jedisBean.isPlanExist(objectType + "_" + objectId)) {
			res.put("message", "No plan with this objectId found!");
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(res);
		}

		JSONObject plan = (JSONObject) jedisBean.getPlanValue(objectType, objectId);
		String originalTag = PlanUtils.hashString(plan.toString());
		String req_eTag = headers.getFirst("If-None-Match");
		if (req_eTag != null && originalTag.equals(req_eTag)) {
			res.put("message", "This plan has not changed ");
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(originalTag).body(res);
		}
		// Map<String, Object> output = new HashMap<String, Object>();
		// output.(objectType +"_"+ objectId, plan);

		return ResponseEntity.status(HttpStatus.OK).eTag(originalTag).body(plan.toMap());
	}

	@DeleteMapping(path = "/{objectType}/{objectId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> deletePlan(@PathVariable String objectType, @PathVariable String objectId,
			@RequestHeader HttpHeaders headers) {
		res.clear();
		if (!PlanUtils.authorize(headers)) {
			res.put("message", "Authorization Failed.. !");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(res);
		}

		if (!jedisBean.isPlanExist(objectType + "_" + objectId)) {
			res.put("message", "No plan with is objectId found!");
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(res);
		}

		if (jedisBean.removePlan(objectType, objectId)) {
			res.put("message", "Plan deleted successfully!");
			return ResponseEntity.status(HttpStatus.NO_CONTENT).body(res);
		}
		res.put("message", "Looks like there is issue with request!");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
	}

	@PatchMapping(value = "/{objectType}/{objectId}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> update(@PathVariable String objectType, @PathVariable String objectId,
			@RequestBody(required = true) String plan, @RequestHeader HttpHeaders headers) {
		
		res.clear();
		if (!PlanUtils.authorize(headers)) {
			res.put("message", "Authorization Failed.. !");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(res);
		}

		JSONObject jsonObject = PlanUtils.getJsonObjectFromString(plan);

		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(MediaType.APPLICATION_JSON);

		JSONObject planJSON = jedisBean.findPlan(objectType, objectId);

		if ( !planJSON.isEmpty() && planJSON != null) {

			try {

				JSONObject planStr = jedisBean.getPlanValue(objectType, objectId);
				String originalTag = PlanUtils.hashString(planStr.toString());
				String req_eTag = headers.getFirst("If-Match");
				if (req_eTag != null && originalTag.equals(req_eTag)) {

					if (!jedisBean.patch(jsonObject)) {
						res.put("message", "Patch failed ..!");
						return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
					}
					String newETag = PlanUtils.hashString(jsonObject.toString());
					res.put("message", "Patch successful...");
					return ResponseEntity.status(HttpStatus.NO_CONTENT).eTag(newETag).body(res);
				} else {
					if (headers.getFirst("If-Match").isEmpty()) {
						res.put("message", "If-Match ETag required");
						return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(res);
					} else {
						res.put("message", "If-Match ETag required");
						return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(originalTag).body(res);

					}
				}
			} catch (Exception e) {
				res.put("message", e.getMessage());
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
			}
		}

		res.put("message", "Invalid Plan Id");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);

	}
	
	@PutMapping(value = "/{objectType}/{objectId}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> replace(@PathVariable String objectType, @PathVariable String objectId,
			@RequestBody(required = true) String plan, @RequestHeader HttpHeaders headers) {	
		
		res.clear();
		if (!PlanUtils.authorize(headers)) {
			res.put("message", "Authorization Failed.. !");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(res);
		}

		JSONObject jsonObject = PlanUtils.getJsonObjectFromString(plan);

		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(MediaType.APPLICATION_JSON);

		JSONObject planJSON = jedisBean.findPlan(objectType, objectId);

		if ( !planJSON.isEmpty() && planJSON != null) {

			try {

				JSONObject planStr = jedisBean.getPlanValue(objectType, objectId);
				String originalTag = PlanUtils.hashString(planStr.toString());
				String req_eTag = headers.getFirst("If-Match");
				if (req_eTag != null && originalTag.equals(req_eTag)) {

					if (!jedisBean.replace(jsonObject)) {
						res.put("message", "Put operation failed ..!");
						return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
					}
					String newETag = PlanUtils.hashString(jsonObject.toString());
					res.put("message", "Patch operation successful...");
					return ResponseEntity.status(HttpStatus.NO_CONTENT).eTag(newETag).body(res);
				} else {
					if (headers.getFirst("If-Match").isEmpty()) {
						res.put("message", "If-Match ETag required");
						return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(res);
					} else {
						res.put("message", "If-Match ETag required");
						return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(originalTag).body(res);

					}
				}
			} catch (Exception e) {
				res.put("message", e.getMessage());
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
			}

		}
		res.put("message", "Invalid Plan Id");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);

	}

	@GetMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> createToken() {
		res.clear();
		String authToken = PlanUtils.createAuthToken();

		res.put("token", authToken);
		return ResponseEntity.status(HttpStatus.CREATED).body(res);
	}

	@DeleteMapping(value = "/clean-redis")
	public ResponseEntity<Object> cleanRedis() {
		res.clear();
		try {
			jedisBean.cleanRepo();
		} catch (Exception e) {
			res.put("message", "Plans deletion Failed");
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
		}
		res.put("message", "All plans are deleted successfully");
		return ResponseEntity.status(HttpStatus.OK).body(res);

	}

}
