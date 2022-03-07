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
package skills.intTests.metrics.skills


import groovy.time.TimeCategory
import org.springframework.beans.factory.annotation.Autowired
import skills.intTests.utils.DefaultIntSpec
import skills.intTests.utils.SkillsFactory
import skills.metrics.builders.MetricsParams
import skills.storage.repos.UserAchievedLevelRepo
import skills.storage.repos.UserPointsRepo
import spock.lang.IgnoreRest

class SkillUsageNavigatorMetricsBuilderSpec extends DefaultIntSpec {

    String metricsId = "skillUsageNavigatorChartBuilder"

    def "no skills"() {
        def proj = SkillsFactory.createProject()

        skillsService.createProject(proj)
        skillsService.createSubject(SkillsFactory.createSubject())

        Map props = [:]

        when:
        def res = skillsService.getMetricsData(proj.projectId, metricsId, props)
        then:
        !res
    }

    def "one empty skill"() {
        def proj = SkillsFactory.createProject()
        List<Map> skills = SkillsFactory.createSkills(1)

        skillsService.createProject(proj)
        skillsService.createSubject(SkillsFactory.createSubject())
        skillsService.createSkills(skills)

        Map props = [:]

        when:
        def res = skillsService.getMetricsData(proj.projectId, metricsId, props)
        then:
        res.size() == 1
        res[0].skillId == 'skill1'
        res[0].skillName == 'Test Skill 1'
        res[0].numUserAchieved == 0
        res[0].numUsersInProgress == 0
        !res[0].lastReportedTimestamp
        !res[0].lastAchievedTimestamp
    }

    def "skills with usage and achievements"() {
        List<String> users = getRandomUsers(10)
        def proj = SkillsFactory.createProject()
        def subj = SkillsFactory.createSubject()
        List<Map> skills = SkillsFactory.createSkills(10)
        skills.each { it.pointIncrement = 100; it.numPerformToCompletion = 5 }

        skillsService.createProject(proj)
        skillsService.createSubject(subj)
        skillsService.createSkills(skills)

        List<Date> days

        use(TimeCategory) {
            days = (5..0).collect { int day -> day.days.ago }
            days.eachWithIndex { Date date, int index ->
                users.subList(0, index).each { String user ->
                    skills.subList(0, index).each { skill ->
                        skillsService.addSkill([projectId: proj.projectId, skillId: skill.skillId], user, date)
                    }
                }
            }
        }

        Map props = [:]
        props[MetricsParams.P_SKILL_ID] = skills[0].skillId

        when:
        def res = skillsService.getMetricsData(proj.projectId, metricsId, props)

        then:
        res.size() == 10
        def skill1 = res.find { it.skillId == 'skill1' }
        skill1.subjectId == subj.subjectId
        skill1.numUserAchieved == 1
        skill1.numUsersInProgress == 4
        new Date(skill1.lastReportedTimestamp) == days[5]
        new Date(skill1.lastAchievedTimestamp) == days[5]

        def skill2 = res.find { it.skillId == 'skill2' }
        skill2.numUserAchieved == 0
        skill2.numUsersInProgress == 5
        new Date(skill2.lastReportedTimestamp) == days[5]
        !skill2.lastAchievedTimestamp

        def skill3 = res.find { it.skillId == 'skill3' }
        skill3.numUserAchieved == 0
        skill3.numUsersInProgress == 5
        new Date(skill3.lastReportedTimestamp) == days[5]
        !skill3.lastAchievedTimestamp

        def skill4 = res.find { it.skillId == 'skill4' }
        skill4.numUserAchieved == 0
        skill4.numUsersInProgress == 5
        new Date(skill4.lastReportedTimestamp) == days[5]
        !skill4.lastAchievedTimestamp

        def skill5 = res.find { it.skillId == 'skill5' }
        skill5.numUserAchieved == 0
        skill5.numUsersInProgress == 5
        new Date(skill5.lastReportedTimestamp) == days[5]
        !skill5.lastAchievedTimestamp

        def skill6 = res.find { it.skillId == 'skill6' }
        skill6.numUserAchieved == 0
        skill6.numUsersInProgress == 0
        !skill6.lastReportedTimestamp
        !skill6.lastAchievedTimestamp
    }

