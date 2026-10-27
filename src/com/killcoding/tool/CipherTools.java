/* ************************************************************************
The MIT License (MIT)

Copyright (c) 2024 KILLCODING.COM

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*****************************************************************************/
package com.killcoding.tool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Arrays;
import com.killcoding.file.BaseFile;

/**
* This class is support base cipher function
* */
public final class CipherTools {
    
    /**
    * Performs the DESEncrypt operation
    * @param keyText - the keyText
    * @param ivText - the ivText
    * @param data - the data
    * @return the DESEncrypt result
    * */
    public static byte[] DESEncrypt(String keyText, String ivText, byte[] data) throws Exception {
		DESKeySpec keySpec = null;
		AlgorithmParameterSpec iv = null ;
		SecretKeyFactory keyFactory = null;
		Key key = null;
		Cipher cipher = null;
		keySpec = new DESKeySpec(keyText.getBytes(BaseFile.CHARSET));
		iv = new IvParameterSpec(ivText.getBytes(BaseFile.CHARSET));
		keyFactory = SecretKeyFactory.getInstance("DES");
		key = keyFactory.generateSecret(keySpec);
		cipher = Cipher.getInstance("DES/CBC/PKCS5Padding");
		cipher.init(Cipher.ENCRYPT_MODE, key, iv);
		return cipher.doFinal(data);
    }
    
    /**
    * Performs the DESDecrypt operation
    * @param keyText - the keyText
    * @param ivText - the ivText
    * @param encryptData - the encryptData
    * @return the DESDecrypt result
    * */
    public static byte[] DESDecrypt(String keyText, String ivText, byte[] encryptData) throws Exception {
		DESKeySpec keySpec = null;
		AlgorithmParameterSpec iv = null ;
		SecretKeyFactory keyFactory = null;
		Key key = null;
		Cipher cipher = null;
		keySpec = new DESKeySpec(keyText.getBytes(BaseFile.CHARSET));
		iv = new IvParameterSpec(ivText.getBytes(BaseFile.CHARSET));
		keyFactory = SecretKeyFactory.getInstance("DES");
		key = keyFactory.generateSecret(keySpec);
		cipher = Cipher.getInstance("DES/CBC/PKCS5Padding");
		cipher.init(Cipher.DECRYPT_MODE, key, iv);
		return cipher.doFinal(encryptData);		
    }
    
    /**
    * Performs the _3DESEncrypt operation
    * @param keyText - the keyText
    * @param ivText - the ivText
    * @param data - the data
    * @return the _3DESEncrypt result
    * */
    public static byte[] _3DESEncrypt(String keyText,String ivText,byte[] data) throws Exception {
        String Algorithm = "DESede";
		String TRANS_FORMATION = "DESede/CBC/PKCS5Padding";
		SecretKey key = null;
		Cipher cipher = null;
		IvParameterSpec iv = null;
		key = new SecretKeySpec(keyText.getBytes(BaseFile.CHARSET), Algorithm);
		cipher = Cipher.getInstance(TRANS_FORMATION);	
		iv = new IvParameterSpec(ivText.getBytes(BaseFile.CHARSET));
		cipher.init(Cipher.ENCRYPT_MODE, key,iv);
		return cipher.doFinal(data);			
    }
    
    /**
    * Performs the _3DESDecrypt operation
    * @param keyText - the keyText
    * @param ivText - the ivText
    * @param encryptData - the encryptData
    * @return the _3DESDecrypt result
    * */
    public static byte[] _3DESDecrypt(String keyText,String ivText,byte[] encryptData) throws Exception {
        String Algorithm = "DESede";
		String TRANS_FORMATION = "DESede/CBC/PKCS5Padding";
		SecretKey key = null;
		Cipher cipher = null;
		IvParameterSpec iv = null;
		key = new SecretKeySpec(keyText.getBytes(BaseFile.CHARSET), Algorithm);
		cipher = Cipher.getInstance(TRANS_FORMATION);	
		iv = new IvParameterSpec(ivText.getBytes(BaseFile.CHARSET));
		cipher.init(Cipher.DECRYPT_MODE, key,iv);
		return cipher.doFinal(encryptData);		
    }
    
    /**
    * Performs the AESEncrypt operation
    * @param keyText - the keyText
    * @param data - the data
    * @return the AESEncrypt result
    * */
    public static byte[] AESEncrypt(String keyText, byte[] data) throws Exception {
		SecretKeySpec spec = new SecretKeySpec(generateAESKey(keyText), "AES");
		Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		cipher.init(Cipher.ENCRYPT_MODE, spec);
		return cipher.doFinal(data);
    }
    
