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
import java.util.HashMap;
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
	* Column count above which {@link #put} stops scanning the entry list and
	* switches to {@link #index}. A result row carries one entry per selected
	* column, and the linear scan made a 43-column row cost roughly 900
	* {@code String.equals} calls; below this threshold the scan is cheaper than
	* building a HashMap for a two or three column row.
	* */
	private static final int INDEX_THRESHOLD = 8;
	/**
	* Key to position in {@link #list}; {@code null} while the map is still
	* below {@link #INDEX_THRESHOLD} and whenever the backing list is mutated
	* through a path other than {@link #put} (iterator removal, clear). Rebuilt
	* lazily by the next {@code put}. Keys must honour the {@code Map}
	* {@code equals}/{@code hashCode} contract, which every caller satisfies
	* since the key is a column name.
	* */
	private transient Map<K, Integer> index = null;

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
					index = null;
				}

				public Iterator<java.util.Map.Entry<K, V>> iterator() {
					final Iterator<java.util.Map.Entry<K, V>> it = list.iterator();
					// Wrapped so that a removal through the iterator invalidates
					// the position index; positions after the removed element
					// shift by one and would otherwise point at the wrong entry.
					return new Iterator<java.util.Map.Entry<K, V>>() {

						public boolean hasNext() {
							return it.hasNext();
						}

						public java.util.Map.Entry<K, V> next() {
							return it.next();
						}

						public void remove() {
							index = null;
							it.remove();
						}
					};
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

	/*
	* Performs the put operation
	* @param key - the key
	* @param value - the value
	* @return the put result
	* */
	@Override
	public V put(K key, V value) {
		int size = list.size();
		Map<K, Integer> idx = index;
		if (idx == null && size >= INDEX_THRESHOLD) {
			idx = index = buildIndex();
		}
		if (idx != null) {
			Integer at = idx.get(key);
			if (at != null) {
				@SuppressWarnings("unchecked")
				Entry<K, V> entry = (Entry<K, V>) (list.get(at.intValue()));
				V oldValue = entry.getValue();
				entry.setValue(value);
				return oldValue;
			}
			list.add(new Entry<K, V>(key, value));
			idx.put(key, size);
			return null;
		}
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

	/*
	* Builds the key to position index over the current entries.
	* @return the rebuilt index
	* */
	private Map<K, Integer> buildIndex() {
		int size = list.size();
		Map<K, Integer> idx = new HashMap<K, Integer>(Math.max(16, size * 2));
		for (int i = 0; i < size; i++) {
			@SuppressWarnings("unchecked")
			Entry<K, V> entry = (Entry<K, V>) (list.get(i));
			idx.put(entry.getKey(), i);
		}
		return idx;
	}

	@Override
	public ResultMap<K, V> clone() {
		return new ResultMap<K, V>(this);
	}

}
