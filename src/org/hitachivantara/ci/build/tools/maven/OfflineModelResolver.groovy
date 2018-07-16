/*!
 * HITACHI VANTARA PROPRIETARY AND CONFIDENTIAL
 *
 * Copyright 2018 Hitachi Vantara. All rights reserved.
 *
 * NOTICE: All information including source code contained herein is, and
 * remains the sole property of Hitachi Vantara and its licensors. The intellectual
 * and technical concepts contained herein are proprietary and confidential
 * to, and are trade secrets of Hitachi Vantara and may be covered by U.S. and foreign
 * patents, or patents in process, and are protected by trade secret and
 * copyright laws. The receipt or possession of this source code and/or related
 * information does not convey or imply any rights to reproduce, disclose or
 * distribute its contents, or to manufacture, use, or sell anything that it
 * may describe, in whole or in part. Any reproduction, modification, distribution,
 * or public display of this information without the express written authorization
 * from Hitachi Vantara is strictly prohibited and in violation of applicable laws and
 * international treaties. Access to the source code contained herein is strictly
 * prohibited to anyone except those individuals and entities who have executed
 * confidentiality and non-disclosure agreements or other agreements with Hitachi Vantara,
 * explicitly covering such access.
 */

package org.hitachivantara.ci.build.tools.maven

import com.cloudbees.groovy.cps.NonCPS
import org.apache.maven.model.Dependency
import org.apache.maven.model.Parent
import org.apache.maven.model.Repository
import org.apache.maven.model.building.ModelSource
import org.apache.maven.model.building.StringModelSource
import org.apache.maven.model.resolution.InvalidRepositoryException
import org.apache.maven.model.resolution.ModelResolver
import org.apache.maven.model.resolution.UnresolvableModelException

/**
 * Dumb model resolver that just returns a fake pom for the requested parent. This is used for external pom resolution
 * and we don't need to actually resolve it, at least for now.
 */
class OfflineModelResolver implements ModelResolver, Serializable {
  OfflineModelResolver() {
  }

  @NonCPS
  ModelSource resolveModel(Dependency dependency) throws UnresolvableModelException {
    resolveModel(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion())
  }

  @NonCPS
  ModelSource resolveModel(String groupId, String artifactId, String version) throws UnresolvableModelException {
    new StringModelSource("""<?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <groupId>${groupId}</groupId>
          <artifactId>${artifactId}</artifactId>
          <version>${version}</version>
          <packaging>pom</packaging>
        </project>
        """
    )
  }

  @NonCPS
  ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
    resolveModel(parent.getGroupId(), parent.getArtifactId(), parent.getVersion())
  }

  @NonCPS
  void addRepository(Repository repository) throws InvalidRepositoryException {
    /* NO-OP */
  }

  @NonCPS
  void addRepository(Repository repository, boolean b) throws InvalidRepositoryException {
    /* NO-OP */
  }

  @NonCPS
  ModelResolver newCopy() {
    new OfflineModelResolver()
  }
}