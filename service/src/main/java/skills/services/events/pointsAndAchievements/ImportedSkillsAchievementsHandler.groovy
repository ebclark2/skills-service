/**
 * Copyright 2020 SkillTree
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package skills.services.events.pointsAndAchievements

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import skills.services.RuleSetDefGraphService
import skills.services.UserAchievementsAndPointsManagement
import skills.services.events.SkillDate
import skills.storage.model.SkillDef
import skills.storage.model.SkillDefMin
import skills.storage.model.SkillRelDef
import skills.storage.repos.SkillDefRepo
import skills.storage.repos.SkillRelDefRepo

@Component
@Slf4j
@CompileStatic
class ImportedSkillsAchievementsHandler {

    @Autowired
    SkillDefRepo skillDefRepo

    @Autowired
    RuleSetDefGraphService ruleSetDefGraphService

    @Autowired
    PointsAndAchievementsHandler pointsAndAchievementsHandler

    @Autowired
    UserAchievementsAndPointsManagement userAchievementsAndPointsManagement

    void handleAchievementsForImportedSkills(String userId, SkillDefMin skill, SkillDate incomingSkillDate) {
        List<SkillDefMin> skills = skillDefRepo.findSkillDefMinCopiedFrom(skill.id)
        List<SkillDef> subjects = []
        skills?.each {
            pointsAndAchievementsHandler.updatePointsAndAchievements(userId, it, incomingSkillDate)
            SkillDef parent = ruleSetDefGraphService.getParentSkill(it.id)
            assert parent.type == SkillDef.ContainerType.Subject
            if (!subjects.find { it.id == parent.id}) {
                subjects.add(parent)
            }
        }

        subjects.each {
            userAchievementsAndPointsManagement.identifyAndAddLevelAchievements(it)
        }
    }
}