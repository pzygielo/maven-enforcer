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
package org.apache.maven.enforcer.rules.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.codehaus.plexus.util.StringUtils;

import static java.util.Optional.ofNullable;

/**
 * This class is used for matching Artifacts against a list of patterns.
 *
 * @author Jakub Senko
 */
public final class ArtifactMatcher {

    /**
     * @author I don't know
     */
    public static class Pattern {
        private final String pattern;

        private final String[] parts;
        private final Predicate<String>[] partsRegex;

        public Pattern(String pattern) {
            if (pattern == null) {
                throw new NullPointerException("pattern");
            }

            this.pattern = pattern;

            parts = pattern.split(":", 7);

            if (parts.length == 7) {
                throw new IllegalArgumentException("Pattern contains too many delimiters.");
            }

            for (String part : parts) {
                if ("".equals(part)) {
                    throw new IllegalArgumentException("Pattern or its part is empty.");
                }
            }
            partsRegex = new Predicate[parts.length];
        }

        public boolean match(Artifact artifact) {
            Objects.requireNonNull(artifact, "artifact must not be null");
            try {
                return match(
                        artifact.getGroupId(),
                        artifact.getArtifactId(),
                        artifact.getVersion(),
                        artifact.getType(),
                        artifact.getScope(),
                        artifact.getClassifier());
            } catch (InvalidVersionSpecificationException e) {
                throw new IllegalArgumentException(e);
            }
        }

        public boolean match(Dependency dependency) {
            Objects.requireNonNull(dependency, "dependency must not be null");
            try {
                return match(
                        dependency.getGroupId(),
                        dependency.getArtifactId(),
                        dependency.getVersion(),
                        dependency.getType(),
                        dependency.getScope(),
                        dependency.getClassifier());
            } catch (InvalidVersionSpecificationException e) {
                throw new IllegalArgumentException(e);
            }
        }

        private boolean match(
                String groupId, String artifactId, String version, String type, String scope, String classifier)
                throws InvalidVersionSpecificationException {
            switch (parts.length) {
                case 6:
                    if (!matches(5, classifier)) {
                        return false;
                    }
                case 5:
                    if (scope == null || scope.isEmpty()) {
                        scope = Artifact.SCOPE_COMPILE;
                    }

                    if (!matches(4, scope)) {
                        return false;
                    }
                case 4:
                    if (type == null || type.isEmpty()) {
                        type = "jar";
                    }

                    if (!matches(3, type)) {
                        return false;
                    }

                case 3:
                    if (!matches(2, version)) {
                        if (!containsVersion(
                                VersionRange.createFromVersionSpec(parts[2]), new DefaultArtifactVersion(version))) {
                            return false;
                        }
                    }

                case 2:
                    if (!matches(1, artifactId)) {
                        return false;
                    }
                case 1:
                    return matches(0, groupId);
                default:
                    throw new AssertionError();
            }
        }

        private boolean matches(int index, String input) {
            // TODO: Check if this can be done better or prevented earlier.
            if (input == null) {
                input = "";
            }
            if (partsRegex[index] == null) {
                String regex = parts[index]
                        .replace(".", "\\.")
                        .replace("*", ".*")
                        .replace(":", "\\:")
                        .replace('?', '.')
                        .replace("[", "\\[")
                        .replace("]", "\\]")
                        .replace("(", "\\(")
                        .replace(")", "\\)");

                if (".*".equals(regex)) {
                    partsRegex[index] = test -> true;
                } else {
                    partsRegex[index] = test ->
                            java.util.regex.Pattern.compile(regex).matcher(test).matches();
                }
            }
            return partsRegex[index].test(input);
        }

        @Override
        public String toString() {
            return pattern;
        }
    }

    private final Collection<Pattern> excludePatterns = new HashSet<>();

    private final Collection<Pattern> includePatterns = new HashSet<>();

    /**
     * Construct class by providing patterns as strings. Empty strings are ignored.
     *
     * @param excludeStrings includes
     * @param includeStrings excludes
     * @throws NullPointerException if any of the arguments is null
     */
    public ArtifactMatcher(final Collection<String> excludeStrings, final Collection<String> includeStrings) {
        ofNullable(excludeStrings).ifPresent(excludes -> excludes.stream()
                .filter(StringUtils::isNotEmpty)
                .map(Pattern::new)
                .forEach(excludePatterns::add));
        ofNullable(includeStrings).ifPresent(includes -> includes.stream()
                .filter(StringUtils::isNotEmpty)
                .map(Pattern::new)
                .forEach(includePatterns::add));
    }

    private boolean match(Function<Pattern, Boolean> matcher) {
        return excludePatterns.stream().anyMatch(matcher::apply)
                && includePatterns.stream().noneMatch(matcher::apply);
    }

    /**
     * Check if artifact matches patterns.
     *
     * @param artifact the artifact to match
     * @return {@code true} if artifact matches any {@link #excludePatterns} and none of the {@link #includePatterns}, otherwise
     *         {@code false}
     */
    public boolean match(Artifact artifact) {
        return match(p -> p.match(artifact));
    }

    /**
     * Check if dependency matches patterns.
     *
     * @param dependency the dependency to match
     * @return {@code true} if dependency matches any {@link #excludePatterns} and none of the {@link #includePatterns},
     *         otherwise {@code false}
     */
    public boolean match(Dependency dependency) {
        return match(p -> p.match(dependency));
    }

    /**
     * Copied from Artifact.VersionRange. This is tweaked to handle singular ranges properly. The default
     * containsVersion method assumes a singular version means allow everything.
     * This method assumes that "2.0.4" == "[2.0.4,)"
     *
     * @param allowedRange range of allowed versions
     * @param version the version to be checked
     * @return true if the version is contained by the range
     */
    public static boolean containsVersion(VersionRange allowedRange, ArtifactVersion version) {
        ArtifactVersion recommendedVersion = allowedRange.getRecommendedVersion();
        if (recommendedVersion == null) {
            return allowedRange.containsVersion(version);
        } else {
            // only singular versions ever have a recommendedVersion
            int compareTo = recommendedVersion.compareTo(version);
            return compareTo <= 0;
        }
    }

    /**
     * To be used for artifacts which are equivalent for the purposes of the {@link ArtifactMatcher}.
     */
    public static class MatchingArtifact {
        String artifactString;

        public MatchingArtifact(Artifact artifact) {
            artifactString = new StringBuilder()
                    .append(artifact.getGroupId())
                    .append(":")
                    .append(artifact.getArtifactId())
                    .append(":")
                    .append(artifact.getVersion())
                    .append(":")
                    .append(artifact.getType())
                    .append(":")
                    .append(artifact.getScope())
                    .append(":")
                    .append(artifact.getClassifier())
                    .toString();
        }

        @Override
        public int hashCode() {
            return artifactString.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            MatchingArtifact other = (MatchingArtifact) obj;
            return Objects.equals(artifactString, other.artifactString);
        }

        @Override
        public String toString() {
            return artifactString;
        }
    }
}
