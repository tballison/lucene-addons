package org.tallison.lucene.search.concordance.charoffsets;

import java.io.IOException;
import java.util.Set;

public interface DocTokenOffsetsVisitor {

  /**
   *
   * @return doctokenoffsets for reuse
   */
  public DocTokenOffsets getDocTokenOffsets();
  public Set<String> getFields();
  public boolean visit(DocTokenOffsets docTokenOffsets) throws IOException, TargetTokenNotFoundException;
}
