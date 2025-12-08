/*
 * Copyright 2012-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.liquibase;

import javax.annotation.Nullable;

import liquibase.exception.ChangeLogParseException;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * An {@link AbstractFailureAnalyzer} that performs analysis of failures caused by a
 * missing Liquibase changelog.
 *
 * @author Andy Wilkinson
 */
class LiquibaseChangelogMissingFailureAnalyzer extends AbstractFailureAnalyzer<ChangeLogParseException> {

	private static final String MESSAGE_SUFFIX = " does not exist";

	@Nullable
	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, ChangeLogParseException cause) {
		String message = cause.getMessage();
		if (message == null || !message.endsWith(MESSAGE_SUFFIX)) {
			return null;
		}
		String changelogPath = extractChangelogPath(message);
		return new FailureAnalysis(getDescription(changelogPath),
				"Make sure a Liquibase changelog is present at the configured path.", cause);
	}

	private String extractChangelogPath(String message) {
		return message.substring(0, message.length() - MESSAGE_SUFFIX.length());
	}

	private String getDescription(String changelogPath) {
		return "Liquibase failed to start because no changelog could be found at '" + changelogPath + "'.";
	}

}
