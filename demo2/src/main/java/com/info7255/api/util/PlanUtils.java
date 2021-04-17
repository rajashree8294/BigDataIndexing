package com.info7255.api.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.springframework.http.HttpHeaders;


public class PlanUtils {

    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    private static final String OUTPUT_FORMAT = "%-20s:%s";
    private  static final String key = "ssdkF$HUy2A#D%kd";
	private  static final String algorithm = "AES";

    private static byte[] digest(byte[] input) throws NoSuchAlgorithmException{
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] result = md.digest(input);
        return result;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    public static String hashString(String data) throws NoSuchAlgorithmException{
        byte[] md5InBytes = PlanUtils.digest(data.getBytes(UTF_8));
        String bytesToHex = PlanUtils.bytesToHex(md5InBytes);
        
        return bytesToHex;
    }
    
    public static JSONObject getJsonObjectFromString(String jsonString) {
		return new JSONObject(jsonString);
	}
  
    public static String createAuthToken() {
    	String encoded = null;
    	JSONObject jsonToken = new JSONObject();
		jsonToken.put("Issuer", "Rajashree");

		TimeZone tz = TimeZone.getTimeZone("UTC");
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssssZ");
		df.setTimeZone(tz);

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(new Date());
		calendar.add(Calendar.MINUTE, 60);
		Date date = calendar.getTime();

		jsonToken.put("expiry", df.format(date));
		String token = jsonToken.toString();
		System.out.println(token);
		SecretKey spec = loadKey();

		try {
			Cipher c = Cipher.getInstance(algorithm);
			c.init(Cipher.ENCRYPT_MODE, spec);
			byte[] encrBytes = c.doFinal(token.getBytes());
			encoded = Base64.getEncoder().encodeToString(encrBytes);

		}catch (Exception e) {
			e.printStackTrace();
		}
		
		return encoded;
    }

    private static SecretKey loadKey() {
		return new SecretKeySpec(key.getBytes(), algorithm);
	}
    
    public static boolean authorize(HttpHeaders headers) {
		if (headers.getFirst("Authorization") == null)
			return false;

		String token = headers.getFirst("Authorization").substring(7);
		byte[] decrToken = Base64.getDecoder().decode(token);
		SecretKey spec = loadKey();
		try {
			Cipher c = Cipher.getInstance(algorithm);
			c.init(Cipher.DECRYPT_MODE, spec);
			String tokenString = new String(c.doFinal(decrToken));
			JSONObject jsonToken = new JSONObject(tokenString);
			
			String ttldateAsString = jsonToken.get("expiry").toString();
			Date currentDate = Calendar.getInstance().getTime();

			TimeZone tz = TimeZone.getTimeZone("UTC");
			DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssssZ");
			formatter.setTimeZone(tz);

			Date ttlDate = formatter.parse(ttldateAsString);
			currentDate = formatter.parse(formatter.format(currentDate));

			if (currentDate.after(ttlDate)) {
				return false;
			}

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
}
