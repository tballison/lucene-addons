package org.tallison.gramreaper;

import org.tallison.gramreaper.ingest.BuildIndex;
import org.tallison.gramreaper.terms.DumpTerms;

public class GramReaperCLI {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("Must specify a tool: BuildIndex or DumpTerms");
      return;
    }
    String tool = args[0];
    String[] opts = new String[args.length-1];
    System.arraycopy(args, 1, opts, 0, args.length-1);
    if (tool.equals("DumpTerms")) {
      DumpTerms.main(opts);
    } else if (tool.equals("BuildIndex")) {
      BuildIndex.main(opts);
    } else {
      System.err.println("Must specify a tool: BuildIndex or DumpTerms. " +
          "I don't recognize "+args[0] + " as a tool");
    }
  }
}
