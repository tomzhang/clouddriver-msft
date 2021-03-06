/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kato.aws.deploy.validators

import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import com.netflix.spinnaker.kato.aws.deploy.description.DeleteAsgTagsDescription
import org.springframework.validation.Errors

class DeleteAsgTagsDescriptionValidatorSpec extends AbstractConfiguredRegionsValidatorSpec {

  @Override
  DescriptionValidator getDescriptionValidator() {
    new DeleteAsgTagsDescriptionValidator()
  }

  @Override
  DeleteAsgTagsDescription getDescription() {
    new DeleteAsgTagsDescription()
  }

  void "invalid tags fails validation"() {
    setup:
    def description = new DeleteAsgTagsDescription()
    description.tagKeys = [null]
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("tagKeys", "deleteAsgTagsDescription.tagKey.invalid")
  }

}
