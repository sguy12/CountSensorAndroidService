package com.clean.peoplecounterap.logic;

import com.clean.peoplecounterap.filter.MedianFilter;
import kotlin.Pair;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import timber.log.Timber;

public class SignalDetector {

    // TODO: update these values for best results
    private final static int lag = 100;
    private final static double threshold = 3.5;
    private final static double influence = 0.5;
    private final static int bufferSize = 800; //40HZ OR 20HZ id the device sample rate
    //buffer_400 => 10sec window sample
    //buffer_200 => 5sec window sample

    public int LastDistance = -1;
    public int LastSignalsSize = -1;

    public boolean IsInProc = false;
    public boolean IsDone = false;
    public int CurrentSize = 0;
    public int errLine = 0;

    private List<Double> rawDataValues = new ArrayList<Double>();
    //private List<Long> rawDataTimeStamps = new ArrayList<Long>(2 * bufferSize);
    private List<Long> verifiedEntries = new LinkedList<>();

    private int sensorInstalledHeight = 4000;

    public void setMinMax(@NotNull Pair<Integer, Integer> pair) {
        Timber.d(pair.toString());
    }

    public void setSensorInstalledHeight(final int sensorInstalledHeight) {
        this.sensorInstalledHeight = sensorInstalledHeight;
    }

    public void Reset()
    {
        rawDataValues = new ArrayList<Double>();
        verifiedEntries = new LinkedList<Long>();
        CurrentSize = 0;
        LastDistance = -1;
        IsDone = false;
    }
    /**
     * get distance and checks is it result is an entry
     *
     * @param distance distance received from sensor
     * @return list of entries timestamps
     */
    public List<Long> processSignal(Double distance) {
        try {
            if(IsDone || IsInProc || distance < 0)
                return new LinkedList<Long>();
            //if(IsDone || IsInProc)
                //return new LinkedList<Long>();

            //edit the distance value:

            Double maxSignalDistance = 3000.0;
            Double maxLengthToDetect = 2000.0;

            Double distanceFixed = distance;
            if(distanceFixed == 0) distanceFixed = maxSignalDistance;
            if(distanceFixed > maxLengthToDetect) distanceFixed = maxSignalDistance;
            distanceFixed = maxSignalDistance - distanceFixed;
            LastDistance = distanceFixed.intValue();

            //CurrentSize++;
            rawDataValues.add(distanceFixed);
            CurrentSize = rawDataValues.size();

            if(CurrentSize > 50) {
                errLine = 0;
                LastSignalsSize = -3;
            }

            // collect at least [bufferSize] samples and wait for the sensor to be idle
            //if (rawDataValues.size() < bufferSize || distance < 0) return new LinkedList<>();
            if(CurrentSize < bufferSize) return new LinkedList<Long>();

            errLine = 100;
            IsInProc = true;
            if(verifiedEntries == null)
                verifiedEntries = new LinkedList<Long>();

            //After collecting samples in the array (in the size of [bufferSize])
            //start to analyze the sample-array in order to find PEEKS [people counting]

            errLine = 200;
            //first: convert the vector to it's median value (1D 5 sample median)
            MedianFilter medFilt = new MedianFilter();
            List<Double> meanDataValues = medFilt.getMean(rawDataValues, 2);
            System.out.println("size of  rawDataValues=" + rawDataValues.size());
            System.out.println("size of  meanDataValues=" + meanDataValues.size());

            errLine = 300;
            //errLine = meanDataValues.size();
            //then: use the algorithm to find the peeks:
            DataForSignals dataForSignals = analyzeDataForSignals(meanDataValues);
            List<Integer> signalsList = dataForSignals.signals;


            errLine = 400;
            boolean isInPeekState = false;
            int lastIndexPeekStart = 0;
            Date date= new Date();
            long time = date.getTime(); //Time in Milliseconds
            //C#: DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()

            if (signalsList != null) {
                errLine = 500;
                LastSignalsSize = signalsList.size();
                for (int i = 0; i < signalsList.size(); i++) {
                    if (signalsList.get(i) > 0 && !isInPeekState) {
                        isInPeekState = true;
                        lastIndexPeekStart = i;
                        System.out.println("Point " + i + " gave signal " + signalsList.get(i));
                        verifiedEntries.add(time);
                        //verifiedEntries.add((long) i);
                    } else {
                        if ((i - lastIndexPeekStart) > 7)
                            isInPeekState = false;
                    }
                }
            }else{
                LastSignalsSize = -2;
            }

            errLine = 600;
            rawDataValues.clear();

            errLine = 700;
            IsDone = true;
            IsInProc = false;
            return verifiedEntries;
        }
        catch(Exception e){
            IsDone = true;
            IsInProc = false;
            return new LinkedList<>();
        }
    }

