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

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
* Cloneable, serializable Map implementation backed by parallel key/value arrays (AbstractMap),
* preserving insertion order and suited to small result sets returned to scripts.
* */
public class ResultMap<K, V> extends AbstractMap<K, V> implements Cloneable, Serializable {

	private static final long serialVersionUID = 1L;

	static class Entry<K, V> implements Map.Entry<K, V>, Cloneable, Serializable {
		private static final long serialVersionUID = 1L;
		@SuppressWarnings("serial")
		protected K key;
		@SuppressWarnings("serial")
		protected V value;

		/**
		* Performs the Entry operation
		* @param key - the key
		* @param value - the value
		* @return the Entry result
		* */
		public Entry(K key, V value) {
			this.key = key;
			this.value = value;
		}

		/**
		* Performs the getKey operation
		* @return the getKey result
		* */
		public K getKey() {
			return key;
		}

		/**
		* Performs the getValue operation
		* @return the getValue result
		* */
		public V getValue() {
			return value;
		}

		/**
		* Performs the setValue operation
		* @param newValue - the newValue
		* @return the setValue result
		* */
		public V setValue(V newValue) {
			V oldValue = value;
			value = newValue;
			return oldValue;
		}

		/**
		* Compares this ResultMap with another object for equality
		* @param o - the o
		* @return the equals result
		* */
		public boolean equals(Object o) {
			if (!(o instanceof Map.Entry)) {
				return false;
			}
			@SuppressWarnings("unchecked")
			Map.Entry<K, V> e = (Map.Entry<K, V>) o;
			return (key == null ? e.getKey() == null : key.equals(e.getKey()))
					&& (value == null ? e.getValue() == null : value.equals(e.getValue()));
		}

		/**
		* Returns the hash code value for this ResultMap
		* @return the hashCode result
		* */
		public int hashCode() {
			int keyHash = (key == null ? 0 : key.hashCode());
			int valueHash = (value == null ? 0 : value.hashCode());
			return keyHash ^ valueHash;
		}

		/**
		* Returns a string representation of this ResultMap
		* @return the toString result
		* */
		public String toString() {
			return key + "=" + value;
		}
	}

	private transient Set<java.util.Map.Entry<K, V>> entries = null;
	private transient ArrayList<java.util.Map.Entry<K, V>> list;

	/**
	* Constructs a new ResultMap
	* */
	@SuppressWarnings("this-escape")
	public ResultMap() {
		super();
		list = new ArrayList<java.util.Map.Entry<K, V>>();
	}

	/**
	* Constructs a new ResultMap
	* @param K - the K
	* @param map - the map
	* */
	@SuppressWarnings("this-escape")
	public ResultMap(Map<K, V> map) {
		super();
		list = new ArrayList<java.util.Map.Entry<K, V>>();
		putAll(map);
	}

	/**
	* Constructs a new ResultMap
	* @param initialCapacity - the initialCapacity
	* */
	public ResultMap(int initialCapacity) {
		super();
		list = new ArrayList<java.util.Map.Entry<K, V>>(initialCapacity);
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		if (entries == null) {
			entries = new AbstractSet<java.util.Map.Entry<K, V>>() {

				/**
				* Performs the clear operation
				* */
				public void clear() {
					list.clear();
				}

				public Iterator<java.util.Map.Entry<K, V>> iterator() {
					return list.iterator();
				}

				/**
				* Performs the size operation
				* @return the size result
				* */
				public int size() {
					return list.size();
				}
			};
		}
		return entries;
	}

	@Override

	/*
	* Performs the put operation
	* @param key - the key
	* @param value - the value
	* @return the put result
	* */
	public V put(K key, V value) {
		int size = list.size();
		Entry<K, V> entry = null;
		int i;
		if (key == null) {
			for (i = 0; i < size; i++) {
				entry = (Entry<K, V>) (list.get(i));
				if (entry.getKey() == null) {
					break;
				}
			}
		} else {
			for (i = 0; i < size; i++) {
				entry = (Entry<K, V>) (list.get(i));
				if (key.equals(entry.getKey())) {
					break;
				}
			}
		}
		V oldValue = null;
		if (i < size) {
			oldValue = entry.getValue();
			entry.setValue(value);
		} else {
			list.add(new Entry<K, V>(key, value));
		}
		return oldValue;
	}

	@Override
	public ResultMap<K, V> clone() {
		return new ResultMap<K, V>(this);
	}

}
