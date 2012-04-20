package org.studentsuccessplan.mygps.web;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.studentsuccessplan.mygps.model.transferobject.MessageTO;
import org.studentsuccessplan.ssp.model.Person;
import org.studentsuccessplan.ssp.model.reference.MessageTemplate;
import org.studentsuccessplan.ssp.service.MessageService;

@Controller
@RequestMapping("/mygps/message")
public class MyGpsMessageController extends AbstractMyGpsController {

	@Autowired
	private MessageService messageService;

	private static final Logger LOGGER = LoggerFactory
			.getLogger(MyGpsMessageController.class);

	@RequestMapping(method = RequestMethod.POST)
	public @ResponseBody
	Boolean contactCoach(@RequestBody MessageTO messageTO,
			HttpServletResponse response) throws Exception {

		try {
			if ((securityService.currentUser().getPerson() == null)
					|| (securityService.currentUser().getPerson()
							.getDemographics().getCoach() == null)) {
				return false;
			}

			Person coach = securityService.currentUser()
					.getPerson().getDemographics().getCoach();

			Map<String, Object> messageParams = new HashMap<String, Object>();
			messageParams.put("subj", messageTO.getSubject());
			messageParams.put("mesg", messageTO.getMessage());

			messageService.createMessage(coach,
					MessageTemplate.EMPTY_TEMPLATE_EMAIL_ID,
					messageParams);

			return true;
		} catch (Exception e) {
			LOGGER.error("ERROR : contactCoach() : {}", e.getMessage(), e);
			throw e;
		}
	}

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}

}
