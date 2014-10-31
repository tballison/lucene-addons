package org.apache.lucene.corpus.stats;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class TestTermStatComparators {

  @Test
  public void testDescendingDFComparator() {
    TermDFTF[] tst = new TermDFTF[]{
        new TermDFTF("cde", 1,2),
        new TermDFTF("bcd", 1,2),
        new TermDFTF("abc", 1,2),
        new TermDFTF("bbc", 2,2),
        new TermDFTF("bbd", 2,2),
        new TermDFTF("eee", 3,2),
    };
    
    
    List<TermDFTF> list = Arrays.asList(tst);
    Collections.sort(list);
    for (int i = 0; i < list.size(); i++){
      System.out.println(list.get(i).toString());
    }
  }

}
