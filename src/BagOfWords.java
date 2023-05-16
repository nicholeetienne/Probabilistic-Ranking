import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.HashMap;

public class BagOfWords
{
    HashMap<String, TermFrequency> termFrequencies;
    int totalTermFrequency;

    BagOfWords()
    {
        termFrequencies = new HashMap<>();
    }

    public static BagOfWords create(int luceneDocId, IndexReader indexReader) throws IOException
    {

        BagOfWords dv = new BagOfWords();
        int totalTermFrequency = 0;
        String fieldName = "contents";
        Terms terms = indexReader.getTermVector(luceneDocId, fieldName);
        TermsEnum iterator = terms.iterator();
        BytesRef byteRef;
        while ((byteRef = iterator.next()) != null)
        {
            String term = new String(byteRef.bytes, byteRef.offset, byteRef.length);
            long termFreq = iterator.totalTermFreq();
            totalTermFrequency += termFreq;
            dv.termFrequencies.put(term, new TermFrequency(term, termFreq));
        }
        dv.totalTermFrequency = totalTermFrequency;

        return dv;
    }
}
