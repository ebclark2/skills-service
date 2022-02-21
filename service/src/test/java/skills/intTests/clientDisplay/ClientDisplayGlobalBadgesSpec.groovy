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
package skills.intTests.clientDisplay

import groovy.util.logging.Slf4j
import org.junit.Ignore
import skills.intTests.utils.DefaultIntSpec
import skills.intTests.utils.SkillsClientException
import skills.intTests.utils.SkillsFactory
import skills.intTests.utils.SkillsService
import spock.lang.IgnoreRest

@Slf4j
class ClientDisplayGlobalBadgesSpec extends DefaultIntSpec {
    String projId = SkillsFactory.defaultProjId
    String globalBadgeId = 'GlobalBadge1'

    String ultimateRoot = 'jh@dojo.com'
    SkillsService rootSkillsService
    String supervisorUserId = 'foo@bar.com'
    SkillsService supervisorSkillsService

    def setup(){
        skillsService.deleteProjectIfExist(projId)
        rootSkillsService = createService(ultimateRoot, 'aaaaaaaa')
        supervisorSkillsService = createService(supervisorUserId)

        if (!rootSkillsService.isRoot()) {
            rootSkillsService.grantRoot()
        }
        rootSkillsService.grantSupervisorRole(supervisorUserId)
    }

    def cleanup() {
        deleteGlobalBadgeIfExists(globalBadgeId)
        rootSkillsService?.removeSupervisorRole(supervisorUserId)
    }

    def "badges summary for a project - one badge"() {
        String userId = "user1"

        def proj1 = SkillsFactory.createProject(1)
        def proj1_subj = SkillsFactory.createSubject(1, 1)
        List<Map> proj1_skills = SkillsFactory.createSkills(3, 1, 1)

        skillsService.createProject(proj1)
        skillsService.createSubject(proj1_subj)
        skillsService.createSkills(proj1_skills)

        Map badge = [badgeId: globalBadgeId, name: 'Badge 1', description: 'This is a first badge', iconClass: "fa fa-seleted-icon"]
        badge.helpUrl = "http://foo.org"
        supervisorSkillsService.createGlobalBadge(badge)
        supervisorSkillsService.assignProjectLevelToGlobalBadge(projectId: projId, badgeId: badge.badgeId, level: "3")
        supervisorSkillsService.assignSkillToGlobalBadge(projectId: projId, badgeId: badge.badgeId, skillId: proj1_skills.get(0).skillId)
        badge.enabled  = 'true'
        supervisorSkillsService.updateGlobalBadge(badge, badge.badgeId)

        when:
        def summaries = skillsService.getBadgesSummary(userId, projId)
        then:
        summaries.size() == 1
        def summary = summaries.first()
        summary.badge == "Badge 1"
        summary.badgeId == globalBadgeId
        summary.global
        !summary.gem
        !summary.startDate
        !summary.endDate
        summary.numTotalSkills == 2
        summary.numSkillsAchieved == 0
        summary.iconClass == "fa fa-seleted-icon"
        summary.helpUrl == "http://foo.org"
    }

    def "badges summary for a project - one badge - achieved"() {
        String userId = "user1"

        def proj1 = SkillsFactory.createProject(1)
        def proj1_subj = SkillsFactory.createSubject(1, 1)
        List<Map> proj1_skills = SkillsFactory.createSkills(3, 1, 1)
        proj1_skills.each{
            it.pointIncrement = 40
        }

        skillsService.createProject(proj1)
        skillsService.createSubject(proj1_subj)
        skillsService.createSkills(proj1_skills)

        Map badge = [badgeId: globalBadgeId, name: 'Badge 1', description: 'This is a first badge', iconClass: "fa fa-seleted-icon",]
        supervisorSkillsService.createGlobalBadge(badge)
        supervisorSkillsService.assignSkillToGlobalBadge(projectId: projId, badgeId: badge.badgeId, skillId: proj1_skills.get(0).skillId)
        badge.enabled  = 'true'
        supervisorSkillsService.updateGlobalBadge(badge, badge.badgeId)

        skillsService.addSkill([projectId: proj1.projectId, skillId: proj1_skills.get(0).skillId], userId, new Date())

        when:
        def summaries = skillsService.getBadgesSummary(userId, proj1.projectId)
        then:
        summaries.size() == 1
        def summary = summaries.first()
        summary.badge == "Badge 1"
        summary.badgeId == globalBadgeId
        summary.global
        !summary.gem
        !summary.startDate
        !summary.endDate
        summary.numTotalSkills == 1
        summary.numSkillsAchieved == 1
        summary.iconClass == "fa fa-seleted-icon"
    }

