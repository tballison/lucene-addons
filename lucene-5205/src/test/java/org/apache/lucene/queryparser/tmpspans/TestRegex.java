package org.apache.lucene.queryparser.tmpspans;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by TALLISON on 11/18/2014.
 */
public class TestRegex {
    public static void main(String[] args) {
            String s = "'S SOLUTION a PROVIDER TESTABCD";
            long start = new Date().getTime();
            Matcher m = Pattern.compile("'(([^']+)+)'").matcher(s);
            while (m.find()) {
                System.out.println(m.start());
            }
            System.out.println("elapsed:" + (new Date().getTime()-start));
    }
}
