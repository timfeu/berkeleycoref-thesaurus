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

import java.io.*;
import java.util.HashMap;
import java.util.Map;


/**
 * This class lemmatizes a word according to a given POS tag. The pos tags and the according model
 * are given in the constructor. For pos tags that are not considered as prefix the input will we
 * given as output.
 * <p>
 * <p>
 * PosLemmatizer l = new PosLemmatizer(new String[]{"adj_model.ptrie","noun_model.ptrie","verb_model"}
 *
 * @author Martin Riedl (riedl@cs.tu-darmstadt.de)
 */
public class PosLemmatizer {
    private HashMap<String, Lemmatizer> lemmatizers = new HashMap<String, Lemmatizer>();

    private static InputStream openBuffered(File file) throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(file));
    }

    public static PosLemmatizer createEnglishNounVerbAdjectiveLemmatizer() throws IOException {
        InputStream adjStream = null;
        InputStream nounStream = null;
        InputStream verbStream = null;

        try {
            adjStream = openBuffered(new File("data/en-adj-red.txt.ptrie"));
            nounStream = openBuffered(new File("data/en-noun-red-large.txt.ptrie"));
            verbStream = openBuffered(new File("data/en-verb-red.txt.ptrie"));
        } catch (IOException e) {
            if (adjStream != null) adjStream.close();
            if (nounStream != null) nounStream.close();
            if (verbStream != null) verbStream.close();
            throw e;
    }

        InputStream[] models = new InputStream[]{adjStream, nounStream, verbStream};
        String[] poss = new String[]{"JJ", "NN", "VB"};

        return new PosLemmatizer(models, poss);
    }

    public static PosLemmatizer createGermanNounVerbAdjectiveLemmatizer() {
        ClassLoader cl = PosLemmatizer.class.getClassLoader();
        InputStream[] models = new InputStream[]{cl.getResourceAsStream("de-adjectives.tree"),
                cl.getResourceAsStream("de-nouns.tree"),
                cl.getResourceAsStream("de-verbs.tree"),
        };
        String[] poss = new String[]{"ADJ", "N", "V"};

        return new PosLemmatizer(models, poss);
    }

    /**
     * Constructor expects equally sized arrays for model paths and pos tag prefixes
     *
     * @param models
     * @param posTagPrefixes
     */
    public PosLemmatizer(String[] models, String[] posTagPrefixes) {
        if (models.length != posTagPrefixes.length) {
            throw new IllegalArgumentException("The number of models given, " +
                    "must match to the number of posTagPrefixes");
        }
        for (int i = 0; i < models.length; i++) {
            lemmatizers.put(posTagPrefixes[i], new Lemmatizer(models[i]));
        }
    }

    /**
     * Constructor expects equally sized arrays for model paths and pos tag prefixes
     *
     * @param models
     * @param posTagPrefixes
     */
    public PosLemmatizer(InputStream[] models, String[] posTagPrefixes) {
        if (models.length != posTagPrefixes.length) {
            throw new IllegalArgumentException("The number of models given, " +
                    "must match to the number of posTagPrefixes");
        }
        for (int i = 0; i < models.length; i++) {
            lemmatizers.put(posTagPrefixes[i], new Lemmatizer(models[i]));
        }
    }


    /**
     * Lemmatizes a word according to POS tag. If no prefix of the given pos prefixes matches
     * the word and the pos will be returned unchanged.
     *
     * @param word
     * @param pos
     * @return
     */
    public String lemmatizeWord(String word, String pos) {
        for (Map.Entry<String, Lemmatizer> e : lemmatizers.entrySet()) {
            if (pos.startsWith(e.getKey())) {
                String lemma = e.getValue().lemmatize(word);
                return lemma;
            }
        }
        return word;

    }
}