    private DataForSignals analyzeDataForSignals(List<Double> data) {

        // init stats instance
        SummaryStatistics stats = new SummaryStatistics();

        DataForSignals dataForSignals = new DataForSignals(data);

        // the results (peaks, 1 or -1) of our algorithm
        List<Integer> signals = dataForSignals.signals;

        // filter out the signals (peaks) from our original list (using influence arg)
        List<Double> filteredData = dataForSignals.filteredData;

        // the current average of the rolling window
        List<Double> avgFilter = dataForSignals.avgFilter;

        // the current standard deviation of the rolling window
        List<Double> stdFilter = dataForSignals.stdFilter;

        // init avgFilter and stdFilter
        for (int i = 0; i < SignalDetector.lag; i++) {
            stats.addValue(data.get(i));
        }
        avgFilter.set(SignalDetector.lag - 1, stats.getMean());
        stdFilter.set(SignalDetector.lag - 1, Math.sqrt(stats.getPopulationVariance())); // getStandardDeviation() uses sample variance
        stats.clear();

        // loop input starting at end of rolling window
        for (int i = SignalDetector.lag; i < data.size(); i++) {

            // if the distance between the current value and average is enough standard deviations (threshold) away
            if (Math.abs((data.get(i) - avgFilter.get(i - 1))) > (Double) SignalDetector.threshold * stdFilter.get(i - 1)) {

                // this is a signal (i.e. peak), determine if it is a positive or negative signal
                if (data.get(i) > avgFilter.get(i - 1)) {
                    signals.set(i, 1);
                } else {
                    signals.set(i, -1);
                }

                // filter this signal out using influence
                filteredData.set(i, ((Double) SignalDetector.influence * data.get(i)) + ((1 - SignalDetector.influence) * filteredData.get(i - 1)));
            } else {
                // ensure this signal remains a zero
                signals.set(i, 0);
                // ensure this value is not filtered
                filteredData.set(i, data.get(i));
            }

            // update rolling average and deviation
            for (int j = i - SignalDetector.lag; j < i; j++) {
                stats.addValue(filteredData.get(j));
            }
            avgFilter.set(i, stats.getMean());
            stdFilter.set(i, Math.sqrt(stats.getPopulationVariance()));
            stats.clear();
        }

        return dataForSignals;

    } // end


    private static class DataForSignals {

        // the results (peaks, 1 or -1) of our algorithm
        List<Integer> signals;

        // filter out the signals (peaks) from our original list (using influence arg)
        List<Double> filteredData;

        // the current average of the rolling window
        List<Double> avgFilter;

        // the current standard deviation of the rolling window
        List<Double> stdFilter;

