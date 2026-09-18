/**
 * Spring configuration and the typed application properties (prefix {@code policypilot.},
 * Document 2, Configuration and Model Providers).
 *
 * <p>Allowed dependencies: Spring, Jackson and {@code common}. No module depends on {@code config} except to read
 * {@link com.liorshaya.policypilot.config.PolicyPilotProperties}; {@code rules} and {@code engine} never do.
 */
package com.liorshaya.policypilot.config;
