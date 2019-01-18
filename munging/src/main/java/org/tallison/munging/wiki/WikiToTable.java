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


    private WikiClean cleaner = new WikiClean.Builder()
            .withLanguage(WikiClean.WikiLanguage.EN).withTitle(false)
            .withFooter(false).build();

    private Matcher redirectMatcher = Pattern.compile("<text[^>]*>#").matcher("");


    public static void main(String[] args) throws Exception {
        Path bzip = Paths.get(args[0]);
        Path tableFile = Paths.get(args[1]);
        String targLang = args[2];
        int maxPages = -1;
        if (args.length > 3) {
            maxPages = Integer.parseInt(args[3]);
        }

        WikiToTable wikiToTable = new WikiToTable();
        wikiToTable.execute(bzip, tableFile, targLang, maxPages);
    }

    private void execute(Path bzipDir, Path tableFile,
                         String targLang, int maxPages) throws Exception {

        int pages = 0;

        int totalReports = countReports(bzipDir, targLang);
        double samplingRate = (double) (maxPages + 1000) / (double) totalReports;
        samplingRate = (samplingRate > 1.0) ? -1.0 : samplingRate;
        System.err.println("finished counting reports: " + totalReports + " with a sampling rate: " + samplingRate);

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

                List<LanguageProfile> languageProfiles = new LanguageProfileReader().readAllBuiltIn();

                //build language detector:
                LanguageDetector languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                        .withProfiles(languageProfiles)
                        .build();

                //create a text object factory
                TextObjectFactory textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();
                pages += processDump(pages, maxPages, samplingRate, targLang,
                        dump, writer);
            }
        }
    }

    private int processDump(int pagesSoFar, int maxPages,
                            double samplingRate, String targetLang,
                            WikipediaArticlesDump dump, BufferedWriter writer) throws Exception {
        if (pagesSoFar > maxPages) {
            return 0;
        }
        Random random = new Random();
        int localPages = 0;
        int totalPages = pagesSoFar;
        TextObjectFactory textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();
        List<LanguageProfile> languageProfiles = new LanguageProfileReader().readAllBuiltIn();

        //build language detector:
        LanguageDetector languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(languageProfiles)
                .build();
        for (String page : dump) {
            if (maxPages > -1 && totalPages > maxPages) {
                break;
            }

            if (page.contains("<ns>") && !page.contains("<ns>0</ns>")) {
                continue;
            }

            if (redirectMatcher.reset(page).find()) {
                continue;
            }

            if (samplingRate > -1.0 &&
                    random.nextDouble() > samplingRate) {
                continue;
            }
            String s = cleaner.clean(page).replaceAll("[\\r\\n]+", " ");

            TextObject textObject = textObjectFactory.forText(s);

            Optional<LdLocale> detectedLang = languageDetector.detect(textObject);
            String detectedLangString = "";
            if (detectedLang.isPresent()) {
                detectedLangString = detectedLang.get().toString();
            } else {
                continue;
            }
            detectedLangString = detectedLangString.toLowerCase(Locale.US);
            if (!detectedLangString.equals(targetLang) &&
                    !detectedLangString.startsWith(targetLang + "-")) {
                System.out.println("skipping: " + detectedLangString + " : " + s);
                continue;
            }
            writer.write(s);
            writer.write("\n");
            localPages++;
            totalPages++;

        }
        return localPages;
    }

    private int countReports(Path bzipDir, String fieldName) throws IOException {
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
                total++;

                if (total % 1000 == 0) {
                    double elapsed = (System.currentTimeMillis()- start) / 1000;
                    System.err.println("still counting: " + total + " : with " + skipped +
                            " skipped in " + elapsed + " seconds");
                }
                if (page.contains("<ns>") && !page.contains("<ns>0</ns>")) {
                    skipped++;
                    continue;
                }

                String s = page;
                if (redirectMatcher.reset(s).find()) {
                    skipped++;
                    continue;
                }
            }
        }
        return total;
    }

}