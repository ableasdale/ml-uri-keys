package com.marklogic.util;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: alexb
 * Date: 02/05/15
 * Time: 13:22
 * To change this template use File | Settings | File Templates.
 */
public class UriKeyFileProcessor {

    Logger LOG = LoggerFactory.getLogger(UriKeyFileProcessor.class);

    Map<Long, Integer> earlyBackupUriKeyMap = new HashMap<Long, Integer>();
    Map<Long, Integer> laterBackupUriKeyMap = new HashMap<Long, Integer>();
    int uriKeysToInvestigate;

    public Map getUriKeyMap(boolean isSource) {
        return isSource ? earlyBackupUriKeyMap : laterBackupUriKeyMap;
    }

    /**
     * Given a path, traverse the directory and identify all 'URIKeys' files for processing.
     *
     * @param path            - The top level directory to traverse in search of URIKeys files
     * @param isEarlierBackup - Boolean flag to specify whether the backup is the earlier of the two being compared
     */
    public void examineFilesAtPath(String path, boolean isEarlierBackup) {
        String msg = isEarlierBackup ? "earlier" : "later";
        LOG.info(MessageFormat.format("Examining {0} backup path", msg));

        File file = new File(path);
        Collection<File> files = FileUtils.listFiles(file, null, true);

        for (File f : files) {
            if (f.getName().contains(Consts.DATABASE_FOREST_COMMON_NAME) && f.getName().startsWith("URIKeys")) {

                try {
                    octalDumpFile(f, isEarlierBackup);
                } catch (IOException e) {
                    LOG.error("Problem processing file: " + f.getAbsolutePath());
                }

            }
        }

        LOG.info(MessageFormat.format("Group completed: {0} Unique URI Keys processed in {1} backup set", getUriKeyMap(isEarlierBackup).size(), msg));

    }


    /**
     * For a given file, this makes a runtime call to od -t d8 {filename} to get the Octal Dump of the file
     *
     * @param file            - the file to process
     * @param isEarlierBackup - Boolean flag to specify whether the backup is the earlier of the two being compared
     * @throws IOException
     */
    public void octalDumpFile(File file, boolean isEarlierBackup) throws IOException {
        LOG.debug(MessageFormat.format("Processing URIKeys file: {0}", file.getAbsolutePath()));
        String s;
        Process p = Runtime.getRuntime().exec(String.format("od -t d8 %s", file.getAbsolutePath()));
        LOG.trace(file.getAbsolutePath());

        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(p.getInputStream()));

        BufferedReader stdError = new BufferedReader(new
                InputStreamReader(p.getErrorStream()));

        while ((s = stdInput.readLine()) != null) {
            processLine(s, isEarlierBackup);
        }

