import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

public class AnalyzerStemmedStopword extends Analyzer
{
    @Override
    protected TokenStreamComponents createComponents(String fieldName)
    {
        final Tokenizer source = new StandardTokenizer();
        TokenStream result = new StandardFilter(source);
        result = new EnglishPossessiveFilter(result);
        result = new LowerCaseFilter(result);
        result = new StopFilter(result, new CharArraySet(Utils.stopwords, true));
        result = new PorterStemFilter(result);
        return new TokenStreamComponents(source, result);
    }
}
