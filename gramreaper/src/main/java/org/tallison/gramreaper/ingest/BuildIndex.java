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

package org.tallison.gramreaper.ingest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.tallison.gramreaper.ingest.schema.IndexSchema;

public class BuildIndex {

  static Logger logger = Logger.getLogger(BuildIndex.class);


  static Options OPTIONS;

  static {
    Option schema = new Option("s", "schema",
        true, "json field/analyzer schema");
    schema.setRequired(true);

    Option indexPath = new Option("idx", "index",
        true, "Lucene index to create");
    indexPath.setRequired(true);

    Option inputFileOrDir = new Option("i", "input", true, "file to ingest (one doc per row, UTF-8); or directory of UTF-8 text files");
    inputFileOrDir.setRequired(true);
    OPTIONS = new Options()
        .addOption(schema)
        .addOption(indexPath)
        .addOption(inputFileOrDir)
        .addOption("sql", true, "sql to retrieve 'content' field to index")
        .addOption("jdbc", true, "jdbc connection string");
  }

  public static void USAGE() {
    HelpFormatter helpFormatter = new HelpFormatter();
    helpFormatter.printHelp(
        80,
        "java -jar gramreaper-q.r-s.t.jar BuildIndex|DumpTerms <options>",
        "GramReaper",
        OPTIONS,
        "");
  }


  public static void main(String[] args) throws Exception {
    DefaultParser parser = new DefaultParser();
    CommandLine commandLine = null;
    try {
      commandLine = parser.parse(OPTIONS, args);
    } catch (ParseException e) {
      USAGE();
      return;
    }

    IndexSchema indexSchema = IndexSchema.load(Paths.get(commandLine.getOptionValue('s')));
    Indexer indexer = Indexer.build(
        Paths.get(commandLine.getOptionValue("idx")),
        indexSchema.getIndexAnalyzer(),
        indexSchema.getDefinedFields());

    Runner runner = null;
    if (commandLine.hasOption('i')) {
        Path inputFileOrDir = Paths.get(commandLine.getOptionValue('i'));
        if (Files.isDirectory(inputFileOrDir)) {
            runner = new DirectoryReaderRunner(inputFileOrDir);
        } else {
            runner = new FileReaderRunner(Paths.get(commandLine.getOptionValue('f')));
        }
    } else {
      runner = new SQLRunner(commandLine.getOptionValue("jdbc"), commandLine.getOptionValue("sql"));
    }
    runner.run(indexer);
    indexer.close();
  }

  private interface Runner {
    void run(Indexer indexer);
  }

  private static class DirectoryReaderRunner implements Runner {
    private final Path file;

    DirectoryReaderRunner(Path file) {
      this.file = file;
    }
    private void handleDir(Path dir, Indexer indexer) {
      for (File f : dir.toFile().listFiles()) {
        if (f.isDirectory()) {
          handleDir(f.toPath(), indexer);
        } else {
          try {
            handleFile(f.toPath(), indexer);
          } catch (IOException e) {
            //log
          }
        }
      }
    }

    private void handleFile(Path file, Indexer indexer) throws IOException {
      StringBuilder sb = new StringBuilder();
      for (String line : Files.readAllLines(file)) {
        sb.append(line).append("\n");
      }
      indexer.index(sb.toString());
    }

    @Override
    public void run(Indexer indexer) {
      handleDir(file, indexer);
    }
  }

  private static class FileReaderRunner implements Runner {
    private final Path file;
    FileReaderRunner(Path file) {
      this.file = file;
    }
    @Override
    public void run(Indexer indexer) {

      InputStream is = null;
      try {
        is = Files.newInputStream(file);
        if (file.getFileName().toString().endsWith(".bz2")) {
          is = new BZip2CompressorInputStream(is);
        } else if (file.getFileName().toString().endsWith(".gz")) {
          is = new GzipCompressorInputStream(is);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }


      try(BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        String line = reader.readLine();
        while (line != null) {
          indexer.index(line);
          line = reader.readLine();
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

    }
  }

  private static class Indexer {
    static Indexer build(Path path, Analyzer analyzer, Set<String> fields) throws Exception {
      IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
      Directory directory = FSDirectory.open(path);
      IndexWriter indexWriter = new IndexWriter(directory, indexWriterConfig);
      return new Indexer(indexWriter, fields);
    }

    private final IndexWriter indexWriter;
    private final Set<String> fields;
    private int docsIndexed = 0;
    private final long start = new Date().getTime();
    private Indexer(IndexWriter indexWriter, Set<String> fields) throws Exception {
      this.indexWriter = indexWriter;
      this.fields = fields;
    }

    void index(String content) throws IOException {
      Document document = new Document();
      for (String field : fields) {
        document.add(new TextField(field, content, Field.Store.NO));
      }
      indexWriter.addDocument(document);
      if (++docsIndexed % 1000 == 0) {
        logger.info("Processed "+docsIndexed + " docs in "+
            (new Date().getTime()-start) + " ms");
      }
    }

    void close() throws IOException {
      indexWriter.flush();
      indexWriter.commit();
      indexWriter.close();
      indexWriter.getDirectory().close();

    }
  }

  private static class SQLRunner implements Runner {
    private final Connection connection;
    private final String sql;
    public SQLRunner(String jdbc, String sql) {
      try {
        connection = DriverManager.getConnection(jdbc);
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
      this.sql = sql;
    }

    @Override
    public void run(Indexer indexer) {
      Statement st = null;
      try {
        st = connection.createStatement();
        ResultSet rs = st.executeQuery(sql);
        while (rs.next()) {
          String content = rs.getString(1);
          indexer.index(content);
        }
      } catch (SQLException|IOException e) {
        throw new RuntimeException(e);
      }
      try {
        connection.close();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
