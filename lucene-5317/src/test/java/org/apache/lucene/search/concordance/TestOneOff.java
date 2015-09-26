package org.apache.lucene.search.concordance;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.LuceneTestCase;
import org.junit.Test;

/**
 * Created by TALLISON on 9/25/2015.
 */
public class TestOneOff extends LuceneTestCase {

  @Test
  public void testWildcard() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    Path dir = Paths.get("C:\\Users\\tallison\\Documents\\My Projects\\Rhapsode\\v03_dev\\resources\\index\\index");
    Directory directory = FSDirectory.open(dir);
    IndexReader reader = DirectoryReader.open(directory);
    IndexSearcher indexSearcher = new IndexSearcher(reader);
    SpanQuery q = new SpanMultiTermQueryWrapper<>(new PrefixQuery(new Term("content", "f")));
    TopDocs td = indexSearcher.search(q, 100);
    System.out.println(td.scoreDocs.length);
    q = (SpanQuery) q.rewrite(reader);
    SpanWeight w = q.createWeight(indexSearcher, false);
    AtomicInteger i = new AtomicInteger(0);
    Filter filter = new QueryWrapperFilter(new PrefixQuery(new Term("content", "f")));
    int spanCount = 0;
    for (LeafReaderContext ctx : reader.leaves()) {

      DocIdSet filterSet = filter.getDocIdSet(ctx, ctx.reader().getLiveDocs());
      if (filterSet == null) {
        return;
      }

      Spans spans = w.getSpans(ctx, SpanWeight.Postings.POSITIONS);
      if (spans == null) {
        continue;
      }
      DocIdSetIterator filterItr = filterSet.iterator();
      if (filterItr == null) {
        continue;
      }
      visitLeafReader(spans, filterItr, i);
    }
    System.out.println("Span Count: " + i.intValue());
  }

  public void visitLeafReader(Spans spans, DocIdSetIterator filterItr, AtomicInteger i) throws IOException {
      int filterDoc = -1;
      int spansDoc = spans.nextDoc();
      while (true) {
        if (spansDoc == DocIdSetIterator.NO_MORE_DOCS) {
          break;
        }
        filterDoc = filterItr.advance(spansDoc);
        if (filterDoc == DocIdSetIterator.NO_MORE_DOCS) {
          break;
        } else if (filterDoc > spansDoc) {
          while (spansDoc <= filterDoc) {
            spansDoc = spans.nextDoc();
            if (spansDoc == filterDoc) {
              System.out.println("spansDoc: "+spansDoc);
              //hit and process
              while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
                i.getAndIncrement();
              }
            } else {
              continue;
            }
          }
        } else if (filterDoc == spansDoc) {
          //hit and process
          System.out.println("spansDoc2: "+spansDoc);
          while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
            i.getAndIncrement();
          }
          //then iterate spans
          spansDoc = spans.nextDoc();
        } else if (filterDoc < spansDoc) {
          throw new IllegalArgumentException("FILTER doc is < spansdoc!!!");
        } else {
          throw new IllegalArgumentException("Something horrible happened");
        }
      }

  }

}
