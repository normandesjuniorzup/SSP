package org.jasig.ssp.service.impl;

import org.jasig.ssp.dao.EarlyAlertDao;
import org.jasig.ssp.model.EarlyAlert;
import org.jasig.ssp.model.reference.EarlyAlertReason;
import org.jasig.ssp.model.reference.EarlyAlertSuggestion;
import org.jasig.ssp.service.ObjectNotFoundException;
import org.jasig.ssp.service.PersonService;
import org.jasig.ssp.service.reference.EarlyAlertReasonService;
import org.jasig.ssp.service.reference.EarlyAlertSuggestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class EarlyAlertRule {

    //1
    @Autowired
    private transient EarlyAlertDao dao;

    //1
    @Autowired
    private transient PersonService personService;

    //1
    @Autowired
    private transient EarlyAlertReasonService earlyAlertReasonService;

    //1
    @Autowired
    private transient EarlyAlertSuggestionService earlyAlertSuggestionService;

    public EarlyAlert prepareToSave(final EarlyAlert earlyAlert) throws ObjectNotFoundException {
        final EarlyAlert current = dao.get(earlyAlert.getId());

        current.setCourseName(earlyAlert.getCourseName());
        current.setCourseTitle(earlyAlert.getCourseTitle());
        current.setEmailCC(earlyAlert.getEmailCC());
        current.setCampus(earlyAlert.getCampus());
        current.setEarlyAlertReasonOtherDescription(earlyAlert.getEarlyAlertReasonOtherDescription());
        current.setComment(earlyAlert.getComment());
        current.setClosedDate(earlyAlert.getClosedDate());

        //1
        if (earlyAlert.getClosedById() == null) {
            current.setClosedBy(null);
        }
        //1
        else {
            current.setClosedBy(personService.get(earlyAlert.getClosedById()));
        }

        //1
        if (earlyAlert.getPerson() == null) {
            current.setPerson(null);
        }
        //1
        else {
            current.setPerson(personService.get(earlyAlert.getPerson().getId()));
        }

        //1
        final Set<EarlyAlertReason> earlyAlertReasons = new HashSet<>();
        //1
        if (earlyAlert.getEarlyAlertReasons() != null) {
            //1
            for (final EarlyAlertReason reason : earlyAlert.getEarlyAlertReasons()) {
                earlyAlertReasons.add(earlyAlertReasonService.load(reason.getId()));
            }
        }

        current.setEarlyAlertReasons(earlyAlertReasons);

        //1
        final Set<EarlyAlertSuggestion> earlyAlertSuggestions = new HashSet<>();

        //1
        if (earlyAlert.getEarlyAlertSuggestions() != null) {
            //1
            for (final EarlyAlertSuggestion reason : earlyAlert.getEarlyAlertSuggestions()) {
                earlyAlertSuggestions.add(earlyAlertSuggestionService.load(reason.getId()));
            }
        }

        current.setEarlyAlertSuggestions(earlyAlertSuggestions);

        return current;
    }

}
