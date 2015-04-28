package org.apache.lucene.corpus.stats;


import org.apache.lucene.util.PriorityQueue;

public class TFIDFPriorityQueue extends PriorityQueue<TermIDF> {
  public TFIDFPriorityQueue(int maxSize) {
    super(maxSize);
  }

  protected boolean lessThan(TermIDF a, TermIDF b) {
    if (a.getTFIDF() < b.getTFIDF()) {
      return true;
    } else if (a.getTFIDF() == b.getTFIDF()) {
      if (a.getTerm().compareTo(b.getTerm()) > 0) {
        return true;
      }
    }
    return false;
  }
}

