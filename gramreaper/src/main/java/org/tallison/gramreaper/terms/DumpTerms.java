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

package org.tallison.gramreaper.terms;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.tallison.util.SlowCompositeReaderWrapper;

public class DumpTerms {
  static Options OPTIONS;

  static {
    Option field = new Option("f", "field",
        true, "Lucene field to process");

    Option indexPath = new Option("i", "index",
        true, "Lucene index to process");

    Option indexDirPath = new Option("indexDir", true,
        "use this if you have a directory with multiple indices");

    OPTIONS = new Options()
        .addOption(field)
        .addOption(indexPath)
        .addOption(indexDirPath)
        .addOption("n", "topN", true, "top n most frequent terms")
        .addOption("min", true, "minimum doc frequency")
        .addOption("max", true, "maximum doc frequency")
        .addOption("maxP", true, "maximum doc freq percentage")
        .addOption("minP", true, "minimum doc freq percentage")
        .addOption("df", false, "include the document frequency in the output; default is false")
        .addOption("tf", false, "include term frequency in the output; default is false")
        .addOption("sortDF", false, "sort results by descending document frequency (default)")
        .addOption("sortTF", false, "sort results by descending term frequency")
        .addOption("s", true, "stop words file -- UTF-8, one word per row")
        .addOption("startWords", true, "start words file -- UTF-8, one word per row; every word will be added to the list")
        .addOption("o", true, "output file")
    ;
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

    enum SORT {
        TF,
        DF
    }
    Path indexDirPath;
    Path indexPath;
    String field = null;
    Integer topN = -1;
    Long minDocFreq = -1l;
    Long maxDocFreq = -1l;
    Double minDocPercentage = -1.0d;
    Double maxDocPercentage = -1.0d;
    boolean includeDocFreq = false;
    boolean includeTermFreq = false;
    Set<String> stopWords = new HashSet<>();
    Set<String> startWords = new HashSet<>();
    Path outputFile;
    SORT sort = SORT.DF;

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
        } else {
          config.topN = 100;
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
          loadSet(commandLine.getOptionValue("s"), config.stopWords);
        }
        if (commandLine.hasOption("startWords")) {
          loadSet(commandLine.getOptionValue("startWords"), config.startWords);
        }
        if (commandLine.hasOption("df")) {
          config.includeDocFreq = true;
        }
        if (commandLine.hasOption("tf")) {
          config.includeTermFreq = true;
        }
        if (commandLine.hasOption("indexDir")) {
          config.indexDirPath = Paths.get(commandLine.getOptionValue("indexDir"));
        }
        if (config.indexDirPath == null && config.indexPath == null) {
          throw new ParseException("Must specify either an indexDir or an indexPath");
        }
        if (commandLine.hasOption("sortTF")) {
          config.sort = SORT.TF;
        }
      } catch (ParseException e) {
        System.err.println(e.getMessage());
        usage();
        return null;
      }
      return config;
    }

    private static void loadSet(String filePath, Set<String> stopWords) throws IOException {
      BufferedReader reader = Files.newBufferedReader(
          Paths.get(filePath),
          StandardCharsets.UTF_8);
      String line = reader.readLine();
      while (line != null) {
        line = line.trim();
        if (line.length() == 0 || line.startsWith("#")) {
          line = reader.readLine();
          continue;
        }
        stopWords.add(line);
        line = reader.readLine();
      }
      reader.close();

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
    if (config.indexPath != null) {
      processIndex(config.indexPath);
    } else {
      for (File f : config.indexDirPath.toFile().listFiles()) {
        try {
          processIndex(f.toPath());
        } catch (IOException e) {
          System.err.println("couldn't open index: "+ f.getName());
        }
      }
    }
  }

  private void processIndex(Path indexPath) throws IOException {
      IndexReader reader = DirectoryReader.open(FSDirectory.open(indexPath));
      LeafReader leafReader = SlowCompositeReaderWrapper.wrap(reader);
      if (config.topN > -1) {
        dumpTopN(leafReader);
      }
  }

  private void dumpTopN(LeafReader leafReader) throws IOException {
    if (config.field == null) {
      FieldInfos fieldInfos = leafReader.getFieldInfos();
      for (FieldInfo fieldInfo : fieldInfos) {
        dumpTopNField(leafReader, fieldInfo.name);
      }
    } else {
      dumpTopNField(leafReader, config.field);
    }
  }

  private void dumpTopNField(LeafReader leafReader, String field) throws IOException {
    AbstractTokenTFDFPriorityQueue queue = config.sort.equals(DumpTermsConfig.SORT.DF) ?
        new TokenDFPriorityQueue(config.topN) : new TokenTFPriorityQueue(config.topN);
    Terms terms = leafReader.terms(field);
    if (terms == null) {
      StringBuilder sb = new StringBuilder();
      int i = 0;
      for (FieldInfo fieldInfo : leafReader.getFieldInfos()) {
        if (i++ > 0) {
          sb.append("\n");
        }
        sb.append(fieldInfo.name);

      }
      throw new RuntimeException("I can't find field \""+field+"\".\n"+
        "I only see:\n"+sb.toString());
    }
    TermsEnum termsEnum = terms.iterator();
    BytesRef bytesRef = termsEnum.next();
    int docsWThisField = leafReader.getDocCount(field);
    while (bytesRef != null) {
      int df = termsEnum.docFreq();
      long tf = termsEnum.totalTermFreq();
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
          (config.sort.equals(DumpTermsConfig.SORT.DF) ? df >= queue.top().df: tf > queue.top().tf)) {
        String t = bytesRef.utf8ToString();
        if (
            ! config.stopWords.contains(t) &&
            ! config.startWords.contains(t)) {

          queue.insertWithOverflow(new TokenDFTF(t, df, tf));
        }
      }
      bytesRef = termsEnum.next();
    }
    if (config.outputFile == null) {
      StringBuilder sb = new StringBuilder();
      for (TokenDFTF tp : queue.getArray()) {
        System.out.println(getRow(sb, tp));
      }
    } else if (Files.isDirectory(config.outputFile)) {
      writeTopN(config.outputFile.resolve(field), queue);
    } else {
      writeTopN(config.outputFile, queue);
    }
  }

  private String getRow(StringBuilder sb, TokenDFTF tp) {
    sb.setLength(0);
    sb.append(clean(tp.token));
    if (config.includeDocFreq) {
      sb.append("\t").append(tp.df);
    }
    if (config.includeTermFreq) {
      sb.append("\t").append(tp.tf);
    }
    return sb.toString();
  }

  private void writeTopN(Path path, AbstractTokenTFDFPriorityQueue queue) throws IOException {
    if (Files.isRegularFile(path)) {
      System.err.println("File "+path.getFileName() + " already exists. Skipping.");
      return;
    }
    Files.createDirectories(path.getParent());
    BufferedWriter writer =
        Files.newBufferedWriter(path, StandardCharsets.UTF_8);
    for (String t : config.startWords) {
      writer.write(t+"\n");
    }
    StringBuilder sb = new StringBuilder();
    for (TokenDFTF tp : queue.getArray()) {
      writer.write(getRow(sb, tp)+"\n");

    }
    writer.flush();
    writer.close();
  }

  private static String clean(String s) {
    if (s == null) {
      return "";
    }
    return s.replaceAll("\\s+", " ").trim();
  }
}
