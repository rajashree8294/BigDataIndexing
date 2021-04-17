package com.info7255.api.beans;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import com.info7255.api.util.PlanUtils;

import io.lettuce.core.RedisException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

@Service
public class JedisBean {

	private static JedisPool pool = null;
	private static final String SEP = "_";
	private static final String redisHost = "localhost";
	private static final Integer redisPort = 6379;

//	public JedisBean() {
//		pool = new JedisPool(redisHost, redisPort);
//	}
	Jedis jedis = new Jedis();

	public boolean insertSchema(String schema) {
		try {
			Jedis jedis = pool.getResource();
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
			// Jedis jedis = pool.getResource();
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
			// Jedis jedis = pool.getResource();
			res = getPlanValue(objectType, objectId);
		} catch (Exception e) {
			// TODO: handle exception
			res = null;
		}

		return res;
	}

	public Boolean isPlanExist(String key) {
		JSONObject res = null;
		// Jedis jedis = pool.getResource();
		Set<String> keys = jedis.keys("*");
		// String value = keys.stream().anyMatch(x -> x.equals(key));
		// res = readUtil(key);

		return keys.stream().anyMatch(x -> x.equals(key));
	}

	public String getKey(String objectType, String objectId) {
		return objectType + SEP + objectId;

	}

	public JSONObject getPlanValue(String objectType, String objectId) {
		return this.readUtil(getKey(objectType, objectId));
	}
	
	public JSONObject getPlanValueJedis(String objectType, String objectId) {
		String strPlan = jedis.get(getKey(objectType, objectId));
		return new JSONObject(strPlan);
	}

	public Boolean cleanRepo() {
		// Jedis jedis = pool.getResource();
		Set<String> keys = jedis.keys("*");
		long deleted = jedis.del(keys.toArray(new String[keys.size()]));
		return deleted > 0 ? true : false;
	}

	public Boolean removePlan(String objectType, String objectId) {
		// Jedis jedis = pool.getResource();
		String key = getKey(objectType, objectId);
		//long deleted = jedis.del(key);
		return deleteUtil(key);
	}
	
	public boolean delete(String body) {
		JSONObject json = new JSONObject(body);
		if (!json.has("objectType") || !json.has("objectId"))
			return false;
		return deleteUtil(json.getString("objectType") + SEP + json.getString("objectId"));
	}

