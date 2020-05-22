package com.terabee.sdkdemo.filter;

import org.apache.commons.math3.stat.StatUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class MedianFilter {
    /**
     * Get the mean of the data set.
     *
     * @param data the data set.
     * @return the mean of the data set.
     */
    public List<Double> getMean(List<Double> data, int n) {
        if(data.size() <= 5)
            return data;
        List<Double> meanArray = new ArrayList<Double>();
        double[] values = new double[5];

        for (int i = 0; i < data.size() - 5; i++) {
            for(int k = 0; k < 5; k++){
                values[k] = data.get(i + k).doubleValue();
            }
            double meanValue = StatUtils.percentile(values, 50);
            meanArray.add(meanValue);
        }

        return  meanArray;
    }

}
