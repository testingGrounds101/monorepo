package edu.nyu.classes.nyuhome.feeds;

import java.util.Date;


class MaxAgeAndCountFilter {
    private Date expireDate;
    private int acceptedResultsCount;
    private int maxResults;

    public MaxAgeAndCountFilter(int maxAgeDays, int maxResults) {
        this.acceptedResultsCount = 0;
        this.maxResults = maxResults;

        expireDate = new Date(new Date().getTime() - ((long)maxAgeDays * 24 * 60 * 60 * 1000));
    }

    public boolean accept(Date entryDate) {
        if (acceptedResultsCount >= maxResults) {
            return false;
        }

        if (entryDate.after(expireDate)) {
            acceptedResultsCount++;
            return true;
        }

        return false;
    }
}

