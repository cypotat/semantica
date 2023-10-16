package dev.potat;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.Timing;

import java.util.*;

public class KeywordsExtractor {
    private static final double INV_E = 1.0 / Math.E;

    // configs
    private final int maxCount;
    private final float minWeight;
    private final Map<String, Float> nerTagsWeights;
    private final Map<String, Float> posPrefixesWeights;

    // pipeline
    StanfordCoreNLP pipeline;


    public KeywordsExtractor(
            int maxCount,
            float minWeight,
            Map<String, Float> nerTagsWeights,
            Map<String, Float> posPrefixesWeights
    ) {
        this.maxCount = maxCount;
        this.minWeight = minWeight;
        this.nerTagsWeights = nerTagsWeights;
        this.posPrefixesWeights = posPrefixesWeights;

        // build pipeline
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,pos,lemma,ner");
        props.setProperty("ner.applyFineGrained", "false");
        props.setProperty("ner.model",
                DefaultPaths.DEFAULT_NER_THREECLASS_MODEL + ',' + DefaultPaths.DEFAULT_NER_CONLL_MODEL);

        this.pipeline = new StanfordCoreNLP(props);
    }

    public LinkedHashMap<String, Float> extract(String text) {
        System.out.println("Extracting keywords from text...");
        LinkedHashMap<String, KeywordInfo> keywords = new LinkedHashMap<>();

        Timing timer = new Timing();
        CoreDocument document = pipeline.processToCoreDocument(text);
        for (CoreLabel tok : document.tokens()) {
            KeywordInfo info = keywords.get(tok.lemma());
            if (info == null) {
                float weight = calculateTokenWeight(tok);
                info = new KeywordInfo(1, weight);
                keywords.put(tok.lemma(), info);
            } else {
                info.incrementCount();
            }
        }

        System.out.println("Time elapsed: " + timer.toSecondsString());

        LinkedHashMap<String, Float> result = new LinkedHashMap<>();
        for (Map.Entry<String, KeywordInfo> entry : keywords.entrySet()) {
            String keyword = entry.getKey();
            float weight = entry.getValue().balancedWeight();
            if (weight >= minWeight) {
                result.put(keyword, weight);
            }

            // TODO: filter by maxCount:
            // tokensToRemove = len(keywords) - maxCount
            // get (tokensToRemove * 1.2) at the end of the list
            // and remove the tokensToRemove tokens with the lowest weight
        }

        return result;
    }

    private float calculateTokenWeight(CoreLabel tok) {
        float weight = 0.0f;
        String nerTag = tok.ner();
        if (nerTagsWeights.containsKey(nerTag)) weight += nerTagsWeights.get(nerTag);
        for (Map.Entry<String, Float> entry : posPrefixesWeights.entrySet()) {
            String prefix = entry.getKey();
            float w = entry.getValue();
            if (tok.tag().startsWith(prefix)) {
                weight += w;
                break;
            }
        }
        return weight;
    }

    public static class KeywordInfo {
        private int count;
        private final float weight;

        public KeywordInfo(int count, float weight) {
            this.count = count;
            this.weight = weight;
        }

        public void incrementCount() {
            count++;
        }

        public float balancedWeight() {
            return weight * (float) Math.pow(count, INV_E);
        }
    }

    public static class Builder {
        private int maxCount = 10;
        private float minWeight = 1;
        private Map<String, Float> nerTagsWeights = Map.of(
                "PERSON", 1.0f,
                "LOCATION", 1.0f,
                "ORGANIZATION", 1.0f,
                "MISC", 1.0f
        );
        private Map<String, Float> posPrefixesWeights = Map.of(
                "NN", 1.0f,
                "JJ", 1.0f
        );

        public Builder maxCount(int maxCount) {
            this.maxCount = maxCount;
            return this;
        }

        public Builder minWeight(float minWeight) {
            this.minWeight = minWeight;
            return this;
        }

        public Builder nerTagsWeights(Map<String, Float> nerTagsWeights) {
            this.nerTagsWeights = nerTagsWeights;
            return this;
        }

        public Builder posPrefixesWeights(Map<String, Float> posPrefixesWeights) {
            this.posPrefixesWeights = posPrefixesWeights;
            return this;
        }

        public KeywordsExtractor build() {
            return new KeywordsExtractor(maxCount, minWeight, nerTagsWeights, posPrefixesWeights);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
