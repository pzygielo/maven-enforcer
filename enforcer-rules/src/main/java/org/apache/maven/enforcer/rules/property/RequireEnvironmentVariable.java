/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.enforcer.rules.property;

import javax.inject.Named;

/**
 * This rule checks that certain environment variable is set.
 *
 * @author <a href='mailto:marvin[at]marvinformatics[dot]com'>Marvin Froeder</a>
 */
@Named("requireEnvironmentVariable")
public final class RequireEnvironmentVariable extends AbstractPropertyEnforcerRule {

    /**
     * Specify the required variable.
     */
    private String variableName = null;

    /**
     * @param variableName the variable name
     *
     * @see #setVariableName(String)
     * @see #getVariableName()
     */
    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public String getVariableName() {
        return variableName;
    }

    @Override
    public String resolveValue() {
        return System.getenv(variableName);
    }

    @Override
    public String getCacheId() {
        return String.valueOf(toString().hashCode());
    }

    @Override
    public String getPropertyName() {
        return variableName;
    }

    @Override
    public String getName() {
        return "Environment variable";
    }

    @Override
    public String toString() {
        return String.format(
                "RequireEnvironmentVariable[variableName=%s, message=%s, regex=%s, regexMessage=%s]",
                variableName, getMessage(), getRegex(), getRegexMessage());
    }
}