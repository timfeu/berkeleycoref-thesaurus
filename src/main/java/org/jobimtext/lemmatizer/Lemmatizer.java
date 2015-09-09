package org.jobimtext.lemmatizer;
/*******************************************************************************
 * Copyright 2012
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
import java.io.InputStream;

import static de.uni_leipzig.asv.utils.Utils.apply_rule;
import de.uni_leipzig.asv.utils.Pretree;

/**
 * This class uses the Patricia Prie Tree to lemmatize 
 * 
 * @author Martin Riedl (riedl@cs.tu-darmstadt.de)
 *
 */
public class Lemmatizer {
	private Pretree p;

	public Lemmatizer(InputStream model) {
		p = new Pretree();
		p.load(model);
	}
	
	public Lemmatizer(String model) {
		p = new Pretree();
		p.load(model);
	}
	/**
	 * Lemmatizes a given word using the instantiated model
	 * @param word
	 * @return
	 */
	public String lemmatize(String word) {

		String pattern = p.classify(word);
		if(pattern.contains(";")){
			if(pattern.contains("0")){
				return word;
			}else{
				return word;
			}
		}
		try {
			String lemma = apply_rule(word, pattern);
			if(lemma.length()==0){
				return word;
			}
			if(!word.contains(";")&&lemma.contains(";")){
				lemma = apply_rule(word, pattern.substring(0,pattern.indexOf(";")));
			}
			return lemma;
		} catch (StringIndexOutOfBoundsException e) {
			return word;
		}

	}

	
	
}
