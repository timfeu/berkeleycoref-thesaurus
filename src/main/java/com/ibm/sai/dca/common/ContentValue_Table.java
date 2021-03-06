/**
 *   Copyright (c) 2012 IBM Corp.
 *   
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *         
 *   http://www.apache.org/licenses/LICENSE-2.0
 *               	
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *               	               
 *   @author: Bonaventura Coppola (coppolab@gmail.com)
 *   
 */


package com.ibm.sai.dca.common;

import java.util.HashMap;
import java.util.Set;

public class ContentValue_Table extends ContentValue {

	private HashMap<String, Double> map;
	
	public ContentValue_Table() {
		map = new HashMap<String, Double>();
	}
	
	public void dump() {
		for (String key : map.keySet()) {
			System.out.println(key+"\t"+map.get(key));
		}
		System.out.println("Size: "+map.size());
	}
	
	public Double get(String key) {
		return map.get(key);
	}
	
	public int size() {
		return map.size();
	}
	
	public Set<String> keySet() {
		return map.keySet();
	}

	public void put(String key, Double val) {
		map.put(key, val);
	}
}