    /**
    * Performs the AESDecrypt operation
    * @param keyText - the keyText
    * @param encryptData - the encryptData
    * @return the AESDecrypt result
    * */
    public static byte[] AESDecrypt(String keyText, byte[] encryptData) throws Exception {
		SecretKeySpec spec = new SecretKeySpec(generateAESKey(keyText), "AES");
		Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		cipher.init(Cipher.DECRYPT_MODE, spec);
		return cipher.doFinal(encryptData);
    }  

    /**
    * Performs the generateAESKey operation
    * @param keyText - the keyText
    * @return the generateAESKey result
    * */
    public static byte[] generateAESKey(String keyText) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(keyText.getBytes(BaseFile.CHARSET));
        return Arrays.copyOf(hash, 16);
    }
    
    /**
    * Performs the RSAPrivateKeyGen operation
    * @param seed - the seed
    * @return the RSAPrivateKeyGen result
    * */
    public static byte[] RSAPrivateKeyGen(byte[] seed) throws Exception {
		 String ALOGRITHM = "RSA";
		 String RANDOM_ALOGRITHM = "SHA1PRNG"; 
		 KeyPairGenerator generator = null;
		 KeyPair keyPair = null;
		 generator = KeyPairGenerator.getInstance(ALOGRITHM);
		 SecureRandom random = SecureRandom
				.getInstance(RANDOM_ALOGRITHM);
		 random.setSeed(seed);
		 generator.initialize(1024, random);
		 keyPair = generator.generateKeyPair();	
		 return keyPair.getPrivate().getEncoded();
    }
    
    /**
    * Performs the RSAPublieKeyGen operation
    * @param seed - the seed
    * @return the RSAPublieKeyGen result
    * */
    public static byte[] RSAPublieKeyGen(byte[] seed) throws Exception {
		 String ALOGRITHM = "RSA";
		 String RANDOM_ALOGRITHM = "SHA1PRNG"; 
		 KeyPairGenerator generator = null;
		 KeyPair keyPair = null;
		 generator = KeyPairGenerator.getInstance(ALOGRITHM);
		 SecureRandom random = SecureRandom
				.getInstance(RANDOM_ALOGRITHM);
		 random.setSeed(seed);
		 generator.initialize(1024, random);
		 keyPair = generator.generateKeyPair();	
		 return keyPair.getPublic().getEncoded();
    }   
    
	/**
	* Performs the RSAPublicKeyFrom operation
	* @param keyArray - the keyArray
	* @return the RSAPublicKeyFrom result
	* */
	public static PublicKey RSAPublicKeyFrom(byte[] keyArray) throws Exception {
		String ALOGRITHM = "RSA";
		KeySpec keySpec = new X509EncodedKeySpec(
		keyArray);
		KeyFactory keyFactory = KeyFactory.getInstance(ALOGRITHM);
		return keyFactory.generatePublic(keySpec);
	}

	/**
	* Performs the RSAPrivateKeyFrom operation
	* @param keyArray - the keyArray
	* @return the RSAPrivateKeyFrom result
	* */
	public static PrivateKey RSAPrivateKeyFrom(byte[] keyArray) throws Exception {
		String ALOGRITHM = "RSA";
		KeySpec keySpec = new PKCS8EncodedKeySpec(keyArray);
		KeyFactory keyFactory = KeyFactory.getInstance(ALOGRITHM);
		return keyFactory.generatePrivate(keySpec);
	}
    
	/**
	* Performs the RSAEncrypt operation
	* @param key - the key
	* @param data - the data
	* @return the RSAEncrypt result
	* */
	public static byte[] RSAEncrypt(Key key, byte[] data) throws Exception {
	    String ALOGRITHM = "RSA";
		Cipher cipher = Cipher.getInstance(ALOGRITHM);
		cipher.init(Cipher.ENCRYPT_MODE, key);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		CipherInputStream cis = new CipherInputStream(
				new ByteArrayInputStream(data), cipher);
		byte[] buffer = new byte[1024];
		int r;
		try {
			while ((r = cis.read(buffer)) > 0) {
				out.write(buffer, 0, r);
			}
		} finally {
			cis.close();
			out.close();
		}
		return out.toByteArray();
	}    
	
	/**
	* Performs the RSADecrypt operation
	* @param key - the key
	* @param encryptData - the encryptData
	* @return the RSADecrypt result
	* */
	public static byte[] RSADecrypt(Key key, byte[] encryptData) throws Exception {
	    String ALOGRITHM = "RSA";
		Cipher cipher = Cipher.getInstance(ALOGRITHM);
		cipher.init(Cipher.DECRYPT_MODE, key);
		ByteArrayInputStream is = new ByteArrayInputStream(encryptData);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		CipherOutputStream cos = new CipherOutputStream(out, cipher);
		byte[] buffer = new byte[1024];
		int r;
		try {
			while ((r = is.read(buffer)) >= 0) {
				cos.write(buffer, 0, r);
			}
		} finally {
			is.close();
			cos.close();
			out.close();
		}
		return out.toByteArray();
	}	

}
