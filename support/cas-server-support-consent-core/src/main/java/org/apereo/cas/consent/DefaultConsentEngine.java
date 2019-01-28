package org.apereo.cas.consent;

import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.util.function.FunctionUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.apereo.inspektr.audit.annotation.Audit;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This is {@link DefaultConsentEngine}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
@Slf4j
@RequiredArgsConstructor
@Getter
public class DefaultConsentEngine implements ConsentEngine {
    private static final long serialVersionUID = -617809298856160625L;

    private final ConsentRepository consentRepository;
    private final ConsentDecisionBuilder consentDecisionBuilder;

    @Override
    public Pair<Boolean, ConsentDecision> isConsentRequiredFor(final Service service,
                                                               final RegisteredService registeredService,
                                                               final Authentication authentication) {
        val attributes = resolveConsentableAttributesFrom(authentication, service, registeredService);

        if (attributes == null || attributes.isEmpty()) {
            LOGGER.debug("Consent is conditionally ignored for service [{}] given no consentable attributes are found", registeredService.getName());
            return Pair.of(Boolean.FALSE, null);
        }

        LOGGER.debug("Locating consent decision for service [{}]", service);
        val decision = findConsentDecision(service, registeredService, authentication);
        if (decision == null) {
            LOGGER.debug("No consent decision found; thus attribute consent is required");
            return Pair.of(Boolean.TRUE, null);
        }

        LOGGER.debug("Located consentable attributes for release [{}]", attributes.keySet());
        if (consentDecisionBuilder.doesAttributeReleaseRequireConsent(decision, attributes)) {
            LOGGER.debug("Consent is required based on past decision [{}] and attribute release policy for [{}]",
                decision, registeredService.getName());
            return Pair.of(Boolean.TRUE, decision);
        }

        LOGGER.debug("Consent is not required yet for [{}]; checking for reminder options", service);
        val unit = decision.getReminderTimeUnit();
        val dt = decision.getCreatedDate().plus(decision.getReminder(), unit);
        val now = LocalDateTime.now();

        LOGGER.debug("Reminder threshold date/time is calculated as [{}]", dt);
        if (now.isAfter(dt)) {
            LOGGER.debug("Consent is required based on reminder options given now at [{}] is after [{}]", now, dt);
            return Pair.of(Boolean.TRUE, decision);
        }

        LOGGER.debug("Consent is not required for service [{}]", service);
        return Pair.of(Boolean.FALSE, null);
    }

    @Audit(action = "SAVE_CONSENT",
        actionResolverName = "SAVE_CONSENT_ACTION_RESOLVER",
        resourceResolverName = "SAVE_CONSENT_RESOURCE_RESOLVER")
    @Override
    public ConsentDecision storeConsentDecision(final Service service,
                                                final RegisteredService registeredService,
                                                final Authentication authentication,
                                                final long reminder,
                                                final ChronoUnit reminderTimeUnit,
                                                final ConsentReminderOptions options) {
        val attributes = resolveConsentableAttributesFrom(authentication, service, registeredService);
        val principalId = authentication.getPrincipal().getId();

        val decisionFound = findConsentDecision(service, registeredService, authentication);

        val supplier = FunctionUtils.doIfNull(decisionFound,
            () -> consentDecisionBuilder.build(service, registeredService, principalId, attributes),
            () -> consentDecisionBuilder.update(decisionFound, attributes));

        val decision = supplier.get();
        decision.setOptions(options);
        decision.setReminder(reminder);
        decision.setReminderTimeUnit(reminderTimeUnit);

        if (consentRepository.storeConsentDecision(decision)) {
            return decision;
        }
        throw new IllegalArgumentException("Could not store consent decision");
    }

    @Override
    public ConsentDecision findConsentDecision(final Service service,
                                               final RegisteredService registeredService,
                                               final Authentication authentication) {
        return consentRepository.findConsentDecision(service, registeredService, authentication);
    }

    @Override
    public Map<String, Object> resolveConsentableAttributesFrom(final ConsentDecision decision) {
        LOGGER.debug("Retrieving consentable attributes from existing decision made by [{}] for [{}]",
            decision.getPrincipal(), decision.getService());
        return this.consentDecisionBuilder.getConsentableAttributesFrom(decision);
    }

    @Override
    public Map<String, Object> resolveConsentableAttributesFrom(final Authentication authentication,
                                                                final Service service,
                                                                final RegisteredService registeredService) {
        LOGGER.debug("Retrieving consentable attributes for [{}]", registeredService);
        val policy = registeredService.getAttributeReleasePolicy();
        if (policy != null) {
            return policy.getConsentableAttributes(authentication.getPrincipal(), service, registeredService);
        }
        return new LinkedHashMap<>();
    }
}