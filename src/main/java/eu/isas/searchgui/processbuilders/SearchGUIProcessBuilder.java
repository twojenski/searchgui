package eu.isas.searchgui.processbuilders;

import com.compomics.util.waiting.Duration;
import com.compomics.util.waiting.WaitingHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * A simple ancestor class to reduce code duplication in formatdb, omssacl and
 * tandem process builders.
 *
 * @author Lennart Martens
 * @author Marc Vaudel
 * @author Harald Barsnes
 */
public abstract class SearchGUIProcessBuilder {

    /**
     * The process to be executed as array.
     */
    ArrayList process_name_array = new ArrayList();
    /**
     * The process builder.
     */
    ProcessBuilder pb;
    /**
     * The process.
     */
    Process p;
    /**
     * The waiting handler to display the feedback.
     */
    protected WaitingHandler waitingHandler;

    /**
     * Trivial constructor.
     */
    public SearchGUIProcessBuilder() {
    }

    /**
     * Starts the process of a process builder, gets the input stream from the
     * process and shows it in a JEditorPane supporting HTML. Does not close
     * until the process is completed.
     */
    public void startProcess() {
        
        Duration processDuration = new Duration();
        processDuration.start();
        
        p = null;
        try {
            p = pb.start();
        } catch (IOException ioe) {
            System.out.println(ioe.getMessage());
            ioe.printStackTrace();
        }

        // get inputstream from process
        InputStream inputStream = p.getInputStream();

        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

            if (getType().equalsIgnoreCase("Comet Process")) {

                Scanner scan = new Scanner(inputStream);
                scan.useDelimiter("\n|\b ");
                String lastString = "";

                // get input from scanner, send to std out and text box
                while (scan.hasNext() && !waitingHandler.isRunCanceled()) {
                    String temp = scan.next();
                    if (!lastString.contains(temp)) {
                        waitingHandler.appendReport(temp + " ", false, temp.lastIndexOf("%") == -1 || temp.lastIndexOf("100%") != -1);
                    }
                    lastString = temp;
                }
            } else if (getType().equalsIgnoreCase("msConvert Process")) {

                boolean progressOutputStarted = false;

                String line;

                // get input from stream
                while ((line = bufferedReader.readLine()) != null) {

                    if (progressOutputStarted && line.lastIndexOf("/") != -1) {

                        String[] progress = line.split("/");

                        if (waitingHandler.getMaxSecondaryProgressCounter() != Integer.parseInt(progress[1].trim())) {
                            waitingHandler.setMaxSecondaryProgressCounter(Integer.parseInt(progress[1].trim()));
                        }

                        waitingHandler.setSecondaryProgressCounter(Integer.parseInt(progress[0].trim()));

                    } else {
                        waitingHandler.appendReport(line, false, true);
                    }

                    if (line.startsWith("writing output file:")) {
                        progressOutputStarted = true;
                        waitingHandler.resetSecondaryProgressCounter();
                        waitingHandler.setSecondaryProgressCounterIndeterminate(false);
                    }
                }

                waitingHandler.setSecondaryProgressCounterIndeterminate(true);

            } else {
                String line;

                // get input from stream and check for errors
                while ((line = bufferedReader.readLine()) != null) {

                    line += System.getProperty("line.separator");

                    if (line.lastIndexOf("<CompomicsError>") != -1) {
                        waitingHandler.appendReportEndLine();
                        line = line.substring("<CompomicsError>".length(), line.length() - ("</CompomicsError>".length() + 2));
                        waitingHandler.appendReport(line, true, true);
                        waitingHandler.setRunCanceled();
                    } else {
                        waitingHandler.appendReport(line, false, false);
                    }
                }
            }

            inputStream.close();
            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // check if the user has cancelled the process or not
        if (waitingHandler.isRunCanceled()) {
            if (p != null) {
                p.destroy();
            }
        } else {
            
            processDuration.end();
            waitingHandler.appendReportEndLine();
            waitingHandler.appendReportEndLine();
            waitingHandler.appendReport(getType() + " Finished (" + processDuration.toString() + ").", true, true);
            waitingHandler.appendReportEndLine();

            // wait for process to terminate before exiting
            try {
                p.waitFor();
            } catch (InterruptedException e) {
                if (p != null) {
                    p.destroy();
                }
            }
        }
    }

    /**
     * Ends the process.
     */
    public void endProcess() {
        if (p != null) {
            p.destroy();
        }
    }

    /**
     * Returns the type of the process.
     *
     * @return the type of the process
     */
    public abstract String getType();

    /**
     * Returns the file name of the currently processed file.
     *
     * @return the file name of the currently processed file
     */
    public abstract String getCurrentlyProcessedFileName();
}
