package org.jasig.ssp.service.impl;

import org.jasig.ssp.transferobject.reports.JournalCaseNotesStudentReportTO;

import java.util.Comparator;

public class JournalCaseNotesStudentReportTOComparator implements Comparator<JournalCaseNotesStudentReportTO> {

    public int compare(JournalCaseNotesStudentReportTO p1, JournalCaseNotesStudentReportTO p2) {

        int value = p1.getLastName().compareToIgnoreCase(p2.getLastName());
        //1
        if (value != 0) {
            return value;
        }

        value = p1.getFirstName().compareToIgnoreCase(p2.getFirstName());
        //1
        if (value != 0) {
            return value;
        }
        //1
        if (p1.getMiddleName() == null && p2.getMiddleName() == null) {
            return 0;
        }
        //1
        if (p1.getMiddleName() == null) {
            return -1;
        }
        //1
        if (p2.getMiddleName() == null) {
            return 1;
        }
        return p1.getMiddleName().compareToIgnoreCase(p2.getMiddleName());
    }
}