    def "last reported is later than last achieved"() {
        List<String> users = getRandomUsers(10)
        def proj = SkillsFactory.createProject()
        List<Map> skills = SkillsFactory.createSkills(5)
        skills.each { it.pointIncrement = 100; it.numPerformToCompletion = 2 }

        skillsService.createProject(proj)
        skillsService.createSubject(SkillsFactory.createSubject())
        skillsService.createSkills(skills)

        List<Date> days
        use(TimeCategory) {
            days = (5..0).collect { int day -> day.days.ago }

            // achieved
            skillsService.addSkill([projectId: proj.projectId, skillId: skills[0].skillId], users[0], days[0])
            skillsService.addSkill([projectId: proj.projectId, skillId: skills[0].skillId], users[0], days[1])

            // in progress
            skillsService.addSkill([projectId: proj.projectId, skillId: skills[0].skillId], users[1], days[3])
        }

        Map props = [:]
        props[MetricsParams.P_SKILL_ID] = skills[0].skillId

        when:
        def res = skillsService.getMetricsData(proj.projectId, metricsId, props).sort { it.skillId }

        then:
        res.size() == 5
        res[0].skillId == 'skill1'
        res[0].numUserAchieved == 1
        res[0].numUsersInProgress == 1
        new Date(res[0].lastReportedTimestamp) == days[3]
        new Date(res[0].lastAchievedTimestamp) == days[1]
    }

    def "skills with usage and achievements - include catalog skills"() {
        List<String> users = getRandomUsers(10)
        def proj = SkillsFactory.createProject()
        def proj2 = SkillsFactory.createProject(2)
        def subj = SkillsFactory.createSubject()
        def subj2 = SkillsFactory.createSubject(2)
        List<Map> skills = SkillsFactory.createSkills(5)
        skills.each { it.pointIncrement = 100; it.numPerformToCompletion = 5 }

        List<Map> p2Skills = SkillsFactory.createSkills(5, 2)
        (0..4).each {
            p2Skills.get(it).pointIncrement = 100
            p2Skills.get(it).numPerformToCompletion = 5
            p2Skills.get(it).skillId = "p2skill_"+it
            p2Skills.get(it).name = "P2Skill "+it
        }

        skillsService.createProject(proj)
        skillsService.createSubject(subj)
        skillsService.createSkills(skills)
        skillsService.createProject(proj2)
        skillsService.createSubject(subj2)
        skillsService.createSkills(p2Skills)

        skillsService.exportSkillToCatalog(proj2.projectId, p2Skills[0].skillId)
        skillsService.exportSkillToCatalog(proj2.projectId, p2Skills[1].skillId)
        skillsService.exportSkillToCatalog(proj2.projectId, p2Skills[2].skillId)
        skillsService.exportSkillToCatalog(proj2.projectId, p2Skills[3].skillId)
        skillsService.exportSkillToCatalog(proj2.projectId, p2Skills[4].skillId)
        skillsService.importSkillFromCatalog(proj.projectId, subj.subjectId, proj2.projectId, p2Skills[0].skillId)
        skillsService.importSkillFromCatalog(proj.projectId, subj.subjectId, proj2.projectId, p2Skills[1].skillId)
        skillsService.importSkillFromCatalog(proj.projectId, subj.subjectId, proj2.projectId, p2Skills[2].skillId)
        skillsService.importSkillFromCatalog(proj.projectId, subj.subjectId, proj2.projectId, p2Skills[3].skillId)
        skillsService.importSkillFromCatalog(proj.projectId, subj.subjectId, proj2.projectId, p2Skills[4].skillId)
        skillsService.finalizeSkillsImportFromCatalog(proj.projectId, true)

        def allSkills = []
        allSkills.addAll(skills)
        allSkills.addAll(p2Skills)

        List<Date> days

        use(TimeCategory) {
            days = (5..0).collect { int day -> day.days.ago }
            days.eachWithIndex { Date date, int index ->
                users.subList(0, index).each { String user ->
                    allSkills.subList(0, index).each { skill ->
                        skillsService.addSkill([projectId: skill.projectId, skillId: skill.skillId], user, date)
                    }
                }
            }
        }

        waitForAsyncTasksCompletion.waitForAllScheduleTasks()

        Map props = [:]
        props[MetricsParams.P_SKILL_ID] = skills[0].skillId

        when:
        def res = skillsService.getMetricsData(proj.projectId, metricsId, props)

        then:
        res.size() == 10
        def skill1 = res.find { it.skillId == 'skill1' }
        skill1.subjectId == subj.subjectId
        skill1.numUserAchieved == 1
        skill1.numUsersInProgress == 4
        new Date(skill1.lastReportedTimestamp) == days[5]
        new Date(skill1.lastAchievedTimestamp) == days[5]

        def skill2 = res.find { it.skillId == 'skill2' }
        skill2.numUserAchieved == 0
        skill2.numUsersInProgress == 5
        new Date(skill2.lastReportedTimestamp) == days[5]
        !skill2.lastAchievedTimestamp

        def skill3 = res.find { it.skillId == 'skill3' }
        skill3.numUserAchieved == 0
        skill3.numUsersInProgress == 5
        new Date(skill3.lastReportedTimestamp) == days[5]
        !skill3.lastAchievedTimestamp

        def skill4 = res.find { it.skillId == 'skill4' }
        skill4.numUserAchieved == 0
        skill4.numUsersInProgress == 5
        new Date(skill4.lastReportedTimestamp) == days[5]
        !skill4.lastAchievedTimestamp

        def skill5 = res.find { it.skillId == 'skill5' }
        skill5.numUserAchieved == 0
        skill5.numUsersInProgress == 5
        new Date(skill5.lastReportedTimestamp) == days[5]
        !skill5.lastAchievedTimestamp

        def skill6 = res.find { it.skillId == 'p2skill_0' }
        skill6.numUserAchieved == 0
        skill6.numUsersInProgress == 0
        !skill6.lastReportedTimestamp
        !skill6.lastAchievedTimestamp
    }

