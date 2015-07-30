package contrast;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CharArrayMap;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.corpus.stats.IDFCalc;
import org.apache.lucene.corpus.stats.TFIDFPriorityQueue;
import org.apache.lucene.corpus.stats.TermIDF;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import org.apache.lucene.util.mutable.MutableValueInt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QueryToCorpusContraster {

  private final int maxDocs;
  private final IndexSearcher searcher;
  private final Version version;
  private boolean ignoreCase = true;
  private Analyzer analyzer = null;
  private int maxTokens = 10000;

  //if the term doesn't show up in this many docs, ignore!
  private int minTermFreq = 10;

  public QueryToCorpusContraster(Version version, IndexSearcher searcher, int maxDocs) {
    this.searcher = searcher;
    this.maxDocs = maxDocs;
    this.version = version;
  }


  public List<TermIDF> contrast(Query query, String fieldName, int numResults)
      throws IOException {
    TopScoreDocCollector results = TopScoreDocCollector.create(maxDocs);
    searcher.search(query, results);

    ScoreDoc[] scoreDocs = results.topDocs().scoreDocs;
    //if there are fewer documents than minTermFreq
    //return empty list now
    if (scoreDocs.length < minTermFreq) {
      return new ArrayList<TermIDF>();
    }

    //total hack
    int initialSize = scoreDocs.length * 100;
    CharArrayMap<MutableValueInt> map = new CharArrayMap<MutableValueInt>(initialSize, ignoreCase);
    CharArraySet tmpSet = new CharArraySet(100, ignoreCase);
    Set<String> selector = new HashSet<String>();
    selector.add(fieldName);

    for (ScoreDoc scoreDoc : scoreDocs) {
      //get terms from doc
      processDoc(scoreDoc.doc, fieldName, selector, tmpSet);
      //now update global doc freqs
      Iterator<Object> it = tmpSet.iterator();
      while (it.hasNext()) {
        char[] token = (char[]) it.next();
        MutableValueInt docCount = map.get(token, 0, token.length);
        if (docCount == null) {
          docCount = new MutableValueInt();
          docCount.value = 1;
        } else {
          docCount.value++;
        }
        map.put(token, docCount);
      }
      tmpSet.clear();
    }

    return getResults(fieldName, map, numResults);
  }


  private List<TermIDF> getResults(String fieldName,
                                   CharArrayMap<MutableValueInt> map, int numResults) {
    TFIDFPriorityQueue queue = new TFIDFPriorityQueue(numResults);
    IDFCalc idfCalc = new IDFCalc(searcher.getIndexReader());
    int tf = -1;
    double idf = -1.0;
    int minTf = minTermFreq;
    String text = null;
    //make more efficient
//    Term reusableTerm = new Term(fieldName, "");
    for (Map.Entry<Object, MutableValueInt> entry : map.entrySet()) {

      tf = entry.getValue().value;
      if (tf < minTf)
        continue;

      text = new String((char[]) entry.getKey());
      // calculate idf for potential phrase
      try {
        idf = idfCalc.singleTermIDF(new Term(fieldName, text));
      } catch (IOException e) {
        throw new RuntimeException("Error trying to calculate IDF: " + e.getMessage());
      }
      int estimatedDF = (int) Math.max(1, Math.round(idfCalc.unIDF(idf)));

      TermIDF r = new TermIDF(text, estimatedDF, tf, idf);

      queue.insertWithOverflow(r);
    }
    List<TermIDF> results = new LinkedList<TermIDF>();

    while (queue.size() > 0) {
      results.add(0, queue.pop());
    }
    return results;
  }

  private void processDoc(int docid, String fieldName, Set<String> selector,
                          CharArraySet set) throws IOException {
    Terms terms = searcher.getIndexReader().getTermVector(docid, fieldName);
    if (terms != null) {
      TermsEnum te = terms.iterator();
      BytesRef bytes = te.next();
      while (bytes != null) {
        set.add(bytes);
      }
    } else if (analyzer != null) {
      Document document = searcher.doc(docid, selector);
      IndexableField[] fields = document.getFields(fieldName);
      if (fields == null) {
        return;
      }
      for (IndexableField field : fields) {
        String s = field.stringValue();
        //is this possible
        if (s == null) {
          continue;
        }
        processFieldEntry(fieldName, s, set);
      }

    } else {
      throw new IllegalArgumentException("The field must have a term vector or the analyzer must" +
          " not be null.");
    }
  }

  private void processFieldEntry(String fieldName, String s, CharArraySet set) throws IOException {
    TokenStream ts = analyzer.tokenStream(fieldName, s);
    CharTermAttribute cattr = ts.getAttribute(CharTermAttribute.class);
    ts.reset();
    while (ts.incrementToken()) {
      set.add(cattr.toString());
    }
    ts.end();
    ts.close();
  }

  /**
   * Sets the analyzer to be used if term vectors are not stored.
   *
   * @param analyzer  analyzer to be used if term vectors are not stored
   * @param maxTokens maximum number of tokens to analyze. If < 0,
   *                  all tokens will be analyzed.
   */
  public void setAnalyzer(Analyzer analyzer, int maxTokens) {
    this.analyzer = analyzer;
    this.maxTokens = maxTokens;
  }
}
