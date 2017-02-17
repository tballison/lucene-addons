/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tallison.gramreaper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

public class DumpTerms {
  static Options OPTIONS;

  static {
    Option field = new Option("f", "field",
        true, "Lucene field to process");
    field.setRequired(true);

    Option indexPath = new Option("i", "index",
        true, "Lucene index to process");
    indexPath.setRequired(true);

    OPTIONS = new Options()
        .addOption(field)
        .addOption(indexPath)
        .addOption("n", "topN", true, "top n most frequent terms")
        .addOption("min", true, "minimum doc frequency")
        .addOption("max", true, "maximum doc frequency")
        .addOption("maxP", true, "maximum doc freq percentage")
        .addOption("minP", true, "minimum doc freq percentage")
        .addOption("includeDF", false, "include the document frequency in the output; default is false")
        .addOption("s", true, "stop words file -- UTF-8, one word per row")
        .addOption("o", true, "output file");
  }

  public static void usage() {
    HelpFormatter helpFormatter = new HelpFormatter();
    helpFormatter.printHelp(
        80,
        "java -jar gramreaper-x.y.jar DumpTerms -i lucene_index -min 10 -o output.txt",
        "Tool: DumpTerms",
        OPTIONS,
        "");

  }

  private final DumpTermsConfig config;

  public DumpTerms(DumpTermsConfig config) {
    this.config = config;
  }

  private static class DumpTermsConfig {
    Path indexPath;
    String field;
    Integer topN = -1;
    Long minDocFreq = -1l;
    Long maxDocFreq = -1l;
    Double minDocPercentage = -1.0d;
    Double maxDocPercentage = -1.0d;
    boolean includeDocFreq = false;
    Set<String> stopWords = new HashSet<>();
    Path outputFile;

    public static DumpTermsConfig build(String[] args) throws IOException {
      DefaultParser parser = new DefaultParser();
      CommandLine commandLine;
      DumpTermsConfig config = new DumpTermsConfig();
      try {
        commandLine = parser.parse(OPTIONS, args);
        if (commandLine.hasOption("o")) {
          config.outputFile = Paths.get(commandLine.getOptionValue("o"));
        }
        if (commandLine.hasOption("i")) {
          config.indexPath = Paths.get(commandLine.getOptionValue("i"));
        }
        if (commandLine.hasOption("n")) {
          config.topN = Integer.parseInt(commandLine.getOptionValue("n"));
        }
        if (commandLine.hasOption("min")) {
          config.minDocFreq = Long.parseLong(commandLine.getOptionValue("min"));
        }
        if (commandLine.hasOption("max")) {
          config.maxDocFreq = Long.parseLong(commandLine.getOptionValue("max"));
        }
        if (commandLine.hasOption("minP")) {
          config.minDocPercentage = Double.parseDouble(commandLine.getOptionValue("minP"));
        }
        if (commandLine.hasOption("maxP")) {
          config.maxDocPercentage = Double.parseDouble(commandLine.getOptionValue("maxP"));
        }
        if (commandLine.hasOption("f")) {
          config.field = commandLine.getOptionValue("f");
        }
        if (commandLine.hasOption("s")) {
          BufferedReader reader = Files.newBufferedReader(
              Paths.get(commandLine.getOptionValue("s")),
              StandardCharsets.UTF_8);
          String line = reader.readLine();
          while (line != null) {
            line = line.trim();
            if (line.length() == 0 || line.startsWith("#")) {
              line = reader.readLine();
              continue;
            }
            config.stopWords.add(line);
            line = reader.readLine();
          }
          reader.close();
        }
        if (commandLine.hasOption("includeDF")) {
          config.includeDocFreq = true;
        }

      } catch (ParseException e) {
        System.err.println(e.getMessage());
        usage();
        return null;
      }
      return config;
    }

  }

  public static void main(String[] args) throws Exception {
    DumpTermsConfig config = DumpTermsConfig.build(args);
    if (config == null) {
      return;
    }
    DumpTerms dumpTerms = new DumpTerms(config);
    dumpTerms.execute();


  }

  private void execute() throws IOException {
    IndexReader reader = DirectoryReader.open(FSDirectory.open(config.indexPath));
    LeafReader leafReader = SlowCompositeReaderWrapper.wrap(reader);
    if (config.topN > -1) {
      dumpTopN(leafReader);
    }
  }

  private void dumpTopN(LeafReader leafReader) throws IOException {
    TokenCountPriorityQueue queue = new TokenCountPriorityQueue(config.topN);
    Terms terms = leafReader.terms(config.field);
    TermsEnum termsEnum = terms.iterator();
    BytesRef bytesRef = termsEnum.next();
    int docsWThisField = leafReader.getDocCount(config.field);
    while (bytesRef != null) {
      int df = termsEnum.docFreq();
      if (config.minDocFreq > -1 && df < config.minDocFreq) {
        bytesRef = termsEnum.next();
        continue;
      }
      if (config.minDocPercentage > -1.0d
          && (double)df/(double)docsWThisField < config.minDocPercentage) {
        bytesRef = termsEnum.next();
        continue;
      }

      if (queue.top() == null || queue.size() < config.topN ||
          df >= queue.top().getValue()) {
        String t = bytesRef.utf8ToString();
        if (! config.stopWords.contains(t)) {
          queue.insertWithOverflow(new TokenIntPair(t, df));
        }
      }
      bytesRef = termsEnum.next();
    }
    if (config.outputFile == null) {
      for (TokenIntPair tp : queue.getArray()) {
        String row = (config.includeDocFreq) ?
            clean(tp.token)+"\t"+tp.value : clean(tp.token);
        System.out.println(row);
      }
    } else {
      BufferedWriter writer =
          Files.newBufferedWriter(config.outputFile, StandardCharsets.UTF_8);
      for (TokenIntPair tp : queue.getArray()) {
        String row = (config.includeDocFreq) ?
            clean(tp.token)+"\t"+tp.value : clean(tp.token);
        writer.write(row+"\n");

      }
      writer.flush();
      writer.close();
    }
  }

  private static String clean(String s) {
    if (s == null) {
      return "";
    }
    return s.replaceAll("\\s+", " ");
  }
}
