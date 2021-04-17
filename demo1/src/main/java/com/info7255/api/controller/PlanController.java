package com.info7255.api.controller;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.everit.json.schema.ValidationException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
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
	
	@PostMapping(value = "/plan", consumes = "application/json")
	public ResponseEntity<Object> createPlan(@RequestBody String plan) throws JSONException, NoSuchAlgorithmException {
		if(plan == null || plan.isEmpty()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("error", "Plan is empty").toString());
		}
		
		JSONObject jsonPlan = new JSONObject(plan);
		
		try {
			jsonValidator.validateSchema(plan);
		} catch (ValidationException e) {
			// TODO: handle exception
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("error", e.getErrorMessage()).toString());
		}
		
		String key = jsonPlan.get("objectType").toString()+ "_" + jsonPlan.get("objectId");
		
		if(jedisBean.isPlanExist(key)) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(new JSONObject().put("message", "This plan already exist! ").toString());
		}
		
		jedisBean.savePlan(key, plan);
		
		String eTag = PlanUtils.hashString(plan);
		
		return ResponseEntity.status(HttpStatus.CREATED).eTag(eTag).body(new JSONObject().put("message", "Plan Created Successfully!").toString());
	}
	
	@GetMapping(path = "/{objectType}/{objectId}", produces = "application/json")
	public ResponseEntity<Object> getPlan(@PathVariable String objectType, @PathVariable String objectId,
			 @RequestHeader HttpHeaders headers) throws NoSuchAlgorithmException {
		
		if(!jedisBean.isPlanExist(objectType +"_"+ objectId)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new JSONObject().put("message", "No plan with this objectId found!").toString());
		}
		
		String plan = jedisBean.getPlanValue(objectType, objectId);
		String originalTag = PlanUtils.hashString(plan);
		String req_eTag = headers.getFirst("If-None-Match");
		if(req_eTag != null && originalTag.equals(req_eTag)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(originalTag).body(new JSONObject().put("message", "This plan has not changed ").toString());
		}
		//Map<String, Object> output = new HashMap<String, Object>();
		//output.(objectType +"_"+ objectId, plan);

		return ResponseEntity.status(HttpStatus.OK).body(new JSONObject(plan).toString());
	}
	
	
	@DeleteMapping(path = "/{objectType}/{objectId}", produces = "application/json")
	public ResponseEntity<Object> deletePlan(@PathVariable String objectType, @PathVariable String objectId, @RequestHeader HttpHeaders headers){
		if(!jedisBean.isPlanExist(objectType +"_"+ objectId)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new JSONObject().put("message", "No plan with is objectId found!").toString());
		}
		
		if(jedisBean.removePlan(objectType, objectId)) {
			return ResponseEntity.status(HttpStatus.NO_CONTENT).body(new JSONObject().put("message", "Plan deleted successfully!").toString());
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("message", "Looks like there is issue with request!").toString());
	}
	
	@DeleteMapping(value = "/clean-redis")
    public ResponseEntity<Object> cleanRedis() {

        try {
            jedisBean.cleanRepo();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("message", "Plans deletion Failed").toString());
        }

        return ResponseEntity.status(HttpStatus.OK).body(new JSONObject().put("message", "All plans are deleted successfully").toString());

    }

}