    def "badges summary for a project level - one badge - achieved"() {
        String userId = "user1"

        def proj1 = SkillsFactory.createProject(1)
        def proj1_subj = SkillsFactory.createSubject(1, 1)
        List<Map> proj1_skills = SkillsFactory.createSkills(3, 1, 1)
        proj1_skills.each{
            it.pointIncrement = 40
        }

        skillsService.createProject(proj1)
        skillsService.createSubject(proj1_subj)
        skillsService.createSkills(proj1_skills)

        Map badge = [badgeId: globalBadgeId, name: 'Badge 1', description: 'This is a first badge', iconClass: "fa fa-seleted-icon",]
        supervisorSkillsService.createGlobalBadge(badge)
//        supervisorSkillsService.assignSkillToGlobalBadge(projectId: projId, badgeId: badge.badgeId, skillId: proj1_skills.get(0).skillId)

        supervisorSkillsService.assignProjectLevelToGlobalBadge(projectId: proj1.projectId, badgeId: badge.badgeId, level: "1")

        skillsService.addSkill([projectId: proj1.projectId, skillId: proj1_skills.get(0).skillId], userId, new Date())

        when:
        def summaries = skillsService.getBadgesSummary(userId, proj1.projectId)
        then:
        summaries.size() == 1
        def summary = summaries.first()
        summary.badge == "Badge 1"
        summary.badgeId == globalBadgeId
        summary.global
        !summary.gem
        !summary.startDate
        !summary.endDate
        summary.numTotalSkills == 1
        summary.numSkillsAchieved == 1
        summary.projectLevelsAndSkillsSummaries.projectLevel.achievedLevel[0] >= summary.projectLevelsAndSkillsSummaries.projectLevel.requiredLevel[0]
        summary.iconClass == "fa fa-seleted-icon"
    }


