package org.jasig.ssp.service.impl;

import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.jasig.ssp.model.EarlyAlert;
import org.jasig.ssp.model.Person;
import org.jasig.ssp.model.external.FacultyCourse;
import org.jasig.ssp.model.external.Term;
import org.jasig.ssp.model.reference.EnrollmentStatus;
import org.jasig.ssp.service.ObjectNotFoundException;
import org.jasig.ssp.service.PersonService;
import org.jasig.ssp.service.external.FacultyCourseService;
import org.jasig.ssp.service.external.TermService;
import org.jasig.ssp.service.reference.ConfigService;
import org.jasig.ssp.service.reference.EnrollmentStatusService;
import org.jasig.ssp.transferobject.messagetemplate.CoachPersonLiteMessageTemplateTO;
import org.jasig.ssp.transferobject.messagetemplate.EarlyAlertMessageTemplateTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.NotNull;
import java.util.Map;

import static org.jasig.ssp.service.impl.ObjectsUtil.checkNotNull;

@Service
@Transactional
public class TemplateParameterFillerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateParameterFillerService.class);

    @Autowired
    private transient PersonService personService;

    @Autowired
    private transient FacultyCourseService facultyCourseService;

    @Autowired
    private transient TermService termService;

    @Autowired
    private transient EnrollmentStatusService enrollmentStatusService;

    @Autowired
    private transient ConfigService configService;

    public Map<String, Object> fillTemplateParameters(@NotNull final EarlyAlert earlyAlert) {

        checkNotNull(earlyAlert, new IllegalArgumentException("EarlyAlert was missing."));
        checkNotNull(earlyAlert.getPerson(), new IllegalArgumentException("EarlyAlert.Person is missing."));
        checkNotNull(earlyAlert.getCreatedBy(), new IllegalArgumentException("EarlyAlert.CreatedBy is missing."));
        checkNotNull(earlyAlert.getCampus(), new IllegalArgumentException("EarlyAlert.Campus is missing."));

        // ensure earlyAlert.createdBy is populated

        checkNotNull(earlyAlert.getCreatedBy(), new IllegalArgumentException("EarlyAlert.CreatedBy is missing."));
        checkNotNull(earlyAlert.getCreatedBy().getFirstName(), new IllegalArgumentException("EarlyAlert.CreatedBy is missing."));

        final Map<String, Object> templateParameters = Maps.newHashMap();

        final String courseName = earlyAlert.getCourseName();

        //1
        if (StringUtils.isNotBlank(courseName)) {
            Person creator;
            //1
            try {
                creator = personService.get(earlyAlert.getCreatedBy().getId());
            }
            //1
            catch (ObjectNotFoundException e1) {
                throw new IllegalArgumentException("EarlyAlert.CreatedBy.Id could not be loaded.", e1);
            }

            final String facultySchoolId = creator.getSchoolId();
            //1
            if ((StringUtils.isNotBlank(facultySchoolId))) {
                String termCode = earlyAlert.getCourseTermCode();
                FacultyCourse course = null;
                //1
                try {
                    //1
                    if (StringUtils.isBlank(termCode)) {
                        course = facultyCourseService.
                                getCourseByFacultySchoolIdAndFormattedCourse(
                                        facultySchoolId, courseName);
                    }
                    //1
                    else {
                        course = facultyCourseService.
                                getCourseByFacultySchoolIdAndFormattedCourseAndTermCode(
                                        facultySchoolId, courseName, termCode);
                    }
                }
                //1
                catch (ObjectNotFoundException e) {
                    // Trace irrelevant. see below for logging. prefer to
                    // do it there, after the null check b/c not all service
                    // methods implement ObjectNotFoundException reliably.
                }
                //1
                if (course != null) {
                    templateParameters.put("course", course);
                    //1
                    if (StringUtils.isBlank(termCode)) {
                        termCode = course.getTermCode();
                    }
                    //1
                    if (StringUtils.isNotBlank(termCode)) {
                        Term term = null;
                        //1
                        try {
                            term = termService.getByCode(termCode);
                        }
                        //1
                        catch (ObjectNotFoundException e) {
                            // Trace irrelevant. See below for logging.
                        }
                        //1
                        if (term != null) {
                            templateParameters.put("term", term);
                        }
                        //1
                        else {
                            LOGGER.info("Not adding term to message template"
                                            + " params or early alert {} because"
                                            + " the term code {} did not resolve to"
                                            + " an external term record",
                                    earlyAlert.getId(), termCode);
                        }
                    }
                }
                //1
                else {
                    LOGGER.info("Not adding course nor term to message template"
                                    + " params for early alert {} because the associated"
                                    + " course {} and faculty school id {} did not"
                                    + " resolve to an external course record.",
                            new Object[]{earlyAlert.getId(), courseName,
                                    facultySchoolId});
                }
            }
        }
        Person creator = null;
        //1
        try {
            creator = personService.get(earlyAlert.getCreatedBy().getId());
        }
        //1
        catch (ObjectNotFoundException exp) {
            LOGGER.error("Early Alert Creator Not found sending message for early alert:" + earlyAlert.getId(), exp);
        }

        EarlyAlertMessageTemplateTO eaMTO = new EarlyAlertMessageTemplateTO(earlyAlert, creator);

        //1
        //Only early alerts response late messages sent to coaches
        if (eaMTO.getCoach() == null) {
            //1
            try {
                // if no earlyAlert.getCampus()  error thrown by design, should never not be a campus.
                eaMTO.setCoach(new CoachPersonLiteMessageTemplateTO(personService.get(earlyAlert.getCampus().getEarlyAlertCoordinatorId())));
            }
            //1
            catch (ObjectNotFoundException exp) {
                LOGGER.error("Early Alert with id: " + earlyAlert.getId() + " does not have valid campus coordinator, no coach assigned: " + earlyAlert.getCampus().getEarlyAlertCoordinatorId(), exp);
            }
        }

        String statusCode = eaMTO.getEnrollmentStatus();
        //1
        if (statusCode != null) {
            EnrollmentStatus enrollmentStatus = enrollmentStatusService.getByCode(statusCode);
            //1
            if (enrollmentStatus != null) {
                //if we have made it here... we can add the status!
                templateParameters.put("enrollment", enrollmentStatus);
            }
        }

        templateParameters.put("earlyAlert", eaMTO);
        templateParameters.put("termToRepresentEarlyAlert", configService.getByNameEmpty("term_to_represent_early_alert"));
        templateParameters.put("TermToRepresentEarlyAlert", configService.getByNameEmpty("term_to_represent_early_alert"));
        templateParameters.put("termForEarlyAlert", configService.getByNameEmpty("term_to_represent_early_alert"));
        templateParameters.put("linkToSSP", configService.getByNameEmpty("serverExternalPath"));
        templateParameters.put("applicationTitle", configService.getByNameEmpty("app_title"));
        templateParameters.put("institutionName", configService.getByNameEmpty("inst_name"));
        templateParameters.put("FirstName", eaMTO.getPerson().getFirstName());
        templateParameters.put("LastName", eaMTO.getPerson().getLastName());
        templateParameters.put("CourseName", eaMTO.getCourseName());

        return templateParameters;
    }
}