    def "skills with usage and achievements - only catalog skills"() {
        List<String> users = getRandomUsers(10)
        def proj = SkillsFactory.createProject()
        def proj2 = SkillsFactory.createProject(2)
        def subj = SkillsFactory.createSubject()
        def subj2 = SkillsFactory.createSubject(2)

        List<Map> p2Skills = SkillsFactory.createSkills(10, 2)
        p2Skills.each {
            it.pointIncrement = 100
            it.numPerformToCompletion = 5
        }

        skillsService.createProject(proj)
        skillsService.createSubject(subj)
        skillsService.createProject(proj2)
        skillsService.createSubject(subj2)
        skillsService.createSkills(p2Skills)

        p2Skills.each {
            skillsService.exportSkillToCatalog(proj2.projectId, it.skillId)
            skillsService.importSkillFromCatalog(proj.projectId, subj.subjectId, proj2.projectId, it.skillId)
        }
        skillsService.finalizeSkillsImportFromCatalog(proj.projectId, true)


        List<Date> days

        use(TimeCategory) {
            days = (5..0).collect { int day -> day.days.ago }
            days.eachWithIndex { Date date, int index ->
                users.subList(0, index).each { String user ->
                    p2Skills.subList(0, index).each { skill ->
                        skillsService.addSkill([projectId: skill.projectId, skillId: skill.skillId], user, date)
                    }
                }
            }
        }

        waitForAsyncTasksCompletion.waitForAllScheduleTasks()

        Map props = [:]
        props[MetricsParams.P_SKILL_ID] = p2Skills[0].skillId

        when:
        def res = skillsService.getMetricsData(proj.projectId, metricsId, props)

        then:
        res.size() == 10
        def skill1 = res.find { it.skillId == 'skill1' }
        skill1.subjectId == subj.subjectId
        skill1.numUserAchieved == 1
        skill1.numUsersInProgress == 4
        new Date(skill1.lastReportedTimestamp) == days[5]
        new Date(skill1.lastAchievedTimestamp) == days[5]

        def skill2 = res.find { it.skillId == 'skill2' }
        skill2.numUserAchieved == 0
        skill2.numUsersInProgress == 5
        new Date(skill2.lastReportedTimestamp) == days[5]
        !skill2.lastAchievedTimestamp

        def skill3 = res.find { it.skillId == 'skill3' }
        skill3.numUserAchieved == 0
        skill3.numUsersInProgress == 5
        new Date(skill3.lastReportedTimestamp) == days[5]
        !skill3.lastAchievedTimestamp

        def skill4 = res.find { it.skillId == 'skill4' }
        skill4.numUserAchieved == 0
        skill4.numUsersInProgress == 5
        new Date(skill4.lastReportedTimestamp) == days[5]
        !skill4.lastAchievedTimestamp

        def skill5 = res.find { it.skillId == 'skill5' }
        skill5.numUserAchieved == 0
        skill5.numUsersInProgress == 5
        new Date(skill5.lastReportedTimestamp) == days[5]
        !skill5.lastAchievedTimestamp

        def skill6 = res.find { it.skillId == 'skill6' }
        skill6.numUserAchieved == 0
        skill6.numUsersInProgress == 0
        !skill6.lastReportedTimestamp
        !skill6.lastAchievedTimestamp
    }
}
