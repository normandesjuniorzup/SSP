package org.studentsuccessplan.mygps.web;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.studentsuccessplan.ssp.factory.reference.ChallengeReferralTOFactory;
import org.studentsuccessplan.ssp.model.reference.Challenge;
import org.studentsuccessplan.ssp.service.reference.ChallengeReferralService;
import org.studentsuccessplan.ssp.service.reference.ChallengeService;
import org.studentsuccessplan.ssp.transferobject.reference.ChallengeReferralTO;

import com.google.common.collect.Lists;

@Controller
@RequestMapping("/1/mygps/challengereferral")
public class MyGpsChallengeReferralController extends AbstractMyGpsController {

	@Autowired
	private transient ChallengeReferralService challengeReferralService;

	@Autowired
	private transient ChallengeReferralTOFactory challengeReferralTOFactory;

	@Autowired
	private transient ChallengeService challengeService;

	private static final Logger LOGGER = LoggerFactory
			.getLogger(MyGpsChallengeReferralController.class);

	public MyGpsChallengeReferralController() {
	}

	public MyGpsChallengeReferralController(
			ChallengeReferralService challengeReferralService,
			ChallengeService challengeService,
			ChallengeReferralTOFactory challangeReferralTOFactory) {
		this.challengeReferralService = challengeReferralService;
		this.challengeService = challengeService;
		this.challengeReferralTOFactory = challangeReferralTOFactory;
	}

	@RequestMapping(value = "/getByChallengeId", method = RequestMethod.GET)
	public @ResponseBody
	List<ChallengeReferralTO> getByChallengeId(
			@RequestParam("challengeId") UUID challengeId) throws Exception {

		try {
			Challenge challenge = challengeService.get(challengeId);
			return challengeReferralTOFactory.asTOList(challengeReferralService
					.getChallengeReferralsByChallengeId(challenge));

		} catch (Exception e) {
			LOGGER.error("ERROR : getByChallengeId() : {}", e.getMessage(), e);
			throw e;
		}
	}

	@RequestMapping(value = "/search", method = RequestMethod.GET)
	public @ResponseBody
	List<ChallengeReferralTO> search(@RequestParam("query") String query,
			@RequestParam("challengeId") UUID challengeId) throws Exception {

		// Not using query param as we are no longer searching the
		// referrals, simply returning based on challengeId and whether the
		// referral is public.

		try {
			Challenge challenge = challengeService.get(challengeId);
			if (challenge != null) {
				return challengeReferralTOFactory
						.asTOList(challengeReferralService
								.challengeReferralSearch(challenge));
			} else {
				return Lists.newArrayList();
			}
		} catch (Exception e) {
			LOGGER.error("ERROR : search() : {}", e.getMessage(), e);
			throw e;
		}
	}

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}
}
