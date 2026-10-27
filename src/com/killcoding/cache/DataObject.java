/*******************************************************************************
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
package com.killcoding.cache;

import java.io.Serializable;



import java.io.ObjectOutputStream;

import java.io.File;
import java.io.ByteArrayOutputStream;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.ObjectInputStream;
import java.io.ByteArrayInputStream;

import com.killcoding.tool.CommonTools;
import com.killcoding.log.Logger;
import com.killcoding.log.LoggerFactory;


import com.killcoding.tool.CipherTools;

/**
* This class is encrypting cache value
* */
public class DataObject implements java.io.Serializable {

	private static final Logger log = LoggerFactory.getLogger(DataObject.class);

	private String path;

    private final static String defaultAesKey = CommonTools.generateId(256);

    private String aesKey = null;

	private boolean useEncrypt = true;

	/**
	* Constructs a new DataObject
	* @param path - the path
	* */
	public DataObject(String path) throws Exception {
		super();
		this.path = path;
		this.aesKey = defaultAesKey;
	}

	/**
	* Constructs a new DataObject
	* @param aesKey - the aesKey
	* @param path - the path
	* */
	public DataObject(String aesKey,String path) throws Exception {
		super();
		this.path = path;
		if (CommonTools.isBlank(aesKey)) {
		    useEncrypt = false;
		} else {
			this.aesKey = aesKey;
			useEncrypt = true;
		}
	}

	/**
	* Performs the getData operation
	* @return the getData result
	* */
	public Object getData() throws Exception {
		byte[] encData = Files.readAllBytes(Paths.get(path));
		byte[] dec = decrypt(encData);
		ByteArrayInputStream bais = null;
		ObjectInputStream oips = null;
		try {
			bais = new ByteArrayInputStream(dec);
			oips = new ObjectInputStream(bais);
			return oips.readObject();
		} finally {
			try {
				if (bais != null)
					bais.close();
			} catch (Exception e) {
				Logger.systemError(DataObject.class,e.getMessage(),e);
			}
			try {
				if (oips != null)
					oips.close();
			} catch (Exception e) {
				Logger.systemError(DataObject.class,e.getMessage(),e);
			}			
		}
	}

	/**
	* Performs the setObject operation
	* @param data - the data
	* */
	public void setObject(Object data) throws Exception {
	    setObjectTo(data);
	}

	/**
	* Performs the setObjectTo operation
	* @param data - the data
	* */
	private void setObjectTo(Object data) throws Exception {
		ByteArrayOutputStream baos = null;
		ObjectOutputStream objectOut = null;
		try {
			File file = new File(path);
			File parent = file.getParentFile();
			if (!parent.exists()) {
				parent.mkdirs();
			}
			baos = new ByteArrayOutputStream();
			objectOut = new ObjectOutputStream(baos);
			objectOut.writeObject(data);
			objectOut.flush();
			baos.flush();
			byte[] encBytes = encrypt(baos.toByteArray());
			Files.write(file.toPath(), encBytes);
		} finally {
			try {
				if (objectOut != null)
					objectOut.close();
			} catch (Exception e) {
				Logger.systemError(DataObject.class,e.getMessage(),e);
			}
			try {
				if (baos != null)
					baos.close();
			} catch (Exception e) {
				Logger.systemError(DataObject.class,e.getMessage(),e);
			}
		}
	}

	/**
	* Performs the encrypt operation
	* @param data - the data
	* @return the encrypt result
	* */
	private byte[] encrypt(byte[] data) throws Exception {
		if (useEncrypt) {
			return CipherTools.AESEncrypt(aesKey,data);
		} else {
			return data;
		}
	}

	/**
	* Performs the decrypt operation
	* @param encData - the encData
	* @return the decrypt result
	* */
	private byte[] decrypt(byte[] encData) throws Exception {
		if (useEncrypt) {
			return CipherTools.AESDecrypt(aesKey,encData);
		} else {
			return encData;
		}
	}

}