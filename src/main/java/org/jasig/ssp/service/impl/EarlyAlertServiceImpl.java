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
package org.jasig.ssp.service.impl; // NOPMD by jon.adams

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.jasig.ssp.config.EarlyAlertResponseReminderRecipientsConfig;
import org.jasig.ssp.dao.EarlyAlertDao;
import org.jasig.ssp.factory.EarlyAlertSearchResultTOFactory;
import org.jasig.ssp.model.*;
import org.jasig.ssp.model.reference.Campus;
import org.jasig.ssp.model.reference.ProgramStatus;
import org.jasig.ssp.model.reference.StudentType;
import org.jasig.ssp.security.SspUser;
import org.jasig.ssp.service.*;
import org.jasig.ssp.service.reference.ConfigService;
import org.jasig.ssp.service.reference.MessageTemplateService;
import org.jasig.ssp.service.reference.ProgramStatusService;
import org.jasig.ssp.service.reference.StudentTypeService;
import org.jasig.ssp.transferobject.EarlyAlertSearchResultTO;
import org.jasig.ssp.transferobject.PagedResponse;
import org.jasig.ssp.transferobject.form.EarlyAlertSearchForm;
import org.jasig.ssp.transferobject.messagetemplate.EarlyAlertMessageTemplateTO;
import org.jasig.ssp.transferobject.reports.*;
import org.jasig.ssp.util.DateTimeUtils;
import org.jasig.ssp.util.collections.Pair;
import org.jasig.ssp.util.collections.Triple;
import org.jasig.ssp.util.sort.PagingWrapper;
import org.jasig.ssp.util.sort.SortingAndPaging;
import org.jasig.ssp.web.api.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.mail.SendFailedException;
import javax.validation.constraints.NotNull;
import java.util.*;

import static org.jasig.ssp.service.impl.ObjectsUtil.checkNotNull;

/**
 * EarlyAlert service implementation
 *
 * @author jon.adams
 */
