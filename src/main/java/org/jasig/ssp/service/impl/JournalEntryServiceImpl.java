/**
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.ssp.service.impl;

import org.jasig.ssp.dao.JournalEntryDao;
import org.jasig.ssp.dao.PersonDao;
import org.jasig.ssp.model.JournalEntry;
import org.jasig.ssp.model.JournalEntryDetail;
import org.jasig.ssp.model.Person;
import org.jasig.ssp.service.AbstractRestrictedPersonAssocAuditableService;
import org.jasig.ssp.service.JournalEntryService;
import org.jasig.ssp.service.ObjectNotFoundException;
import org.jasig.ssp.service.PersonProgramStatusService;
import org.jasig.ssp.transferobject.reports.*;
import org.jasig.ssp.util.sort.PagingWrapper;
import org.jasig.ssp.util.sort.SortingAndPaging;
import org.jasig.ssp.web.api.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.jasig.ssp.service.impl.ThrowingConsumer.*;

//1
//1
//1
@Service
@Transactional
public class JournalEntryServiceImpl extends AbstractRestrictedPersonAssocAuditableService<JournalEntry> implements JournalEntryService {

    //1
    @Autowired
    private transient JournalEntryDao dao;
    //1
    @Autowired
    private transient PersonProgramStatusService personProgramStatusService;
    //1
    @Autowired
    private transient PersonDao personDao;

    @Override
    protected JournalEntryDao getDao() {
        return dao;
    }

    //1
    //1
    @Override
    public JournalEntry create(final JournalEntry obj) throws ObjectNotFoundException, ValidationException {
        final JournalEntry journalEntry = getDao().save(obj);
        checkForTransition(journalEntry);
        return journalEntry;
    }

    @Override
    public JournalEntry save(final JournalEntry obj) throws ObjectNotFoundException, ValidationException {
        final JournalEntry journalEntry = getDao().save(obj);
        checkForTransition(journalEntry);
        return journalEntry;
    }

    private void checkForTransition(final JournalEntry journalEntry) throws ObjectNotFoundException, ValidationException {
        // search for a JournalStep that indicates a transition

        journalEntry.getJournalEntryDetails()
                .stream()
                //1
                .filter(JournalEntryDetail::isUsedForTransition)
                .findFirst()
                //1
                //1
                .ifPresent(unchecked((detail) -> personProgramStatusService.setTransitionForStudent(journalEntry.getPerson())));
    }

    //1
    @Override
    public Long getCountForCoach(Person coach, Date createDateFrom, Date createDateTo, List<UUID> studentTypeIds) {
        return dao.getJournalCountForCoach(coach, createDateFrom, createDateTo, studentTypeIds);
    }

    @Override
    public Long getStudentCountForCoach(Person coach, Date createDateFrom, Date createDateTo, List<UUID> studentTypeIds) {
        return dao.getStudentJournalCountForCoach(coach, createDateFrom, createDateTo, studentTypeIds);
    }

    //1
    //1
    //1
    @Override
    public PagingWrapper<EntityStudentCountByCoachTO> getStudentJournalCountForCoaches(EntityCountByCoachSearchForm form) {
        return dao.getStudentJournalCountForCoaches(form);
    }

    //1
    //1
    //1
    @Override
    public PagingWrapper<JournalStepStudentReportTO> getJournalStepStudentReportTOsFromCriteria(JournalStepSearchFormTO personSearchForm, SortingAndPaging sAndP) {
        return dao.getJournalStepStudentReportTOsFromCriteria(personSearchForm, sAndP);
    }

    //1
    //1
    @Override
    public List<JournalCaseNotesStudentReportTO> getJournalCaseNoteStudentReportTOsFromCriteria(JournalStepSearchFormTO personSearchForm, SortingAndPaging sAndP) throws ObjectNotFoundException {
        return new JournalCaseNotesStudentReportTOFromCriteria(dao, personDao).getJournalCaseNoteStudentReportTOsFromCriteria(personSearchForm, sAndP);
    }

}