    def "badges summary for a project - few badges"() {
        String userId = "user1"

        def proj1 = SkillsFactory.createProject(1)
        def proj1_subj = SkillsFactory.createSubject(1, 1)
        List<Map> proj1_skills = SkillsFactory.createSkills(5, 1, 1)
        proj1_skills.each{
            it.pointIncrement = 40
        }

        skillsService.createProject(proj1)
        skillsService.createSubject(proj1_subj)
        skillsService.createSkills(proj1_skills)

        List<String> badgeIds = (1..3).collect({ "${globalBadgeId}${it}".toString()})
        badgeIds.each {
            Map badge = [badgeId: it, name: it, description: "This is ${it}".toString(), iconClass: "fa fa-${it}".toString(),]
            supervisorSkillsService.createGlobalBadge(badge)
        }

        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: badgeIds.get(0), skillId: proj1_skills.get(0).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: badgeIds.get(0), skillId: proj1_skills.get(1).skillId])
        skillsService.addSkill([projectId: proj1.projectId, skillId: proj1_skills.get(0).skillId], userId, new Date())

        supervisorSkillsService.assignProjectLevelToGlobalBadge(projectId: proj1.projectId, badgeId: badgeIds.get(1), level: "3")

        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: badgeIds.get(2), skillId: proj1_skills.get(0).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: badgeIds.get(2), skillId: proj1_skills.get(1).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: badgeIds.get(2), skillId: proj1_skills.get(2).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: badgeIds.get(2), skillId: proj1_skills.get(3).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: badgeIds.get(2), skillId: proj1_skills.get(4).skillId])

        when:
        def summaries = skillsService.getBadgesSummary(userId, proj1.projectId)
        then:
        summaries.size() == 3
        summaries.get(0).badge == badgeIds[0]
        summaries.get(0).badgeId == badgeIds[0]
        summaries.get(0).iconClass == "fa fa-${badgeIds[0]}"
        summaries.get(0).numSkillsAchieved == 1
        summaries.get(0).numTotalSkills == 2

        summaries.get(1).badge == badgeIds[1]
        summaries.get(1).badgeId == badgeIds[1]
        summaries.get(1).iconClass == "fa fa-${badgeIds[1]}"
        summaries.get(1).numSkillsAchieved == 0
        summaries.get(1).numTotalSkills == 1

        summaries.get(2).badge == badgeIds[2]
        summaries.get(2).badgeId == badgeIds[2]
        summaries.get(2).iconClass == "fa fa-${badgeIds[2]}"
        summaries.get(2).numSkillsAchieved == 1
        summaries.get(2).numTotalSkills == 5

        cleanup:
        badgeIds.each {
            deleteGlobalBadgeIfExists(it)
        }
    }

    def "single badge summary"() {
        String userId = "user1"

        def proj1 = SkillsFactory.createProject(1)
        def proj1_subj = SkillsFactory.createSubject(1, 1)
        List<Map> proj1_skills = SkillsFactory.createSkills(3, 1, 1)

        skillsService.createProject(proj1)
        skillsService.createSubject(proj1_subj)
        skillsService.createSkills(proj1_skills)

        Map badge = [badgeId: globalBadgeId, name: 'Badge 1', description: 'This is a first badge', iconClass: "fa fa-seleted-icon",]
        supervisorSkillsService.createGlobalBadge(badge)
        supervisorSkillsService.assignSkillToGlobalBadge(projectId: projId, badgeId: badge.badgeId, skillId: proj1_skills.get(0).skillId)
        badge.enabled = "true"
        supervisorSkillsService.createGlobalBadge(badge)

        when:
        def summary = skillsService.getBadgeSummary(userId, proj1.projectId, globalBadgeId, -1, true)
        then:
        summary.badge == "Badge 1"
        summary.badgeId == globalBadgeId
        summary.global
        !summary.gem
        !summary.startDate
        !summary.endDate
        summary.numTotalSkills == 1
        summary.numSkillsAchieved == 0
        summary.iconClass == "fa fa-seleted-icon"
        summary.skills.size() == 1
        summary.skills.get(0).skillId == proj1_skills.get(0).skillId
        summary.skills.get(0).skill == proj1_skills.get(0).name
        summary.skills.get(0).pointIncrement == proj1_skills.get(0).pointIncrement
        summary.skills.get(0).pointIncrementInterval == proj1_skills.get(0).pointIncrementInterval
        summary.skills.get(0).totalPoints == proj1_skills.get(0).numPerformToCompletion * proj1_skills.get(0).pointIncrement
        summary.skills.get(0).points == 0
        summary.skills.get(0).todaysPoints == 0
        !summary.skills.get(0).crossProject
        !summary.skills.get(0).description
        !summary.dependencyInfo
    }

    def "single badge summary - achieved skill"() {
        String userId = "user1"

        def proj1 = SkillsFactory.createProject(1)
        def proj1_subj = SkillsFactory.createSubject(1, 1)
        List<Map> proj1_skills = SkillsFactory.createSkills(3, 1, 1)
        proj1_skills.each{
            it.pointIncrement = 40
        }

        skillsService.createProject(proj1)
        skillsService.createSubject(proj1_subj)
        skillsService.createSkills(proj1_skills)

        Map badge = [badgeId: globalBadgeId, name: 'Badge 1', description: 'This is a first badge', iconClass: "fa fa-seleted-icon",]
        supervisorSkillsService.createGlobalBadge(badge)

        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: globalBadgeId, skillId: proj1_skills.get(0).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: globalBadgeId, skillId: proj1_skills.get(1).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: globalBadgeId, skillId: proj1_skills.get(2).skillId])
        badge.enabled = "true"
        supervisorSkillsService.createGlobalBadge(badge)

        skillsService.addSkill([projectId: proj1.projectId, skillId: proj1_skills.get(0).skillId], userId, new Date())

        when:
        def summary = skillsService.getBadgeSummary(userId, proj1.projectId, globalBadgeId, -1, true)
        then:
        summary.badge == "Badge 1"
        summary.badgeId == globalBadgeId
        summary.global
        !summary.gem
        !summary.startDate
        !summary.endDate
        summary.numTotalSkills == 3
        summary.numSkillsAchieved == 1
        summary.iconClass == "fa fa-seleted-icon"
        summary.skills.size() == 3
        def skill1 = summary.skills.find { it.skillId == proj1_skills.get(0).skillId }
        skill1.totalPoints == 40
        skill1.todaysPoints == 40
        skill1.points == 40
        !summary.dependencyInfo

        def skill2 = summary.skills.find { it.skillId == proj1_skills.get(1).skillId }
        skill2.todaysPoints == 0
        skill2.points == 0

        def skill3 = summary.skills.find { it.skillId == proj1_skills.get(2).skillId }
        skill3.todaysPoints == 0
        skill3.points == 0
    }

    def "single badge summary - with dependency info"() {
        String userId = "user1"

        def proj1 = SkillsFactory.createProject(1)
        def proj1_subj = SkillsFactory.createSubject(1, 1)
        List<Map> proj1_skills = SkillsFactory.createSkills(4, 1, 1)
        proj1_skills.each{
            it.pointIncrement = 40
        }

        skillsService.createProject(proj1)
        skillsService.createSubject(proj1_subj)
        skillsService.createSkills(proj1_skills)

//        String badge1 = "badge1"
//        skillsService.addBadge([projectId: proj1.projectId, badgeId: badge1, name: 'Badge 1', description: 'This is a first badge', iconClass: "fa fa-seleted-icon",])
//        skillsService.assignSkillToBadge([projectId: proj1.projectId, badgeId: badge1, skillId: proj1_skills.get(0).skillId])
//        skillsService.assignSkillToBadge([projectId: proj1.projectId, badgeId: badge1, skillId: proj1_skills.get(1).skillId])


        Map badge = [badgeId: globalBadgeId, name: 'Badge 1', description: 'This is a first badge', iconClass: "fa fa-seleted-icon",]
        supervisorSkillsService.createGlobalBadge(badge)

        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: globalBadgeId, skillId: proj1_skills.get(0).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: globalBadgeId, skillId: proj1_skills.get(1).skillId])
        badge.enabled = "true"
        supervisorSkillsService.createGlobalBadge(badge)

        skillsService.assignDependency([projectId: proj1.projectId, skillId: proj1_skills.get(1).skillId, dependentSkillId: proj1_skills.get(2).skillId])
        skillsService.assignDependency([projectId: proj1.projectId, skillId: proj1_skills.get(1).skillId, dependentSkillId: proj1_skills.get(3).skillId])

        skillsService.addSkill([projectId: proj1.projectId, skillId: proj1_skills.get(0).skillId], userId, new Date())

        when:
        def summary = skillsService.getBadgeSummary(userId, proj1.projectId, globalBadgeId, -1, true)
        then:
        summary.badge == "Badge 1"
        summary.badgeId == globalBadgeId
        summary.global

        !summary.skills.find { it.skillId == proj1_skills.get(0).skillId }.dependencyInfo

        def skill1 = summary.skills.find { it.skillId == proj1_skills.get(1).skillId }
        skill1.dependencyInfo.numDirectDependents == 2
        !skill1.dependencyInfo.achieved
    }

    def "single badge summary - with achieved dependency info"() {
        String userId = "user1"

        def proj1 = SkillsFactory.createProject(1)
        def proj1_subj = SkillsFactory.createSubject(1, 1)
        List<Map> proj1_skills = SkillsFactory.createSkills(4, 1, 1)
        proj1_skills.each{
            it.pointIncrement=25
        }

        skillsService.createProject(proj1)
        skillsService.createSubject(proj1_subj)
        skillsService.createSkills(proj1_skills)

        String badge1Id = "badge1"
        def badge = [projectId: proj1.projectId, badgeId: badge1Id, name: 'Badge 1', description: 'This is a first badge', iconClass: "fa fa-seleted-icon",]
        skillsService.addBadge(badge)
        skillsService.assignSkillToBadge([projectId: proj1.projectId, badgeId: badge1Id, skillId: proj1_skills.get(0).skillId])
        skillsService.assignSkillToBadge([projectId: proj1.projectId, badgeId: badge1Id, skillId: proj1_skills.get(1).skillId])

        badge.enabled = "true"
        skillsService.addBadge(badge)

        skillsService.assignDependency([projectId: proj1.projectId, skillId: proj1_skills.get(1).skillId, dependentSkillId: proj1_skills.get(2).skillId])
        skillsService.assignDependency([projectId: proj1.projectId, skillId: proj1_skills.get(1).skillId, dependentSkillId: proj1_skills.get(3).skillId])

        skillsService.addSkill([projectId: proj1.projectId, skillId: proj1_skills.get(2).skillId], userId, new Date())
        skillsService.addSkill([projectId: proj1.projectId, skillId: proj1_skills.get(3).skillId], userId, new Date())

        when:
        def summary = skillsService.getBadgeSummary(userId, proj1.projectId, badge1Id)
        then:
        summary.badge == "Badge 1"
        summary.badgeId == "badge1"

        !summary.skills.find { it.skillId == proj1_skills.get(0).skillId }.dependencyInfo

        def skill1 = summary.skills.find { it.skillId == proj1_skills.get(1).skillId }
        skill1.dependencyInfo.numDirectDependents == 2
        skill1.dependencyInfo.achieved
    }

    def "single badge summary with only level dependencies should not appear completed due to subject level achievements" () {
        //steps
        //create 2 projects
        //create 2 subjects in one project
        //make sure user has achieved level 1 in project 1 and level 2 in project 1 subject 1
        //make sure user has achieved level 2 in project 2
        //create global badge which requires project 1 level 2 and project 2 level 2

        def proj1 = SkillsFactory.createProject(1)
        def proj1_subj = SkillsFactory.createSubject(1, 1)
        List<Map> proj1_skills = SkillsFactory.createSkills(4, 1, 1)
        proj1_skills.each{
            it.pointIncrement=25
        }

        def proj1_subj2 = SkillsFactory.createSubject(1, 2)
        def proj1_subj2_skills = SkillsFactory.createSkills(4, 1, 2)
        proj1_subj2_skills.each {
            it.pointIncrement = 100
        }


        def proj2 = SkillsFactory.createProject(2)
        def proj2_subj1 = SkillsFactory.createSubject(2, 1)
        List<Map> proj2_skills = SkillsFactory.createSkills(5, 2)
        proj2_skills.each {
            it.pointIncrement = 25
        }

        skillsService.createProject(proj1)
        skillsService.createSubject(proj1_subj)
        skillsService.createSkills(proj1_skills)
        skillsService.createSubject(proj1_subj2)
        skillsService.createSkills(proj1_subj2_skills)
        skillsService.createProject(proj2)
        skillsService.createSubject(proj2_subj1)
        skillsService.createSkills(proj2_skills)

        Map badge = [badgeId: globalBadgeId, name: 'Badge 1', description: 'This is a first badge', iconClass: "fa fa-seleted-icon",]
        supervisorSkillsService.createGlobalBadge(badge)
        supervisorSkillsService.assignProjectLevelToGlobalBadge(projectId: proj1.projectId, badgeId: globalBadgeId, level: "2")
        supervisorSkillsService.assignProjectLevelToGlobalBadge(projectId: proj2.projectId, badgeId: globalBadgeId, level: "2")
        badge.enabled = true
        supervisorSkillsService.createGlobalBadge(badge)

        String user = getRandomUsers(1)[0]
        skillsService.addSkill([projectId: proj1.projectId, skillId: proj1_skills.get(0).skillId], user, new Date())
        skillsService.addSkill([projectId: proj1.projectId, skillId: proj1_skills.get(1).skillId], user, new Date())
        skillsService.addSkill([projectId: proj2.projectId, skillId: proj2_skills.get(0).skillId], user, new Date())
        skillsService.addSkill([projectId: proj2.projectId, skillId: proj2_skills.get(1).skillId], user, new Date())

        when:
        def proj1level = skillsService.getUserLevel(proj1.projectId, user)
        def proj2level = skillsService.getUserLevel(proj2.projectId, user)

        def summary = skillsService.getBadgeSummary(user, proj1.projectId, globalBadgeId, -1, true)

        then:
        proj1level == 1
        proj2level == 2
        summary.projectLevelsAndSkillsSummaries.find {it.projectId == proj1.projectId}.projectLevel.requiredLevel == 2
        summary.projectLevelsAndSkillsSummaries.find {it.projectId == proj1.projectId}.projectLevel.achievedLevel == proj1level
        summary.projectLevelsAndSkillsSummaries.find {it.projectId == proj2.projectId}.projectLevel.requiredLevel == 2
        summary.projectLevelsAndSkillsSummaries.find {it.projectId == proj2.projectId}.projectLevel.achievedLevel == proj2level
    }

    def "project summary should return achieved badges summary"(){
        String userId = "user1"

        def proj1 = SkillsFactory.createProject(1)
        def proj1_subj = SkillsFactory.createSubject(1, 1)
        List<Map> proj1_skills = SkillsFactory.createSkills(4, 1, 1)

        skillsService.createProject(proj1)
        skillsService.createSubject(proj1_subj)
        skillsService.createSkills(proj1_skills)

        Map badge = [badgeId: globalBadgeId, name: 'Badge 1', description: 'This is a first badge', iconClass: "fa fa-seleted-icon",]
        supervisorSkillsService.createGlobalBadge(badge)

        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: globalBadgeId, skillId: proj1_skills.get(0).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: globalBadgeId, skillId: proj1_skills.get(1).skillId])

        badge.enabled  = 'true'
        supervisorSkillsService.updateGlobalBadge(badge, badge.badgeId)

        when:
        def summary = skillsService.getSkillSummary(userId, proj1.projectId)
        then:
        summary.badges.numBadgesCompleted == 0
        summary.badges.enabled
    }

    def "project summary should return achieved badges summary - badges completed"(){
        String userId = "user1"

        def proj1 = SkillsFactory.createProject(1)
        def proj1_subj = SkillsFactory.createSubject(1, 1)
        List<Map> proj1_skills = SkillsFactory.createSkills(4, 1, 1)
        proj1_skills.each{
            it.pointIncrement = 25
        }

        skillsService.createProject(proj1)
        skillsService.createSubject(proj1_subj)
        skillsService.createSkills(proj1_skills)

        Map badge = [badgeId: globalBadgeId, name: 'Badge 1', description: 'This is a first badge', iconClass: "fa fa-seleted-icon",]
        supervisorSkillsService.createGlobalBadge(badge)

        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: globalBadgeId, skillId: proj1_skills.get(0).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: globalBadgeId, skillId: proj1_skills.get(1).skillId])
        badge.enabled  = 'true'
        supervisorSkillsService.updateGlobalBadge(badge, badge.badgeId)

        skillsService.addSkill([projectId: proj1.projectId, skillId: proj1_skills.get(0).skillId], userId, new Date())
        def res = skillsService.addSkill([projectId: proj1.projectId, skillId: proj1_skills.get(1).skillId], userId, new Date())

        when:
        def summary = skillsService.getSkillSummary(userId, proj1.projectId)
        then:
        summary.badges.numBadgesCompleted == 1
        summary.badges.enabled
    }

    def "badges summaries are returned sorted by displayOrder"() {
        String userId = "user1"

        def proj1 = SkillsFactory.createProject(1)
        def proj1_subj = SkillsFactory.createSubject(1, 1)
        List<Map> proj1_skills = SkillsFactory.createSkills(5, 1, 1, 20)

        skillsService.createProject(proj1)
        skillsService.createSubject(proj1_subj)
        skillsService.createSkills(proj1_skills)

        List<String> badgeIds = (1..3).collect({ "${globalBadgeId}${it}".toString()})
        badgeIds.each {
            Map badge = [badgeId: it, name: it, description: "This is ${it}".toString(), iconClass: "fa fa-${it}".toString(),]
            supervisorSkillsService.createGlobalBadge(badge)
        }

        supervisorSkillsService.changeGlobalBadgeDisplayOrder([badgeId: badgeIds[0]], 1)
        supervisorSkillsService.changeGlobalBadgeDisplayOrder([badgeId: badgeIds[0]], 2)
        supervisorSkillsService.changeGlobalBadgeDisplayOrder([badgeId: badgeIds[2]], 0)

        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: badgeIds.get(0), skillId: proj1_skills.get(0).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: badgeIds.get(0), skillId: proj1_skills.get(1).skillId])
        skillsService.addSkill([projectId: proj1.projectId, skillId: proj1_skills.get(0).skillId], userId, new Date())

        supervisorSkillsService.assignProjectLevelToGlobalBadge(projectId: proj1.projectId, badgeId: badgeIds.get(1), level: "3")

        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: badgeIds.get(2), skillId: proj1_skills.get(0).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: badgeIds.get(2), skillId: proj1_skills.get(1).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: badgeIds.get(2), skillId: proj1_skills.get(2).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: badgeIds.get(2), skillId: proj1_skills.get(3).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: badgeIds.get(2), skillId: proj1_skills.get(4).skillId])

        when:
        def summaries = skillsService.getBadgesSummary(userId, proj1.projectId)
        then:
        summaries.size() == 3
        summaries.get(2).badge == badgeIds[0]
        summaries.get(2).badgeId == badgeIds[0]
        summaries.get(2).iconClass == "fa fa-${badgeIds[0]}"
        summaries.get(2).numSkillsAchieved == 1
        summaries.get(2).numTotalSkills == 2

        summaries.get(1).badge == badgeIds[1]
        summaries.get(1).badgeId == badgeIds[1]
        summaries.get(1).iconClass == "fa fa-${badgeIds[1]}"
        summaries.get(1).numSkillsAchieved == 0
        summaries.get(1).numTotalSkills == 1

        summaries.get(0).badge == badgeIds[2]
        summaries.get(0).badgeId == badgeIds[2]
        summaries.get(0).iconClass == "fa fa-${badgeIds[2]}"
        summaries.get(0).numSkillsAchieved == 1
        summaries.get(0).numTotalSkills == 5

        cleanup:
        badgeIds.each {
            deleteGlobalBadgeIfExists(it)
        }
    }

    def "badge skill summaries from within a different project"() {

        String userId = "user1"

        def proj1 = SkillsFactory.createProject(1)
        def proj2 = SkillsFactory.createProject(2)
        def proj1_subj = SkillsFactory.createSubject(1, 1)
        def proj2_subj = SkillsFactory.createSubject(2, 2)
        List<Map> proj1_skills = SkillsFactory.createSkills(2, 1, 1, 50)
        List<Map> proj2_skills = SkillsFactory.createSkills(2, 2, 2, 50)

        skillsService.createProject(proj1)
        skillsService.createProject(proj2)
        skillsService.createSubject(proj1_subj)
        skillsService.createSubject(proj2_subj)
        skillsService.createSkills(proj1_skills)
        skillsService.createSkills(proj2_skills)

        Map badge = [badgeId: "bid1", name: "global badge", description: "gbadge".toString(), iconClass: "fa fa-foo".toString(),]
        supervisorSkillsService.createGlobalBadge(badge)

        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj1.projectId, badgeId: "bid1", skillId: proj1_skills.get(0).skillId])
        supervisorSkillsService.assignSkillToGlobalBadge([projectId: proj2.projectId, badgeId: "bid1", skillId: proj2_skills.get(0).skillId])
        skillsService.addSkill([projectId: proj1.projectId, skillId: proj1_skills.get(0).skillId], userId, new Date())
        badge.enabled = "true"
        supervisorSkillsService.createGlobalBadge(badge)

        when:
        def summaries = skillsService.getBadgesSummary(userId, proj1.projectId)
        def skillSummary = skillsService.getCrossProjectSkillSummary(userId, proj1.projectId, proj2.projectId, proj2_skills.get(0).skillId)
        def globalBadge = skillsService.getBadgeSummary(userId, proj1.projectId, badge.badgeId, -1, true)

        then:
        summaries
        skillSummary
        List proj1Skills = globalBadge.skills.findAll { it.projectId == proj1.projectId }
        proj1Skills.size() == 1
        !proj1Skills.get(0).crossProject

        List proj2Skills = globalBadge.skills.findAll { it.projectId == proj2.projectId }
        proj2Skills.size() == 1
        proj2Skills.get(0).crossProject
    }

    def "sort skills alphabetically"() {
        String userId = "user1"

        def proj1 = SkillsFactory.createProject(1)
        def proj1_subj = SkillsFactory.createSubject(1, 1)
        List<Map> proj1_skills = SkillsFactory.createSkills(5, 1, 1)
        proj1_skills[0].name = "Dsome"
        proj1_skills[1].name = "Zsome"
        proj1_skills[2].name = "ksome"
        proj1_skills[3].name = "asome"
        proj1_skills[4].name = "lsome"

        skillsService.createProject(proj1)
        skillsService.createSubject(proj1_subj)
        skillsService.createSkills(proj1_skills)

        Map badge = [badgeId: globalBadgeId, name: 'Badge 1', description: 'This is a first badge', iconClass: "fa fa-seleted-icon",]
        supervisorSkillsService.createGlobalBadge(badge)
        proj1_skills.each {
            supervisorSkillsService.assignSkillToGlobalBadge(projectId: projId, badgeId: badge.badgeId, skillId: it.skillId)
        }
        badge.enabled = "true"
        supervisorSkillsService.createGlobalBadge(badge)

        when:
        def summary = skillsService.getBadgeSummary(userId, proj1.projectId, globalBadgeId, -1, true)
        then:
        summary.skills.collect { it.skill } == ["asome", "Dsome", "ksome", "lsome", "Zsome"]
    }

    private deleteGlobalBadgeIfExists(String badgeId) {
        try {
            if (supervisorSkillsService?.getGlobalBadge(badgeId)) {
                supervisorSkillsService.deleteGlobalBadge(badgeId)
            }
        } catch (SkillsClientException e) {
            log.error("Unabled to delete global badge with id [$badgeId]: ${e.message}")
        }
    }
}
