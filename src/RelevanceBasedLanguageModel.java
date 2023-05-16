import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;
import java.util.*;

public class RelevanceBasedLanguageModel
{
    HashMap<Integer, BagOfWords> R;
    HashMap<String, TermFrequency> terms_in_R;
    HashMap<Integer, Double> P_q_given_D_R;
    int num_fb_docs = 20;
    double alpha = 0.6f;
    long total_TF_in_R;
    int num_fb_terms = 30;
    private double lambda = 0.6f;

    public RelevanceBasedLanguageModel(IndexReader indexReader, TopDocs topDocs, String[] analyzedQuery) throws IOException
    {
        R = new HashMap<>();
        terms_in_R = new HashMap<>();
        P_q_given_D_R = new HashMap<>();
        Fields fields = MultiFields.getFields(indexReader);
        Terms terms = fields.terms("contents");
        total_TF_in_R = terms.getSumTotalTermFreq();

        for (int i = 0; i < Math.min(topDocs.scoreDocs.length, num_fb_docs); i++)
        {
            int id = topDocs.scoreDocs[i].doc;
            BagOfWords D = BagOfWords.create(id, indexReader);
            R.put(id, D);

            for (Map.Entry<String, TermFrequency> entrySet : D.termFrequencies.entrySet())
            {
                String term = entrySet.getKey();
                TermFrequency freq = entrySet.getValue();
                TermFrequency t_in_R = terms_in_R.get(term);
                if (t_in_R != null)
                {
                    freq.tf += t_in_R.tf;
                    terms_in_R.put(term, freq);
                }
                else
                {
                    terms_in_R.put(term, new TermFrequency(term, freq.tf));
                }
            }
        }

        for (Map.Entry<Integer, BagOfWords> entrySet : R.entrySet())
        {
            int id = entrySet.getKey();
            BagOfWords D = entrySet.getValue();
            // A4 P(q|D, R)
            double p = 0;
            for (String q : analyzedQuery)
                p += Math.log(MLE(q, D));
            P_q_given_D_R.put(id, p);
        }

    }

    public double MLE(String q, BagOfWords d)
    {

        TermFrequency D = d.termFrequencies.get(q);
        TermFrequency R = terms_in_R.get(q);
        return MLE(R, D, d);
    }

    public double MLE(TermFrequency R, TermFrequency D, BagOfWords d)
    {
        if (D == null)
            return 1 / (double) d.totalTermFrequency;
        return D.tf / (double) d.totalTermFrequency;
    }

    public double MLE_rerank(TermFrequency R, TermFrequency D, BagOfWords d)
    {
        // A2: doesn't work for reranking
        if (R == null)
            return 1.f;
        return (D != null) ? (alpha * D.tf / (double) d.totalTermFrequency) : 0 + (1.0f - alpha) * (R.tf / (double) total_TF_in_R);
    }

    public HashMap<String, Pair> RM1()
    {
        List<Pair> P_t_given_q_List = new ArrayList<>(terms_in_R.size());
        for (Map.Entry<String, TermFrequency> termF : terms_in_R.entrySet())
        {
            String t = termF.getKey();
            TermFrequency f = termF.getValue();
            double p = 0;
            for (int D : R.keySet()) // sum over each D in D_R
            {
                p += Math.log(MLE(f, R.get(D).termFrequencies.get(t), R.get(D))) + P_q_given_D_R.get(D);
            }
            P_t_given_q_List.add(new Pair(t, p));
        }
        HashMap<String, Pair> P_t_given_q = sortMap(P_t_given_q_List);
        return P_t_given_q;
    }

    private HashMap<String, Pair> sortMap(List<Pair> P_t_given_q_List)
    {
        Collections.sort(P_t_given_q_List, (t1, t2) -> Double.compare(t2.p, t1.p));
        HashMap<String, Pair> P_t_given_q = new LinkedHashMap<>();
        for (Pair singleTerm : P_t_given_q_List)
        {
            P_t_given_q.put(singleTerm.w, new Pair(singleTerm.w, singleTerm.p));
        }
        return P_t_given_q;
    }

    public HashMap<String, Pair> RM3(String[] q)
    {
        HashMap<String, Pair> P_t_given_q = RM1();

        List<Pair> listP_t_given_q = new ArrayList<>(P_t_given_q.values());
//        listP_t_given_q = listP_t_given_q.subList(0, num_fb_terms);
        P_t_given_q = new LinkedHashMap<>();
        double norm = 0;
        for (Pair each : listP_t_given_q)
        {
            P_t_given_q.put(each.w, new Pair(each.w, each.p));
            norm += Math.exp(each.p);
        }
        for (Pair wp : P_t_given_q.values())
        {
            wp.p = Math.exp(wp.p) / norm;
        }

        norm = 0;
        for (Pair value : P_t_given_q.values())
        {
            value.p = value.p * lambda;
            norm += value.p;
        }

        for (String t : q)
        {
            Pair lambdaRM1 = P_t_given_q.get(t);
            double lambdaMLE = (1.0 - lambda) * P_MLE_t_given_q(q, t);
            if (lambdaRM1 != null)
            {
                lambdaRM1.p += lambdaMLE;
                norm += lambdaMLE;
                P_t_given_q.put(t, lambdaRM1);
            }
            else
            {
                P_t_given_q.put(t, new Pair(t, lambdaMLE));
            }
        }

        for (Map.Entry<String, Pair> entrySet : P_t_given_q.entrySet())
        {
            Pair wp = entrySet.getValue();
            wp.p /= norm;
        }
        P_t_given_q = sortMap(new ArrayList<>(P_t_given_q.values()));
        return P_t_given_q;
    }


    public double P_MLE_t_given_q(String[] q, String t)
    {
        int count = 0;
        for (String each : q)
        {
            if (t.equals(each))
            {
                count++;
            }
        }
        return count / (double) q.length;
    }

    public void reRank(HashMap<String, Pair> P_t_given_q, TopDocs topDocs, IndexReader indexReader, String[] analyzedQuery) throws IOException
    {
        List<String> expandedQuery = new LinkedList<>();
        for (String q : analyzedQuery)
        {
            expandedQuery.add(q);
        }
        int limit = 0;
        for (String t : P_t_given_q.keySet())
        {
            if (limit == num_fb_terms)
            {
                break;
            }
            expandedQuery.add(t);
            limit += 1;
        }
        for (ScoreDoc scoreDoc : topDocs.scoreDocs)
        {
            scoreDoc.score = 0;
            BagOfWords dv = BagOfWords.create(scoreDoc.doc, indexReader);
            for (String q : expandedQuery)
            {
                scoreDoc.score += Math.log(MLE_rerank(terms_in_R.get(q), dv.termFrequencies.get(q), dv));
            }
        }
        Arrays.sort(topDocs.scoreDocs, (o1, o2) -> Double.compare(o2.score, o1.score));
    }
}