        DataForSignals(List<Double> data) {
            // the results (peaks, 1 or -1) of our algorithm
            signals = new ArrayList<Integer>(Collections.nCopies(data.size(), 0));

            // filter out the signals (peaks) from our original list (using influence arg)
            filteredData = new ArrayList<Double>(data);

            // the current average of the rolling window
            avgFilter = new ArrayList<Double>(Collections.nCopies(data.size(), 0.0d));

            // the current standard deviation of the rolling window
            stdFilter = new ArrayList<Double>(Collections.nCopies(data.size(), 0.0d));
        }

    }


}
//Main method
//
//import java.text.DecimalFormat;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.HashMap;
//import java.util.List;
//
//public class Main {
//
//    public static void main(String[] args) throws Exception {
//        DecimalFormat df = new DecimalFormat("#0.000");
//
//        ArrayList<Double> data = new ArrayList<Double>(Arrays.asList(1d, 1d, 1.1d, 1d, 0.9d, 1d, 1d, 1.1d, 1d, 0.9d, 1d,
//                1.1d, 1d, 1d, 0.9d, 1d, 1d, 1.1d, 1d, 1d, 1d, 1d, 1.1d, 0.9d, 1d, 1.1d, 1d, 1d, 0.9d, 1d, 1.1d, 1d, 1d,
//                1.1d, 1d, 0.8d, 0.9d, 1d, 1.2d, 0.9d, 1d, 1d, 1.1d, 1.2d, 1d, 1.5d, 1d, 3d, 2d, 5d, 3d, 2d, 1d, 1d, 1d,
//                0.9d, 1d, 1d, 3d, 2.6d, 4d, 3d, 3.2d, 2d, 1d, 1d, 0.8d, 4d, 4d, 2d, 2.5d, 1d, 1d, 1d));
//
//        SignalDetector signalDetector = new SignalDetector();
//        int lag = 30;
//        double threshold = 5;
//        double influence = 0;
//
//        HashMap<String, List> resultsMap = signalDetector.analyzeDataForSignals(data, lag, threshold, influence);
//        // print algorithm params
//        System.out.println("lag: " + lag + "\t\tthreshold: " + threshold + "\t\tinfluence: " + influence);
//
//        System.out.println("Data size: " + data.size());
//        System.out.println("Signals size: " + resultsMap.get("signals").size());
//
//        // print data
//        System.out.print("Data:\t\t");
//        for (double d : data) {
//            System.out.print(df.format(d) + "\t");
//        }
//        System.out.println();
//
//        // print signals
//        System.out.print("Signals:\t");
//        List<Integer> signalsList = resultsMap.get("signals");
//        for (int i : signalsList) {
//            System.out.print(df.format(i) + "\t");
//        }
//        System.out.println();
//
//        // print filtered data
//        System.out.print("Filtered Data:\t");
//        List<Double> filteredDataList = resultsMap.get("filteredData");
//        for (double d : filteredDataList) {
//            System.out.print(df.format(d) + "\t");
//        }
//        System.out.println();
//
//        // print running average
//        System.out.print("Avg Filter:\t");
//        List<Double> avgFilterList = resultsMap.get("avgFilter");
//        for (double d : avgFilterList) {
//            System.out.print(df.format(d) + "\t");
//        }
//        System.out.println();
//
//        // print running std
//        System.out.print("Std filter:\t");
//        List<Double> stdFilterList = resultsMap.get("stdFilter");
//        for (double d : stdFilterList) {
//            System.out.print(df.format(d) + "\t");
//        }
//        System.out.println();
//
//        System.out.println();
//        for (int i = 0; i < signalsList.size(); i++) {
//            if (signalsList.get(i) != 0) {
//                System.out.println("Point " + i + " gave signal " + signalsList.get(i));
//            }
//        }
//    }
//}
//Results
//
//lag: 30     threshold: 5.0      influence: 0.0
//Data size: 74
//Signals size: 74
//Data:           1.000   1.000   1.100   1.000   0.900   1.000   1.000   1.100   1.000   0.900   1.000   1.100   1.000   1.000   0.900   1.000   1.000   1.100   1.000   1.000   1.000   1.000   1.100   0.900   1.000   1.100   1.000   1.000   0.900   1.000   1.100   1.000   1.000   1.100   1.000   0.800   0.900   1.000   1.200   0.900   1.000   1.000   1.100   1.200   1.000   1.500   1.000   3.000   2.000   5.000   3.000   2.000   1.000   1.000   1.000   0.900   1.000   1.000   3.000   2.600   4.000   3.000   3.200   2.000   1.000   1.000   0.800   4.000   4.000   2.000   2.500   1.000   1.000   1.000
//Signals:        0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   1.000   0.000   1.000   1.000   1.000   1.000   1.000   0.000   0.000   0.000   0.000   0.000   0.000   1.000   1.000   1.000   1.000   1.000   1.000   0.000   0.000   0.000   1.000   1.000   1.000   1.000   0.000   0.000   0.000
//Filtered Data:  1.000   1.000   1.100   1.000   0.900   1.000   1.000   1.100   1.000   0.900   1.000   1.100   1.000   1.000   0.900   1.000   1.000   1.100   1.000   1.000   1.000   1.000   1.100   0.900   1.000   1.100   1.000   1.000   0.900   1.000   1.100   1.000   1.000   1.100   1.000   0.800   0.900   1.000   1.200   0.900   1.000   1.000   1.100   1.200   1.000   1.000   1.000   1.000   1.000   1.000   1.000   1.000   1.000   1.000   1.000   0.900   1.000   1.000   1.000   1.000   1.000   1.000   1.000   1.000   1.000   1.000   0.800   0.800   0.800   0.800   0.800   1.000   1.000   1.000
//Avg Filter:     0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   1.003   1.003   1.007   1.007   1.003   1.007   1.010   1.003   1.000   0.997   1.003   1.003   1.003   1.000   1.003   1.010   1.013   1.013   1.013   1.010   1.010   1.010   1.010   1.010   1.007   1.010   1.010   1.003   1.003   1.003   1.007   1.007   1.003   1.003   1.003   1.000   1.000   1.007   1.003   0.997   0.983   0.980   0.973   0.973   0.970
//Std filter:     0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.000   0.060   0.060   0.063   0.063   0.060   0.063   0.060   0.071   0.073   0.071   0.080   0.080   0.080   0.077   0.080   0.087   0.085   0.085   0.085   0.083   0.083   0.083   0.083   0.083   0.081   0.079   0.079   0.080   0.080   0.080   0.077   0.077   0.075   0.075   0.075   0.073   0.073   0.063   0.071   0.080   0.078   0.083   0.089   0.089   0.086
//
//Point 45 gave signal 1
//Point 47 gave signal 1
//Point 48 gave signal 1
//Point 49 gave signal 1
//Point 50 gave signal 1
//Point 51 gave signal 1
//Point 58 gave signal 1
//Point 59 gave signal 1
//Point 60 gave signal 1
//Point 61 gave signal 1
//Point 62 gave signal 1
//Point 63 gave signal 1
//Point 67 gave signal 1
//Point 68 gave signal 1
//Point 69 gave signal 1
//Point 70 gave signal 1