	public boolean deleteUtil(String uuid) {
		try {
					
			Set<String> keys = jedis.keys(uuid + SEP + "*");
			if (keys.isEmpty())
				return false;
			for (String key : keys) {
				if (jedis.type(key).equalsIgnoreCase("string")) {
					jedis.del(key);
					continue;
				}
				Set<String> jsonKeySet = jedis.smembers(key);
				for (String embd_uuid : jsonKeySet) {
					deleteUtil(embd_uuid);
				}
				jedis.del(key);
			}
			
			jedis.del(uuid);
			return true;
		} catch (JedisException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean replace(JSONObject jsonObject) {
		String key = jsonObject.getString("objectType") + SEP + jsonObject.getString("objectId");
		
		try {
			if(removePlan(jsonObject.getString("objectType"), jsonObject.getString("objectId"))) {
				return insertUtil(jsonObject, key);
			}
			return false;

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

	}
	

	public boolean patch(JSONObject jsonObject) {
		
		String uuid = jsonObject.getString("objectType") + SEP + jsonObject.getString("objectId");
		Map<String, String> simpleMap = jedis.hgetAll(uuid);
		try {
					
			if (simpleMap.isEmpty()) {
				simpleMap = new HashMap<String, String>();
			}

//			if (!isPlanExist(uuid))
//				return false;

			for (Object key : jsonObject.keySet()) {
				String attributeKey = String.valueOf(key);
				Object attributeVal = jsonObject.get(String.valueOf(key));
				String edge = attributeKey;

				if (attributeVal instanceof JSONObject) {

					JSONObject embdObject = (JSONObject) attributeVal;
					String setKey = uuid + SEP + edge;
					String embd_uuid = embdObject.get("objectType") + SEP + embdObject.getString("objectId");
					jedis.sadd(setKey, embd_uuid);
					patch(embdObject);

				} else if (attributeVal instanceof JSONArray) {

					JSONArray jsonArray = (JSONArray) attributeVal;
					Iterator<Object> jsonIterator = jsonArray.iterator();
					String setKey = uuid + SEP + edge;

					while (jsonIterator.hasNext()) {
						JSONObject embdObject = (JSONObject) jsonIterator.next();
						String embd_uuid = embdObject.get("objectType") + SEP + embdObject.getString("objectId");
						jedis.sadd(setKey, embd_uuid);
						patch(embdObject);
					}

				} else {
					simpleMap.put(attributeKey, String.valueOf(attributeVal));
				}
			}
		} catch (JedisException e) {
			e.printStackTrace();
			return false;
		}
		jedis.hmset(uuid, simpleMap);
		return true;
	}
	

	private JSONObject readUtil(String uuid) {
		try {
			JSONObject o = new JSONObject();

			Set<String> keys = jedis.keys(uuid + SEP + "*");

			for (String key : keys) {
				Set<String> jsonKeySet = jedis.smembers(key);

				if (jsonKeySet.size() > 1) {

					JSONArray ja = new JSONArray();
					Iterator<String> jsonKeySetIterator = jsonKeySet.iterator();
					while (jsonKeySetIterator.hasNext()) {
						String nextKey = jsonKeySetIterator.next();
						ja.put(readUtil(nextKey));
					}
					o.put(key.substring(key.lastIndexOf(SEP) + 1), ja);
				} else {

					Iterator<String> jsonKeySetIterator = jsonKeySet.iterator();
					JSONObject embdObject = null;
					while (jsonKeySetIterator.hasNext()) {
						String nextKey = jsonKeySetIterator.next();
						embdObject = readUtil(nextKey);
					}
					o.put(key.substring(key.lastIndexOf(SEP) + 1), embdObject);
				}
			}

			Map<String, String> simpleMap = jedis.hgetAll(uuid);
			for (String simpleKey : simpleMap.keySet()) {
				o.put(simpleKey, simpleMap.get(simpleKey));
			}

			return o;
		} catch (RedisException e) {
			e.printStackTrace();
			return null;
		}
	}

	public boolean insertUtil(JSONObject jsonObject, String uuid) {

		try {

			Map<String, String> simpleMap = new HashMap<String, String>();

			for (Object key : jsonObject.keySet()) {
				String attributeKey = String.valueOf(key);
				Object attributeVal = jsonObject.get(String.valueOf(key));
				String edge = attributeKey;
				if (attributeVal instanceof JSONObject) {

					JSONObject embdObject = (JSONObject) attributeVal;
					String setKey = uuid + SEP + edge;
					String embd_uuid = embdObject.get("objectType") + SEP + embdObject.getString("objectId");
					jedis.sadd(setKey, embd_uuid);

					insertUtil(embdObject, embd_uuid);

				} else if (attributeVal instanceof JSONArray) {

					JSONArray jsonArray = (JSONArray) attributeVal;
					Iterator<Object> jsonIterator = jsonArray.iterator();
					String setKey = uuid + SEP + edge;

					while (jsonIterator.hasNext()) {
						JSONObject embdObject = (JSONObject) jsonIterator.next();
						String embd_uuid = embdObject.get("objectType") + SEP + embdObject.getString("objectId");
						jedis.sadd(setKey, embd_uuid);
						insertUtil(embdObject, embd_uuid);
					}

				} else {
					simpleMap.put(attributeKey, String.valueOf(attributeVal));
				}
			}
			jedis.hmset(uuid, simpleMap);
		} catch (JedisException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}
	
}
