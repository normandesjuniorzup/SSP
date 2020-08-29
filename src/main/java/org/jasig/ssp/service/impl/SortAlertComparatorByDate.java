package org.jasig.ssp.service.impl;

import org.jasig.ssp.transferobject.EarlyAlertTO;

import java.util.Comparator;
import java.util.Date;

public class SortAlertComparatorByDate implements Comparator<EarlyAlertTO> {

    @Override
    public int compare(EarlyAlertTO p1, EarlyAlertTO p2) {
        Date p1Date = p1.getLastResponseDate();
        //1
        if (p1Date == null) {
            p1Date = p1.getCreatedDate();
        }
        Date p2Date = p2.getLastResponseDate();
        //1
        if (p2Date == null) {
            p2Date = p2.getCreatedDate();
        }
        return p1Date.compareTo(p2Date);
    }
}
