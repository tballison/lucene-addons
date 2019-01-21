package org.tallison.munging.wiki;

import com.google.common.base.Optional;
import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObject;
import com.optimaize.langdetect.text.TextObjectFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.wikiclean.WikiClean;
import org.wikiclean.WikipediaArticlesDump;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikiToTable {

    static Options OPTIONS;

    static {
        Option inputDir = new Option("i", "input",
                true, "directory with *-pages-articles-*.xml.bz2 files");
        inputDir.setRequired(true);

        Option outputFile = new Option("o", "output",
                true, "output file");
        outputFile.setRequired(true);

        Option targetLang = new Option("l", "lang",
                true, "target language");
        outputFile.setRequired(true);

        OPTIONS = new Options()
                .addOption(inputDir)
                .addOption(outputFile)
                .addOption(targetLang)
                .addOption("maxPages", true, "maximum number of pages to process")
                .addOption("minLength", true, "minimum length of article");
    }

    public static void USAGE() {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp(
                80,
                "java -jar munging-q.r-s.t.jar <options>",
                "GramReaper",
                OPTIONS,
                "");
    }


    private WikiClean cleaner = new WikiClean.Builder()
            .withLanguage(WikiClean.WikiLanguage.EN).withTitle(false)
            .withFooter(false).build();

    private Matcher redirectMatcher = Pattern.compile("<text[^>]*>#").matcher("");


    public static void main(String[] args) throws Exception {
        DefaultParser parser = new DefaultParser();
        CommandLine commandLine = null;
        try {
            commandLine = parser.parse(OPTIONS, args);
        } catch (ParseException e) {
            USAGE();
            return;
        }
        Path bzip = Paths.get(commandLine.getOptionValue('i'));
        Path tableFile = Paths.get(commandLine.getOptionValue('o'));
        String targLang = commandLine.getOptionValue('l');

        int maxPages = -1;
        if (commandLine.hasOption("maxPages")) {
            maxPages = Integer.parseInt(commandLine.getOptionValue("maxPages"));
        }

        int minPageLength = -1;
        if (commandLine.hasOption("minLength")) {
            minPageLength = Integer.parseInt(commandLine.getOptionValue("minLength"));
        }

        WikiToTable wikiToTable = new WikiToTable();
        wikiToTable.execute(bzip, tableFile, targLang, maxPages, minPageLength);
    }

    private final TextObjectFactory textObjectFactory;
    private final List<LanguageProfile> languageProfiles;
    private final LanguageDetector languageDetector;

    public WikiToTable() throws Exception {
        textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();
        languageProfiles = new LanguageProfileReader().readAllBuiltIn();

        //build language detector:
        languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(languageProfiles)
                .build();
    }
    private void execute(Path bzipDir, Path tableFile,
                         String targLang, int maxPages, int minPageLength) throws Exception {

        int pages = 0;
        double samplingRate = -1.0;
        if (maxPages > -1) {
            int totalReports = countReports(bzipDir, targLang, minPageLength);
            samplingRate = (double) (maxPages + 1000) / (double) totalReports;
            samplingRate = (samplingRate > 1.0) ? -1.0 : samplingRate;
            System.err.println("finished counting reports: " + totalReports + " with a sampling rate: " + samplingRate);
        }

        try (BufferedWriter writer =
                     new BufferedWriter(
                             new OutputStreamWriter(
                                     new GzipCompressorOutputStream(
                                             Files.newOutputStream(tableFile)
                                     ) , StandardCharsets.UTF_8))) {
            for (File bzip : bzipDir.toFile().listFiles()) {
                if (!bzip.getName().startsWith(targLang)) {
                    continue;
                }
                if (maxPages > -1 && pages > maxPages) {
                    break;
                }
                WikipediaArticlesDump dump = new WikipediaArticlesDump(bzip);

                pages += processDump(pages, maxPages, minPageLength, samplingRate, targLang,
                        dump, writer);
            }
        }
    }

    private int processDump(int pagesSoFar, int maxPages, int minPageLength,
                            double samplingRate, String targetLang,
                            WikipediaArticlesDump dump, BufferedWriter writer) throws Exception {
        if (maxPages > -1 && pagesSoFar > maxPages) {
            return 0;
        }

        Random random = new Random();
        int localPages = 0;
        int totalPages = pagesSoFar;
        int skipped = 0;
        long started = System.currentTimeMillis();
        for (String page : dump) {
            if (minPageLength > -1 && page.trim().length() < minPageLength) {
                skipped++;
                continue;
            }
            if (maxPages > -1 && totalPages > maxPages) {
                break;
            }

            if (page.contains("<ns>") && !page.contains("<ns>0</ns>")) {
                skipped++;
                continue;
            }

            if (redirectMatcher.reset(page).find()) {
                skipped++;
                continue;
            }

            if (samplingRate > -1.0 &&
                    random.nextDouble() > samplingRate) {
                skipped++;
                continue;
            }
            String s = cleaner.clean(page).replaceAll("[\\r\\n]+", " ");

            TextObject textObject = textObjectFactory.forText(s);

            Optional<LdLocale> detectedLang = languageDetector.detect(textObject);
            String detectedLangString = "";
            if (detectedLang.isPresent()) {
                detectedLangString = detectedLang.get().toString();
            } else {
                skipped++;
                continue;
            }
            detectedLangString = detectedLangString.toLowerCase(Locale.US);
            if (!detectedLangString.equals(targetLang) &&
                    !detectedLangString.startsWith(targetLang + "-")) {
                int len = Math.min(100, s.length());
                if (len < s.length()) {
                    s = s.substring(0, len);
                }
                skipped++;
                System.out.println("skipping: " + detectedLangString + " : " + s);
                continue;
            }

            writer.write(s);
            writer.write("\n");
            localPages++;
            totalPages++;
            if (totalPages %1000 == 0) {
                long elapsed = System.currentTimeMillis()-started;
                System.out.println("wrote "+totalPages+
                        " pages total and skipped "+skipped +
                        " (for this file) in "+elapsed + "ms (for this bzip file)");
            }

        }
        return localPages;
    }

    private int countReports(Path bzipDir, String fieldName, int minPageLength) throws IOException {
        int total = 0;
        long start = System.currentTimeMillis();
        int maxPFileName = -1;
        for (File bzip : bzipDir.toFile().listFiles()) {
            if (!bzip.getName().startsWith(fieldName)) {
                continue;
            }
            Matcher m = Pattern.compile("p(\\d+)\\.bz2").matcher(bzip.getName());
            System.out.println(bzip.getName());
            if (m.find()) {
                Integer lastP = null;
                try {
                    lastP = Integer.parseInt(m.group(1));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
                if (lastP != null && lastP > maxPFileName) {
                    maxPFileName = lastP;
                }
            }
        }
        if (maxPFileName > -1) {
            System.out.println("max pfilname: " + maxPFileName);
            return (int) (maxPFileName * 0.70);
            //return maxPFileName;
        }
        int skipped = 0;
        for (File bzip : bzipDir.toFile().listFiles()) {
            if (!bzip.getName().startsWith(fieldName)) {
                continue;
            }

            WikipediaArticlesDump dump = new WikipediaArticlesDump(bzip);
            for (String page : dump) {
                if (page.contains("<ns>") && !page.contains("<ns>0</ns>")) {
                    skipped++;
                    continue;
                }

                String s = page;
                if (redirectMatcher.reset(s).find()) {
                    skipped++;
                    continue;
                }
                if (minPageLength > -1 && page.trim().length() < minPageLength) {
                    continue;
                }
                total++;

                if (total % 1000 == 0) {
                    double elapsed = (System.currentTimeMillis()- start) / 1000;
                    System.err.println("still counting: " + total + " : with " + skipped +
                            " skipped in " + elapsed + " seconds");
                }
            }
        }
        return total;
    }

}