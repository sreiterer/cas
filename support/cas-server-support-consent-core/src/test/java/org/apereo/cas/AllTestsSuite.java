
package org.apereo.cas;

import org.apereo.cas.consent.DefaultConsentDecisionBuilderTests;
import org.apereo.cas.consent.DefaultConsentEngineTests;
import org.apereo.cas.consent.GroovyConsentRepositoryTests;
import org.apereo.cas.consent.InMemoryConsentRepositoryTests;
import org.apereo.cas.consent.JsonConsentRepositoryTests;

import org.junit.platform.suite.api.SelectClasses;

/**
 * This is {@link AllTestsSuite}.
 *
 * @author Auto-generated by Gradle Build
 * @since 6.0.0-RC3
 */
@SelectClasses({
    DefaultConsentEngineTests.class,
    GroovyConsentRepositoryTests.class,
    DefaultConsentDecisionBuilderTests.class,
    InMemoryConsentRepositoryTests.class,
    JsonConsentRepositoryTests.class
})
public class AllTestsSuite {
}