        while ((s = stdError.readLine()) != null) {
            LOG.error(s);
        }
    }

    /**
     * For each line processed, the URI Keys will be extracted and placed in a Map
     *
     * @param line            - a line of output from od (Octal Dump)
     * @param isEarlierBackup - Boolean flag to specify whether the backup is the earlier of the two being compared
     */
    public void processLine(String line, boolean isEarlierBackup) {

        if (line.contains("*")) {
            LOG.trace("Found an * character - ignoring - what does it mean? " + line);
        } else {
            LOG.trace("Processing: " + line);
            String[] items = line.split(" ");
            LOG.trace("Offset for line: " + items[0]);  // this is an offset, I believe

            for (int i = 1; i < items.length; i++) {

                if (items[i].contains(" ") || "".equals(items[i])) {
                    LOG.trace("Ignoring" + items[i]);
                } else {

                    LOG.trace("Uri Key: " + items[i]);
                    long key = Long.parseLong(items[i]);

                    if (getUriKeyMap(isEarlierBackup).containsKey(key)) {
                        Integer j = (Integer) getUriKeyMap(isEarlierBackup).get(key);
                        getUriKeyMap(isEarlierBackup).put(key, (j + 1));
                    } else {
                        getUriKeyMap(isEarlierBackup).put(key, 1);
                    }

                }
            }

        }
    }

    /**
     * Run the report! ...
     */
    public void report() {

        LOG.info("Generating comparison report ...");

        /* EARLIER backup set report code */
        Iterator earlierBackupEntries = earlyBackupUriKeyMap.entrySet().iterator();
        boolean isAProblem = false;

        /*
        int singleUriKeysInEarlierBackup = 0;
        int pairedUriKeysInEarlierBackup = 0;
        int tripletUriKeysInEarlierBackup = 0; */

        // create some XML
        StringBuilder sb = new StringBuilder();
        sb.append("<items>\n");

        while (earlierBackupEntries.hasNext()) {
            isAProblem = false;
            Map.Entry entry = (Map.Entry) earlierBackupEntries.next();
            Long key = (Long) entry.getKey();
            Integer value = (Integer) entry.getValue();

            /* Does the same URI Key record appear in the later set? */

            if (laterBackupUriKeyMap.containsKey(key)) {
                if (laterBackupUriKeyMap.get(key) != value) {
                    uriKeysToInvestigate += 1;
                    isAProblem = true;
                }
            } else {
                uriKeysToInvestigate += 1;
                isAProblem = true;
            }

            if (isAProblem) {
                sb.append("\t<item uri=\"").append(key).append("\"");
                sb.append(" earlier=\"").append(value).append("\"");
                if (laterBackupUriKeyMap.containsKey(key)) {
                    sb.append(" later=\"").append(laterBackupUriKeyMap.get(key)).append("\"");
                }
                sb.append(" />\n");
            }

            LOG.trace(String.format("URI Key : \t%d\t value : \t%d", key, value));

            /* NOT Sure whether we care about this information?
            if (value == 1) {
                singleUriKeysInEarlierBackup += value;
            } else if (value == 2) {
                pairedUriKeysInEarlierBackup += value;
            } else if (value == 3) {
                tripletUriKeysInEarlierBackup += value;
            } else {
                LOG.debug(String.format("Anomaly found - %d occurrence(s) of URI Key \t:\t %d", value, key));
            } */

        }

        /* LOG.info("Single URI Keys in earlier set: " + singleUriKeysInEarlierBackup);
        LOG.info("Paired URI Keys in earlier set: " + pairedUriKeysInEarlierBackup);
        LOG.info("URI Keys appearing three times in earlier set: " + tripletUriKeysInEarlierBackup);   */
        sb.append("</items>");
        LOG.trace(sb.toString());


        /* LATER backup set report code
        Iterator laterBackupEntries = laterBackupUriKeyMap.entrySet().iterator();

        int singleUriKeysInLaterBackup = 0;
        int pairedUriKeysInLaterBackup = 0;
        int tripletUriKeysInLaterBackup = 0;
        while (laterBackupEntries.hasNext()) {
            Map.Entry entry = (Map.Entry) laterBackupEntries.next();
            Long key = (Long) entry.getKey();
            Integer value = (Integer) entry.getValue();
            LOG.trace(String.format("URI Key : \t%d\t value : \t%d", key, value));

            if (value == 1) {
                singleUriKeysInLaterBackup += value;
            } else if (value == 2) {
                pairedUriKeysInLaterBackup += value;
            } else if (value == 3) {
                tripletUriKeysInLaterBackup += value;
            } else {
                LOG.debug(String.format("Anomaly found - %d occurrence(s) of URI Key \t:\t %d", value, key));
            }

        }

        LOG.info("Single URI Keys in later set: " + singleUriKeysInLaterBackup);
        LOG.info("Paired URI Keys in later set: " + pairedUriKeysInLaterBackup);
        LOG.info("URI Keys appearing three times in later set: " + tripletUriKeysInLaterBackup);
                                        */


        /* What differences are there - write the report XML to disk */

        FileWriter fw = null;
        try {
            fw = new FileWriter(Consts.PATH_FOR_XML_REPORT);
            fw.append(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } finally {

            try {
                fw.flush();
                fw.close();


            } catch (IOException e) {
                LOG.error("Error while writing XML report to disk: " + e.getMessage());

            }

        }


        LOG.info(MessageFormat.format("Total keys to investigate: {0}", uriKeysToInvestigate));


    }
}