//1
//1
//1
@Service
@Transactional
public class EarlyAlertServiceImpl extends // NOPMD
        AbstractPersonAssocAuditableService<EarlyAlert>
        implements EarlyAlertService {

    //1
    @Autowired
    private transient EarlyAlertDao dao;
    //1
    @Autowired
    private transient ConfigService configService;
    //1
    @Autowired
    private transient EarlyAlertRoutingService earlyAlertRoutingService;
    //1
    @Autowired
    private transient MessageService messageService;
    //1
    @Autowired
    private transient MessageTemplateService messageTemplateService;
    //1
    @Autowired
    private transient PersonService personService;
    //1
    @Autowired
    private transient PersonProgramStatusService personProgramStatusService;
    //1
    @Autowired
    private transient ProgramStatusService programStatusService;
    //1
    @Autowired
    private transient StudentTypeService studentTypeService;
    //1
    @Autowired
    private transient SecurityService securityService;
    //1
    @Autowired
    private transient EarlyAlertSearchResultTOFactory searchResultFactory;
    //1
    @Autowired
    private transient EarlyAlertResponseReminderRecipientsConfig earReminderRecipientConfig;
    //1
    @Autowired
    private transient TemplateParameterFillerService templateParameterFillerService;

    @Autowired
    private transient EarlyAlertRule earlyAlertRule;

    private static final Logger LOGGER = LoggerFactory.getLogger(EarlyAlertServiceImpl.class);

    @Override
    protected EarlyAlertDao getDao() {
        return dao;
    }

    //1
    //1
    @Override
    @Transactional(rollbackFor = {ObjectNotFoundException.class, ValidationException.class})
    public EarlyAlert create(@NotNull final EarlyAlert earlyAlert)
            throws ObjectNotFoundException, ValidationException {

        checkNotNull(earlyAlert, new IllegalArgumentException("EarlyAlert must be provided."));
        checkNotNull(earlyAlert.getPerson(), new ValidationException("EarlyAlert Student data must be provided."));

        //1
        final Person student = earlyAlert.getPerson();

        // Figure student advisor or early alert coordinator
        final UUID assignedAdvisor = getEarlyAlertAdvisor(earlyAlert);
        checkNotNull(assignedAdvisor, new ValidationException("Could not determine the Early Alert Advisor for student ID " + student.getId()));

        //1
        if (student.getCoach() == null || assignedAdvisor.equals(student.getCoach().getId())) {
            student.setCoach(personService.get(assignedAdvisor));
        }

        ensureValidAlertedOnPersonStateNoFail(student);

        // Create alert
        final EarlyAlert saved = getDao().save(earlyAlert);

        // Send e-mail to assigned advisor (coach)
        sendMessageToAdvisor(saved, earlyAlert.getEmailCC());

        // Send e-mail CONFIRMATION to faculty
        sendConfirmationMessageToFaculty(saved);

        return saved;
    }

    @Override
    public void closeEarlyAlert(UUID earlyAlertId) throws ObjectNotFoundException, ValidationException {
        final EarlyAlert earlyAlert = getDao().get(earlyAlertId);

        // DAOs don't implement ObjectNotFoundException consistently and we'd
        // rather they not implement it at all, so a small attempt at 'future
        // proofing' here
        checkNotNull(earlyAlert, new ObjectNotFoundException(earlyAlertId, EarlyAlert.class.getName()));

        //1
        if (earlyAlert.getClosedDate() != null) {
            // already closed
            return;
        }

        //1
        final SspUser sspUser = securityService.currentUser();

        checkNotNull(sspUser, new ValidationException("Early Alert cannot be closed by a null User."));

        earlyAlert.setClosedDate(new Date());
        earlyAlert.setClosedBy(sspUser.getPerson());

        // This save will result in a Hib session flush, which works fine with
        // our current usage. Future use cases might prefer to delay the
        // flush and we can address that when the time comes. Might not even
        // need to change anything here if it turns out nothing actually
        // *depends* on the flush.
        getDao().save(earlyAlert);
    }

    @Override
    public void openEarlyAlert(UUID earlyAlertId) throws ObjectNotFoundException, ValidationException {
        final EarlyAlert earlyAlert = getDao().get(earlyAlertId);

        // DAOs don't implement ObjectNotFoundException consistently and we'd
        // rather they not implement it at all, so a small attempt at 'future
        // proofing' here
        checkNotNull(earlyAlert, new ObjectNotFoundException(earlyAlertId, EarlyAlert.class.getName()));

        //1
        if (earlyAlert.getClosedDate() == null) {
            return;
        }

        final SspUser sspUser = securityService.currentUser();
        checkNotNull(sspUser, new ValidationException("Early Alert cannot be closed by a null User."));

        earlyAlert.setClosedDate(null);
        earlyAlert.setClosedBy(null);

        // This save will result in a Hib session flush, which works fine with
        // our current usage. Future use cases might prefer to delay the
        // flush and we can address that when the time comes. Might not even
        // need to change anything here if it turns out nothing actually
        // *depends* on the flush.
        getDao().save(earlyAlert);
    }

    @Override
    public EarlyAlert save(@NotNull final EarlyAlert earlyAlert) throws ObjectNotFoundException {
        final EarlyAlert current = earlyAlertRule.prepare(earlyAlert);
        return getDao().save(current);
    }

    //1
    //1
    @Override
    public PagingWrapper<EarlyAlert> getAllForPerson(final Person person, final SortingAndPaging sAndP) {
        return getDao().getAllForPersonId(person.getId(), sAndP);
    }


    @Override
    public void sendMessageToStudent(@NotNull final EarlyAlert earlyAlert) throws ObjectNotFoundException, SendFailedException, ValidationException {
        checkNotNull(earlyAlert, new IllegalArgumentException("EarlyAlert was missing."));
        checkNotNull(earlyAlert.getPerson(), new IllegalArgumentException("EarlyAlert.Person is missing."));

        final Person person = earlyAlert.getPerson();
        final SubjectAndBody subjAndBody = messageTemplateService
                .createEarlyAlertToStudentMessage(fillTemplateParameters(earlyAlert));

        Set<String> watcheremails = new HashSet<>(person.getWatcherEmailAddresses());
        // Create and queue the message
        final Message message = messageService.createMessage(person, org.springframework.util.StringUtils.arrayToCommaDelimitedString(watcheremails
                        .toArray(new String[watcheremails.size()])),
                subjAndBody);

        LOGGER.info("Message {} created for EarlyAlert {}", message, earlyAlert);
    }


    @Override
    public Map<String, Object> fillTemplateParameters(@NotNull final EarlyAlert earlyAlert) {
        return templateParameterFillerService.fillTemplateParameters(earlyAlert);
    }

    @Override
    public void applyEarlyAlertCounts(Person person) {

    }

    @Override
    public Map<UUID, Number> getCountOfActiveAlertsForPeopleIds(final Collection<UUID> peopleIds) {
        return dao.getCountOfActiveAlertsForPeopleIds(peopleIds);
    }

    @Override
    public Map<UUID, Number> getCountOfClosedAlertsForPeopleIds(final Collection<UUID> peopleIds) {
        return dao.getCountOfClosedAlertsForPeopleIds(peopleIds);
    }

    @Override
    public Long getCountOfEarlyAlertsForSchoolIds(final Collection<String> schoolIds, Campus campus) {
        return dao.getCountOfAlertsForSchoolIds(schoolIds, campus);
    }

    @Override
    public Long getEarlyAlertCountForCoach(Person coach, Date createDateFrom, Date createDateTo, List<UUID> studentTypeIds) {
        return dao.getEarlyAlertCountForCoach(coach, createDateFrom, createDateTo, studentTypeIds);
    }

    @Override
    public Long getStudentEarlyAlertCountForCoach(Person coach, Date createDateFrom, Date createDateTo, List<UUID> studentTypeIds) {
        return dao.getStudentEarlyAlertCountForCoach(coach, createDateFrom, createDateTo, studentTypeIds);
    }

    @Override
    public Long getEarlyAlertCountForCreatedDateRange(String termCode, Date createDatedFrom, Date createdDateTo, Campus campus, String rosterStatus) {
        return dao.getEarlyAlertCountForCreatedDateRange(termCode, createDatedFrom, createdDateTo, campus, rosterStatus);
    }

    @Override
    public Long getClosedEarlyAlertCountForClosedDateRange(Date closedDateFrom, Date closedDateTo, Campus campus, String rosterStatus) {
        return dao.getClosedEarlyAlertCountForClosedDateRange(closedDateFrom, closedDateTo, campus, rosterStatus);
    }

    @Override
    public Long getClosedEarlyAlertsCountForEarlyAlertCreatedDateRange(String termCode, Date createDatedFrom, Date createdDateTo, Campus campus, String rosterStatus) {
        return dao.getClosedEarlyAlertsCountForEarlyAlertCreatedDateRange(termCode, createDatedFrom, createdDateTo, campus, rosterStatus);
    }

    @Override
    public Long getStudentCountForEarlyAlertCreatedDateRange(String termCode, Date createDatedFrom,
                                                             Date createdDateTo, Campus campus, String rosterStatus) {
        return dao.getStudentCountForEarlyAlertCreatedDateRange(termCode, createDatedFrom, createdDateTo, campus, rosterStatus);
    }

    //1
    //1
    @Override
    public PagingWrapper<EarlyAlertStudentReportTO> getStudentsEarlyAlertCountSetForCriteria(
            EarlyAlertStudentSearchTO earlyAlertStudentSearchTO,
            SortingAndPaging createForSingleSort) {
        return dao.getStudentsEarlyAlertCountSetForCriteria(earlyAlertStudentSearchTO, createForSingleSort);
    }

    //1
    @Override
    public List<EarlyAlertCourseCountsTO> getStudentEarlyAlertCountSetPerCourses(
            String termCode, Date createdDateFrom, Date createdDateTo, Campus campus, ObjectStatus objectStatus) {
        return dao.getStudentEarlyAlertCountSetPerCourses(termCode, createdDateFrom, createdDateTo, campus, objectStatus);
    }

    @Override
    public Long getStudentEarlyAlertCountSetPerCoursesTotalStudents(
            String termCode, Date createdDateFrom, Date createdDateTo, Campus campus, ObjectStatus objectStatus) {
        return dao.getStudentEarlyAlertCountSetPerCoursesTotalStudents(termCode, createdDateFrom, createdDateTo, campus, objectStatus);
    }

    //1
    @Override
    public List<Triple<String, Long, Long>> getEarlyAlertReasonTypeCountByCriteria(
            Campus campus, String termCode, Date createdDateFrom, Date createdDateTo, ObjectStatus status) {
        return dao.getEarlyAlertReasonTypeCountByCriteria(campus, termCode, createdDateFrom, createdDateTo, status);
    }

    //1
    @Override
    public List<EarlyAlertReasonCountsTO> getStudentEarlyAlertReasonCountByCriteria(
            String termCode, Date createdDateFrom, Date createdDateTo, Campus campus, ObjectStatus objectStatus) {
        return dao.getStudentEarlyAlertReasonCountByCriteria(termCode, createdDateFrom, createdDateTo, campus, objectStatus);
    }

    @Override
    public Long getStudentEarlyAlertReasonCountByCriteriaTotalStudents(
            String termCode, Date createdDateFrom, Date createdDateTo, Campus campus, ObjectStatus objectStatus) {
        return dao.getStudentEarlyAlertReasonCountByCriteriaTotalStudents(termCode, createdDateFrom, createdDateTo, campus, objectStatus);
    }

    //1
    //1
    @Override
    public PagingWrapper<EntityStudentCountByCoachTO> getStudentEarlyAlertCountByCoaches(EntityCountByCoachSearchForm form) {
        return dao.getStudentEarlyAlertCountByCoaches(form);
    }

    @Override
    public Long getEarlyAlertCountSetForCriteria(EarlyAlertStudentSearchTO searchForm) {
        return dao.getEarlyAlertCountSetForCriteria(searchForm);
    }

    @Override
    public void sendAllEarlyAlertReminderNotifications() {
        Date lastResponseDate = getMinimumResponseComplianceDate();
        // if no responseDate is given no emails are sent
        //1
        if (lastResponseDate == null) {
            return;
        }
        List<EarlyAlert> eaOutOfCompliance = dao.getResponseDueEarlyAlerts(lastResponseDate);

        Map<UUID, List<EarlyAlertMessageTemplateTO>> easByCoach = new HashMap<>();
        Map<UUID, Person> coaches = new HashMap<>();
        final boolean includeCoachAsRecipient = this.earReminderRecipientConfig.includeCoachAsRecipient();
        final boolean includeEarlyAlertCoordinatorAsRecipient = this.earReminderRecipientConfig.includeEarlyAlertCoordinatorAsRecipient();
        final boolean includeEarlyAlertCoordinatorAsRecipientOnlyIfStudentHasNoCoach = this.earReminderRecipientConfig.includeEarlyAlertCoordinatorAsRecipientOnlyIfStudentHasNoCoach();
        LOGGER.info("Config: includeCoachAsRecipient(): {}", includeCoachAsRecipient);
        LOGGER.info("Config: includeEarlyAlertCoordinatorAsRecipient(): {}", includeEarlyAlertCoordinatorAsRecipient);
        LOGGER.info("Config: includeEarlyAlertCoordinatorAsRecipientOnlyIfStudentHasNoCoach(): {}", includeEarlyAlertCoordinatorAsRecipientOnlyIfStudentHasNoCoach);
        //1
        for (EarlyAlert earlyAlert : eaOutOfCompliance) {
            final Set<Person> recipients = new HashSet<>();
            Person coach = earlyAlert.getPerson().getCoach();
            //1
            if (includeCoachAsRecipient) {
                //1
                if (coach == null) {
                    LOGGER.warn("Early Alert with id: {} is associated with a person without a coach, so skipping email to coach.", earlyAlert.getId());
                }
                //1
                else {
                    recipients.add(coach);
                }
            }
            //1
            if (includeEarlyAlertCoordinatorAsRecipient || (coach == null && includeEarlyAlertCoordinatorAsRecipientOnlyIfStudentHasNoCoach)) {
                final Campus campus = earlyAlert.getCampus();
                //1
                if (campus == null) {
                    LOGGER.error("Early Alert with id: {} does not have valid a campus, so skipping email to EAC.", earlyAlert.getId());
                }
                //1
                else {
                    final UUID earlyAlertCoordinatorId = campus.getEarlyAlertCoordinatorId();
                    //1
                    if (earlyAlertCoordinatorId == null) {
                        LOGGER.error("Early Alert with id: {} has campus with no early alert coordinator, so skipping email to EAC.", earlyAlert.getId());
                    }
                    //1
                    else {
                        //1
                        try {
                            final Person earlyAlertCoordinator = personService.get(earlyAlertCoordinatorId);
                            //1
                            if (earlyAlertCoordinator == null) { // guard against change in behavior where ObjectNotFoundException is not thrown (which we've seen)
                                LOGGER.error("Early Alert with id: {} has campus with an early alert coordinator with a bad ID ({}), so skipping email to EAC.", earlyAlert.getId(), earlyAlertCoordinatorId);
                            }
                            //1
                            else {
                                recipients.add(earlyAlertCoordinator);
                            }
                        }
                        //1
                        catch (ObjectNotFoundException exp) {
                            LOGGER.error("Early Alert with id: {} has campus with an early alert coordinator with a bad ID ({}), so skipping email to coach because no coach can be resolved.", new Object[]{earlyAlert.getId(), earlyAlertCoordinatorId, exp});
                        }
                    }
                }
            }
            LOGGER.debug("Early Alert: {}; Recipients: {}", earlyAlert.getId(), recipients);
            //1
            if (recipients.isEmpty()) {
                continue;
            }
            //1
            else {
                //1
                for (Person person : recipients) {
                    // We've definitely got a coach by this point
                    //1
                    if (easByCoach.containsKey(person.getId())) {
                        final List<EarlyAlertMessageTemplateTO> coachEarlyAlerts = easByCoach.get(person.getId());
                        coachEarlyAlerts.add(createEarlyAlertTemplateTO(earlyAlert));
                    }
                    //1
                    else {
                        coaches.put(person.getId(), person);
                        final ArrayList<EarlyAlertMessageTemplateTO> eam = Lists.newArrayList();
                        eam.add(createEarlyAlertTemplateTO(earlyAlert)); // add separately from newArrayList() call else list will be sized to 1
                        easByCoach.put(person.getId(), eam);
                    }
                }
            }
            List<WatchStudent> watchers = earlyAlert.getPerson().getWatchers();
            //1
            for (WatchStudent watcher : watchers) {
                //1
                if (easByCoach.containsKey(watcher.getPerson().getId())) {
                    final List<EarlyAlertMessageTemplateTO> coachEarlyAlerts = easByCoach.get(watcher.getPerson().getId());
                    coachEarlyAlerts.add(createEarlyAlertTemplateTO(earlyAlert));
                }
                //1
                else {
                    coaches.put(watcher.getPerson().getId(), watcher.getPerson());
                    final ArrayList<EarlyAlertMessageTemplateTO> eam = Lists.newArrayList();
                    eam.add(createEarlyAlertTemplateTO(earlyAlert)); // add separately from newArrayList() call else list will be sized to 1
                    easByCoach.put(watcher.getPerson().getId(), eam);
                }
            }
        }
        //1
        for (UUID coachId : easByCoach.keySet()) {
            Map<String, Object> messageParams = new HashMap<>();
            easByCoach.get(coachId).sort(new SortAlertComparatorByDate());

            int daysSince1900ResponseExpected = DateTimeUtils.daysSince1900(lastResponseDate);
            List<Pair<EarlyAlertMessageTemplateTO, Integer>> earlyAlertTOPairs = new ArrayList<>();
            //1
            for (EarlyAlertMessageTemplateTO ea : easByCoach.get(coachId)) {
                int daysOutOfCompliance;
                //1
                if (ea.getLastResponseDate() != null) {
                    daysOutOfCompliance = daysSince1900ResponseExpected - DateTimeUtils.daysSince1900(ea.getLastResponseDate());
                }
                //1
                else {
                    daysOutOfCompliance = daysSince1900ResponseExpected - DateTimeUtils.daysSince1900(ea.getCreatedDate());
                }

                //1
                // Just in case attempt to only send emails for EA full day out of compliance
                if (daysOutOfCompliance >= 0) {
                    earlyAlertTOPairs.add(new Pair<>(ea, daysOutOfCompliance));
                }
            }
            messageParams.put("earlyAlertTOPairs", earlyAlertTOPairs);
            messageParams.put("coach", coaches.get(coachId));
            messageParams.put("DateTimeUtils", DateTimeUtils.class);
            messageParams.put("termToRepresentEarlyAlert", configService.getByNameEmpty("term_to_represent_early_alert"));

            SubjectAndBody subjAndBody = messageTemplateService.createEarlyAlertResponseRequiredToCoachMessage(messageParams);
            //1
            try {
                messageService.createMessage(coaches.get(coachId), null, subjAndBody);
            }
            //1
            catch (Exception exp) {
                LOGGER.error("Unable to send reminder emails to coach: " + coaches.get(coachId).getFullName() + "\n", exp);
            }
        }

    }

    @Override
    public PagedResponse<EarlyAlertSearchResultTO> searchEarlyAlert(EarlyAlertSearchForm form) {
        PagingWrapper<EarlyAlertSearchResult> models = dao.searchEarlyAlert(form);
        return new PagedResponse<>(true, models.getResults(), searchResultFactory.asTOList(models.getRows()));
    }

    public Map<UUID, Number> getResponsesDueCountEarlyAlerts(List<UUID> personIds) {
        Date lastResponseDate = getMinimumResponseComplianceDate();

        //1
        if (lastResponseDate == null) {
            return new HashMap<>();
        }

        return dao.getResponsesDueCountEarlyAlerts(personIds, lastResponseDate);
    }

    private EarlyAlertMessageTemplateTO createEarlyAlertTemplateTO(EarlyAlert earlyAlert) {
        Person creator = null;
        //1
        try {
            creator = personService.get(earlyAlert.getCreatedBy().getId());
        }
        //1
        catch (ObjectNotFoundException exp) {
            LOGGER.error("Early Alert with id: " + earlyAlert.getId() + " does not have valid creator: " + earlyAlert.getCreatedBy(), exp);
        }
        return new EarlyAlertMessageTemplateTO(earlyAlert, creator, earlyAlert.getPerson().getWatcherEmailAddresses());
    }

    private Date getMinimumResponseComplianceDate() {
        final String numVal = configService.getByNameNull("maximum_days_before_early_alert_response");
        //1
        if (StringUtils.isBlank(numVal)) {
            return null;
        }

        int allowedDaysPastResponse = Integer.parseInt(numVal);

        return DateTimeUtils.getDateOffsetInDays(new Date(), -allowedDaysPastResponse);
    }

    /**
     * Send confirmation e-mail ({@link Message}) to the faculty who created
     * this alert.
     *
     * @param earlyAlert Early Alert
     * @throws ObjectNotFoundException
     * @throws ValidationException
     */
    private void sendConfirmationMessageToFaculty(final EarlyAlert earlyAlert)
            throws ObjectNotFoundException, ValidationException {

        checkNotNull(earlyAlert, new IllegalArgumentException("EarlyAlert was missing."));
        checkNotNull(earlyAlert.getPerson(), new IllegalArgumentException("EarlyAlert.Person is missing."));

        //1
        if (!configService.getByNameOrDefaultValue("send_faculty_mail")) {
            LOGGER.debug("Skipping Faculty Early Alert Confirmation Email: Config Turned Off");
            return; //skip if faculty early alert email turned off
        }

        final UUID personId = earlyAlert.getCreatedBy().getId();
        Person person = personService.get(personId);
        //1
        if (person == null) {
            LOGGER.warn("EarlyAlert {} has no creator. Unable to send"
                    + " confirmation message to faculty.", earlyAlert);
        }
        //1
        else {
            final SubjectAndBody subjAndBody = messageTemplateService
                    .createEarlyAlertFacultyConfirmationMessage(fillTemplateParameters(earlyAlert));

            // Create and queue the message
            final Message message = messageService.createMessage(person, null,
                    subjAndBody);

            LOGGER.info("Message {} created for EarlyAlert {}", message, earlyAlert);
        }
    }

    /**
     * Business logic to determine the advisor that is assigned to the student
     * for this Early Alert.
     *
     * @param earlyAlert EarlyAlert instance
     * @return The assigned advisor
     * @throws ValidationException If Early Alert, Student, and/or system information could not
     *                             determine the advisor for this student.
     */
    private UUID getEarlyAlertAdvisor(final EarlyAlert earlyAlert) throws ValidationException {
        // Check for student already assigned to an advisor (a.k.a. coach)
        //1

        if ((earlyAlert.getPerson().getCoach() != null) && (earlyAlert.getPerson().getCoach().getId() != null)) {
            return earlyAlert.getPerson().getCoach().getId();
        }
        checkNotNull(earlyAlert.getCampus(), new IllegalArgumentException("Campus ID can not be null."));

        // Get campus Early Alert coordinator
        //1
        if (earlyAlert.getCampus().getEarlyAlertCoordinatorId() != null) {
            // Return Early Alert coordinator UUID
            return earlyAlert.getCampus().getEarlyAlertCoordinatorId();
        }

        // TODO If no campus EA Coordinator, assign to default EA Coordinator
        // (which is not yet implemented)

        // getEarlyAlertAdvisor should never return null
        throw new ValidationException(
                "Could not determined the Early Alert Coordinator for this student. Ensure that a default coordinator is set globally and for all campuses.");
    }

    private void ensureValidAlertedOnPersonStateNoFail(Person person) {
        //1
        try {
            ensureValidAlertedOnPersonStateOrFail(person);
        }
        //1
        catch (Exception e) {
            LOGGER.error("Unable to set a program status or student type on "
                    + "person '{}'. This is likely to prevent that person "
                    + "record from appearing in caseloads, student searches, "
                    + "and some reports.", person.getId(), e);
        }
    }

    private void ensureValidAlertedOnPersonStateOrFail(Person person) throws ObjectNotFoundException, ValidationException {
        //1
        person.setObjectStatus(ObjectStatus.ACTIVE);

        final ProgramStatus programStatus = programStatusService.getActiveStatus();

        checkNotNull(programStatus, new ObjectNotFoundException("Unable to find a ProgramStatus representing \"activeness\".", "ProgramStatus"));

        Set<PersonProgramStatus> programStatuses = person.getProgramStatuses();

        //1
        if (programStatuses == null || programStatuses.isEmpty()) {
            programStatuses = new HashSet<>();
            PersonProgramStatus personProgramStatus = new PersonProgramStatus();
            personProgramStatus.setEffectiveDate(new Date());
            personProgramStatus.setProgramStatus(programStatus);
            personProgramStatus.setPerson(person);
            programStatuses.add(personProgramStatus);
            person.setProgramStatuses(programStatuses);
            // save should cascade, but make sure custom create logic fires
            personProgramStatusService.create(personProgramStatus);
        }

        //1
        if (person.getStudentType() == null) {
            StudentType studentType = studentTypeService.get(StudentType.EAL_ID);

            checkNotNull(studentType, new ObjectNotFoundException(
                    "Unable to find a StudentType representing an early "
                            + "alert-assigned type.", "StudentType"));

            person.setStudentType(studentType);
        }
    }

    /**
     * Send e-mail ({@link Message}) to the assigned advisor for the student.
     *
     * @param earlyAlert Early Alert
     * @param emailCC    Email address to also CC this message
     * @throws ObjectNotFoundException
     * @throws ValidationException
     */
    private void sendMessageToAdvisor(@NotNull final EarlyAlert earlyAlert, final String emailCC) throws ObjectNotFoundException, ValidationException {
        checkNotNull(earlyAlert, new IllegalArgumentException("Early alert was missing."));
        checkNotNull(earlyAlert.getPerson(), new IllegalArgumentException("EarlyAlert Person is missing."));

        final Person person = earlyAlert.getPerson().getCoach();

        //1
        final SubjectAndBody subjAndBody = messageTemplateService.createEarlyAlertAdvisorConfirmationMessage(fillTemplateParameters(earlyAlert));

        Set<String> watcherEmailAddresses = new HashSet<>(earlyAlert.getPerson().getWatcherEmailAddresses());
        //1
        if (emailCC != null && !emailCC.isEmpty()) {
            watcherEmailAddresses.add(emailCC);
        }
        //1
        if (person == null) {
            LOGGER.warn("Student {} had no coach when EarlyAlert {} was"
                            + " created. Unable to send message to coach.",
                    earlyAlert.getPerson(), earlyAlert);
        }
        //1
        else {
            //1
            // Create and queue the message
            final Message message = messageService.createMessage(person, org.springframework.util.StringUtils.arrayToCommaDelimitedString(watcherEmailAddresses
                    .toArray(new String[watcherEmailAddresses.size()])), subjAndBody);
            LOGGER.info("Message {} created for EarlyAlert {}", message, earlyAlert);
        }

        // Send same message to all applicable Campus Early Alert routing
        // entries
        final PagingWrapper<EarlyAlertRouting> routes = earlyAlertRoutingService
                .getAllForCampus(earlyAlert.getCampus(), new SortingAndPaging(
                        ObjectStatus.ACTIVE));

        //1
        if (routes.getResults() > 0) {
            final ArrayList<String> alreadySent = Lists.newArrayList();

            //1
            for (final EarlyAlertRouting route : routes.getRows()) {
                // Check that route applies

                checkNotNull(route.getEarlyAlertReason(), new ObjectNotFoundException(
                        "EarlyAlertRouting missing EarlyAlertReason.", "EarlyAlertReason"));

                //1
                // Only routes that are for any of the Reasons in this EarlyAlert should be applied.
                if ((earlyAlert.getEarlyAlertReasons() == null) || !earlyAlert.getEarlyAlertReasons().contains(route.getEarlyAlertReason())) {
                    continue;
                }

                // Send e-mail to specific person
                final Person to = route.getPerson();
                //1
                if (to != null && StringUtils.isNotBlank(to.getPrimaryEmailAddress())) {
                    //check if this alert has already been sent to this recipient, if so skip
                    //1
                    if (alreadySent.contains(route.getPerson().getPrimaryEmailAddress())) {
                        continue;
                    }
                    //1
                    else {
                        alreadySent.add(route.getPerson().getPrimaryEmailAddress());
                    }

                    final Message message = messageService.createMessage(to, null, subjAndBody);
                    LOGGER.info("Message {} for EarlyAlert {} also routed to {}", new Object[]{message, earlyAlert, to});
                }

                // Send e-mail to a group
                //1
                if (!StringUtils.isEmpty(route.getGroupName()) && !StringUtils.isEmpty(route.getGroupEmail())) {
                    final Message message = messageService.createMessage(route.getGroupEmail(), null, subjAndBody);
                    LOGGER.info("Message {} for EarlyAlert {} also routed to {}", new Object[]{message, earlyAlert, // NOPMD
                            route.getGroupEmail()});
                }
            }
        }
    }

}
