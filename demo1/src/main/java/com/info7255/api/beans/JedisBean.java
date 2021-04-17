package com.info7255.api.beans;

import java.util.Set;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Service;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

@Service
public class JedisBean {

	Jedis jedis = new Jedis();

	public boolean insertSchema(String schema) {
		try {
			if (jedis.set("plan_schema", schema).equals("OK"))
				return true;
			else
				return false;
		} catch (JedisException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean savePlan(String key, String plan) {
		try {
			if (jedis.set(key, plan).equals("OK"))
				return true;
			else
				return false;
		} catch (JedisException e) {
			e.printStackTrace();
			return false;
		}
	}

	public JSONObject findPlan(String objectType, String objectId) {

		JSONObject res = null;
		try {
			String key = getKey(objectType, objectId);
			String value = jedis.get(key);
			res = new JSONObject(new JSONTokener(value));
		} catch (Exception e) {
			// TODO: handle exception
			res = null;
		}

		return res;
	}

	public Boolean isPlanExist(String key) {
		JSONObject res = null;
		String value = jedis.get(key);
		return value != null ? true : false;
	}

	private String getKey(String objectType, String objectId) {
		return objectType + "_" + objectId;

	}

	public String getPlanValue(String objectType, String objectId) {
		return jedis.get(getKey(objectType, objectId));
	}

	public Boolean cleanRepo() {
		Set<String> keys = jedis.keys("*");
		long deleted = jedis.del(keys.toArray(new String[keys.size()]));
		return deleted > 0 ? true : false;
	}

	public Boolean removePlan(String objectType, String objectId) {
		String key = getKey(objectType, objectId);
		long deleted = jedis.del(key);
		return deleted > 0 ? true : false;
	}

}
