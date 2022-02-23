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
package skills.intTests.root

import groovy.json.JsonOutput
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.http.HttpStatus
import skills.intTests.utils.DefaultIntSpec
import skills.intTests.utils.SkillsClientException
import skills.intTests.utils.SkillsFactory
import skills.intTests.utils.SkillsService
import spock.lang.IgnoreIf
import spock.lang.IgnoreRest
import spock.lang.Requires

class RootAccessSpec extends DefaultIntSpec {

    String ultimateRoot = 'jh@dojo.com'
    SkillsService rootSkillsService
    String nonRootUserId = 'foo@bar.com'
    SkillsService nonRootSkillsService
    String secondRoot = "bob@email.com"
    SkillsService secondRootSkillService

    def setup() {
        rootSkillsService = createService(ultimateRoot, 'aaaaaaaa')
        secondRootSkillService = createService(secondRoot, 'bbbbbbbbbbbbbbb')
        nonRootSkillsService = createService(nonRootUserId)

        if (!rootSkillsService.isRoot()) {
            rootSkillsService.grantRoot()
        }
        if(!secondRootSkillService.isRoot()) {
            rootSkillsService.grantRootRole(secondRoot)
            assert secondRootSkillService.isRoot()
        }
        if (nonRootSkillsService.isRoot()) {
            rootSkillsService.removeRootRole(nonRootUserId)
            assert !nonRootSkillsService.isRoot()
        }
    }

    def 'prevent a user being created with root privileges if a root account already exists'() {
        setup:
        String userId = RandomStringUtils.randomAlphanumeric(14)
        Map<String, String> userInfo = [
                firstName: 'A',
                lastName : 'B',
                email    : userId,
                password : 'aaaaaaaa',
        ]

        when:
        nonRootSkillsService.createRootAccount(userInfo)

        then:
        SkillsClientException exception = thrown()
        exception.httpStatus == HttpStatus.BAD_REQUEST
        exception.message.contains('root user already exists')
    }

    def 'prevent granting root user privileges if a root account already exists'() {
        when:
        nonRootSkillsService.grantRoot()

        then:
        SkillsClientException exception = thrown()
        exception.httpStatus == HttpStatus.BAD_REQUEST
        exception.message.contains('root user already exists')
    }

    def 'verify retrieving root users as a root user'() {
        when:
        def result = rootSkillsService.getRootUsers()

        then:
        result.size() >= 1
        result.find {it.userId == 'jh@dojo.com'}
        result.each {
            assert it.roleName == 'ROLE_SUPER_DUPER_USER'
            true
        }
    }

    def 'verify non-root users cannot retrieve root user information'() {
        when:
        nonRootSkillsService.getRootUsers()

        then:
        SkillsClientException exception = thrown()
        exception.httpStatus == HttpStatus.FORBIDDEN
    }

    def 'verify retrieving non-root users as a root user'() {
        when:
        def result = rootSkillsService.getNonRootUsers("skills")

        then:
        result.size() >= 1
        !result.find {it.userId == 'jh@dojo.com'}
        result.find {it.userId == 'skills@skills.org'}
    }

    def 'verify non-root users cannot retrieve user information about non-root users'() {
        when:
        nonRootSkillsService.getNonRootUsers("skills")

        then:
        SkillsClientException exception = thrown()
        exception.httpStatus == HttpStatus.FORBIDDEN
    }

    def 'verify isRoot with root user'() {
        expect:
        rootSkillsService.isRoot() == true
    }

    def 'verify isRoot with non-root user'() {
        expect:
        nonRootSkillsService.isRoot() == false
    }

    def 'verify root users can add other root users'() {
        setup:
        def originalRootUsers = rootSkillsService.getRootUsers()
        assert !originalRootUsers.find {it.userId == nonRootUserId}

        when:
        rootSkillsService.addRootRole(nonRootUserId)

        then:
        def rootUsers = rootSkillsService.getRootUsers()
        rootUsers.size() == originalRootUsers.size() + 1
        rootUsers.find {it.userId == nonRootUserId}

        cleanup:
        rootSkillsService.removeRootRole(nonRootUserId)
    }

    @IgnoreIf({env["SPRING_PROFILES_ACTIVE"] == "pki" })
    def 'verify the server sends a failure when a root user tries to add root privileges to a user that does not exist'() {
        when:
        rootSkillsService.addRootRole(RandomStringUtils.randomAlphanumeric(14))

        then:
        SkillsClientException exception = thrown()
        exception.httpStatus == HttpStatus.BAD_REQUEST
        exception.message.contains('does not exist')
    }


    @Requires({env["SPRING_PROFILES_ACTIVE"] == "pki" })
    def 'verify the server sends a failure when a root user tries to add root privileges to a user that does not exist - 2-way ssl'() {
        when:
        rootSkillsService.addRootRole("doesNotExist")

        then:
        // because 2-way ssl uses a user-info-service, a non-existant user can result in different error messages than
        // the form based version
        thrown(Exception)
    }

    def 'verify non-root users cannot add root users'() {
        when:
        nonRootSkillsService.addRootRole("skills@skills.org")

        then:
        SkillsClientException exception = thrown()
        exception.httpStatus == HttpStatus.FORBIDDEN
    }

    def 'verify the server will prevent all root users from being removed'() {
        setup:
        Collection rootUsers = rootSkillsService.getRootUsers()
        rootUsers.removeAll {it.userId == ultimateRoot}

        when:
        rootUsers.each {
            rootSkillsService.removeRootRole(it.userId)
        }
        // only one root user should remain
        rootSkillsService.removeRootRole(ultimateRoot)

        then:
        SkillsClientException exception = thrown()
        exception.httpStatus == HttpStatus.BAD_REQUEST

        cleanup:
        rootUsers.each {
            rootSkillsService.addRootRole(it.userId)
        }
    }

    def 'verify non-root users cannot remove root user privileges'() {
        when:
        nonRootSkillsService.removeRootRole(ultimateRoot)

        then:
        SkillsClientException exception = thrown()
        exception.httpStatus == HttpStatus.FORBIDDEN
    }

    def 'verify that inception project was created and assigned ot the root user'() {
        when:
        def inception = rootSkillsService.getProject("Inception")

        then:
        inception.projectId == 'Inception'
    }

    def 'verify that inception project is disassociated when user loses root privileges'() {
        setup:
        def originalRootUsers = rootSkillsService.getRootUsers()
        assert !originalRootUsers.find {it.userId == nonRootUserId}

        when:
        rootSkillsService.addRootRole(nonRootUserId)
        def inception = nonRootSkillsService.getProject("Inception")
        rootSkillsService.removeRootRole(nonRootUserId)
        def inception1 = nonRootSkillsService.getProject("Inception")

        then:
        SkillsClientException exception = thrown()
        inception
        !inception1
        exception.httpStatus == org.springframework.http.HttpStatus.FORBIDDEN
    }

    def 'insert new global setting'() {
        Map settingRequest = [
                settingGroup : "public_header",
                setting : "classification",
                value : "MY_CLASS"
        ]
        when :
        rootSkillsService.addOrUpdateGlobalSetting("classification", settingRequest)
        def settingsResult = rootSkillsService.getPublicSettings("public_header")

        then:
        settingsResult
        settingsResult.size() == 1
        settingsResult[0].setting == "classification"
        settingsResult[0].value == "MY_CLASS"
    }


    def 'update existing global setting'() {
        Map settingRequest = [
                settingGroup : "public_header",
                setting : "classification",
                value : "MY_CLASS1"
        ]
        when :
        rootSkillsService.addOrUpdateGlobalSetting(settingRequest.setting, settingRequest)
        def settingsResult1 = rootSkillsService.getPublicSettings("public_header")

        settingRequest.value = "MY_CLASS2"
        rootSkillsService.addOrUpdateGlobalSetting(settingRequest.setting, settingRequest)
        def settingsResult2 = rootSkillsService.getPublicSettings("public_header")

        then:
        settingsResult1
        settingsResult1.size() == 1
        settingsResult1[0].setting == "classification"
        settingsResult1[0].value == "MY_CLASS1"

        settingsResult2
        settingsResult2.size() == 1
        settingsResult2[0].setting == "classification"
        settingsResult2[0].value == "MY_CLASS2"
    }

    def 'get projects for root returns zero by default'() {
        def proj = SkillsFactory.createProject(1)
        def proj2 = SkillsFactory.createProject(2)
        def proj3 = SkillsFactory.createProject(3)
        rootSkillsService.createProject(proj)
        rootSkillsService.createProject(proj2)
        rootSkillsService.createProject(proj3)

        when:
        def projects = rootSkillsService.getProjects()

        then:
        !projects
    }

    def 'get projects for root returns only pinned projects'() {
        def proj = SkillsFactory.createProject(1)
        def proj2 = SkillsFactory.createProject(2)
        def proj3 = SkillsFactory.createProject(3)
        rootSkillsService.createProject(proj)
        rootSkillsService.createProject(proj2)
        rootSkillsService.createProject(proj3)
        rootSkillsService.pinProject(proj.projectId)

        when:
        def projects = rootSkillsService.getProjects()

        then:
        projects.size() == 1
        projects.find { it.projectId == proj.projectId }
    }

    def "pinned projects are unique per root user"() {
        def proj = SkillsFactory.createProject(1)
        def proj2 = SkillsFactory.createProject(2)
        def proj3 = SkillsFactory.createProject(3)
        def proj4 = SkillsFactory.createProject(4)
        nonRootSkillsService.createProject(proj)
        nonRootSkillsService.createProject(proj2)
        nonRootSkillsService.createProject(proj3)
        nonRootSkillsService.createProject(proj4)

        rootSkillsService.pinProject(proj.projectId)
        rootSkillsService.pinProject(proj2.projectId)

        when:
        def prePinProjects = secondRootSkillService.getProjects()
        secondRootSkillService.pinProject(proj3.projectId)
        secondRootSkillService.pinProject(proj4.projectId)
        def postPinProjects = secondRootSkillService.getProjects()
        def otherRootUserProjects = rootSkillsService.getProjects()

        then:
        !prePinProjects
        postPinProjects.size() == 2
        postPinProjects.find { it.projectId == proj3.projectId}
        postPinProjects.find { it.projectId == proj4.projectId}
        otherRootUserProjects.size() == 2
        otherRootUserProjects.find { it.projectId == proj.projectId }
        otherRootUserProjects.find { it.projectId == proj2.projectId }
    }

    def 'able to search all projects'() {
        def proj = SkillsFactory.createProject(1)
        def proj2 = SkillsFactory.createProject(2)
        def proj3 = SkillsFactory.createProject(3)
        rootSkillsService.createProject(proj)
        rootSkillsService.createProject(proj2)
        rootSkillsService.createProject(proj3)

        when:
        def projects = rootSkillsService.searchProjects("3")

        then:
        projects.size() == 1
        projects.find { it.projectId == proj3.projectId }
    }

    def 'only root users can search all projects'() {
        def proj = SkillsFactory.createProject(1)
        def proj2 = SkillsFactory.createProject(2)
        def proj3 = SkillsFactory.createProject(3)
        nonRootSkillsService.createProject(proj)
        nonRootSkillsService.createProject(proj2)
        nonRootSkillsService.createProject(proj3)

        when:
        nonRootSkillsService.searchProjects("search")

        then:
        thrown(Exception)
    }

    def 'get all projects'() {
        def proj = SkillsFactory.createProject(1)
        def proj2 = SkillsFactory.createProject(2)
        def proj3 = SkillsFactory.createProject(3)
        rootSkillsService.createProject(proj)
        rootSkillsService.createProject(proj2)
        rootSkillsService.createProject(proj3)

        when:
        def projects = rootSkillsService.getAllProjects()

        then:
        projects.size() == 4
        projects.collect { it.projectId }.sort() == ["Inception", proj.projectId, proj2.projectId, proj3.projectId ]
    }

    def 'only root users can get all projects'() {
        def proj = SkillsFactory.createProject(1)
        def proj2 = SkillsFactory.createProject(2)
        def proj3 = SkillsFactory.createProject(3)
        nonRootSkillsService.createProject(proj)
        nonRootSkillsService.createProject(proj2)
        nonRootSkillsService.createProject(proj3)

        when:
        nonRootSkillsService.getAllProjects()

        then:
        thrown(Exception)
    }


    def 'only root users can pin projects'() {
        def proj = SkillsFactory.createProject(1)
        def proj2 = SkillsFactory.createProject(2)
        def proj3 = SkillsFactory.createProject(3)
        nonRootSkillsService.createProject(proj)
        nonRootSkillsService.createProject(proj2)
        nonRootSkillsService.createProject(proj3)

        when:
        nonRootSkillsService.pinProject(proj.projectId)

        then:
        thrown(Exception)
    }

    def 'edit id of pinned project'() {
        def proj = SkillsFactory.createProject(1)
        def proj2 = SkillsFactory.createProject(2)
        def proj3 = SkillsFactory.createProject(3)
        rootSkillsService.createProject(proj)
        rootSkillsService.createProject(proj2)
        rootSkillsService.createProject(proj3)
        rootSkillsService.pinProject(proj.projectId)
        rootSkillsService.pinProject(proj2.projectId)


        def res = rootSkillsService.getProject(proj.projectId)
        def originalProjectId = res.projectId
        res.projectId = "ShinyNewProjectId"
        res.name = "NewNewNew"
        rootSkillsService.updateProject(res, originalProjectId)

        when:
        def projects = rootSkillsService.getProjects()

        then:
        projects.size() == 2
        projects.find { it.projectId == res.projectId }
        projects.find { it.projectId == proj2.projectId }
    }

    @IgnoreIf({env["SPRING_PROFILES_ACTIVE"] == "pki" })
    def 'get users without role'() {
        expect:
//        this won't work until #145 is implemented and user clean up is added to the DefaultIntSpec
//        rootSkillsService.getUsersWithoutRole(role, usernameQuery).containsAll(result)


        rootSkillsService.getUsersWithoutRole(role, usernameQuery).size() >= expectedN
        where:
        role                    | usernameQuery | expectedN
        /*'ROLE_SUPER_DUPER_USER' | ''            | [[userId:'skills@skills.org', first:'Skills', last:'Test', nickname:'Skills Test', dn:''], [userId:'foo@bar.com', first:'Skills', last:'Test', nickname:'Skills Test', dn:'']]
        'ROLE_SUPER_DUPER_USER' | 'foo'         | [[userId:'foo@bar.com', first:'Skills', last:'Test', nickname:'Skills Test', dn:'']]
        'ROLE_SUPER_DUPER_USER' | 'bar'         | [[userId:'foo@bar.com', first:'Skills', last:'Test', nickname:'Skills Test', dn:'']]
        'ROLE_SUPER_DUPER_USER' | 'bar'         | [[userId:'foo@bar.com', first:'Skills', last:'Test', nickname:'Skills Test', dn:'']]
        'ROLE_SUPERVISOR'       | ''            | [[userId:'skills@skills.org', first:'Skills', last:'Test', nickname:'Skills Test', dn:''], [userId:'jh@dojo.com', first:'Skills', last:'Test', nickname:'Skills Test', dn:''], [userId:'foo@bar.com', first:'Skills', last:'Test', nickname:'Skills Test', dn:'']]
        'ROLE_SUPERVISOR'       | 'dojo'        | [[userId:'jh@dojo.com', first:'Skills', last:'Test', nickname:'Skills Test', dn:'']]
        'ROLE_SUPERVISOR'       | 'foo'         | [[userId:'foo@bar.com', first:'Skills', last:'Test', nickname:'Skills Test', dn:'']]*/
        'ROLE_SUPER_DUPER_USER' | ''            | 2
        'ROLE_SUPER_DUPER_USER' | 'foo'         | 1
        'ROLE_SUPER_DUPER_USER' | 'bar'         | 1
        'ROLE_SUPER_DUPER_USER' | 'bar'         | 1
        'ROLE_SUPERVISOR'       | ''            | 2
        'ROLE_SUPERVISOR'       | 'foo'         | 1
    }

    def 'verify root user also gets supervisor role'() {
        when:
        def result = rootSkillsService.getUsersWithRole('ROLE_SUPERVISOR')

        then:
        result
        result.find { it.userId == ultimateRoot }
    }

    def 'verify when adding root user that user also gets supervisor role'() {
        setup:
        def originalRootUsers = rootSkillsService.getRootUsers()
        assert !originalRootUsers.find {it.userId == nonRootUserId}

        def originalSupervisorUsers = rootSkillsService.getUsersWithRole('ROLE_SUPERVISOR')
        assert !originalSupervisorUsers.find {it.userId == nonRootUserId}

        when:
        rootSkillsService.addRootRole(nonRootUserId)
        def rootUsers = rootSkillsService.getRootUsers()
        def supervisorUsers = rootSkillsService.getUsersWithRole('ROLE_SUPERVISOR')

        then:
        rootUsers
        rootUsers.find { it.userId == nonRootUserId }
        supervisorUsers
        supervisorUsers.find { it.userId == nonRootUserId }
    }

    def 'verify when adding root user that already has supervisor role'() {
        setup:
        def originalRootUsers = rootSkillsService.getRootUsers()
        assert !originalRootUsers.find {it.userId == nonRootUserId}

        def originalSupervisorUsers = rootSkillsService.getUsersWithRole('ROLE_SUPERVISOR')
        assert !originalSupervisorUsers.find {it.userId == nonRootUserId}

        rootSkillsService.grantSupervisorRole(nonRootUserId)
        originalSupervisorUsers = rootSkillsService.getUsersWithRole('ROLE_SUPERVISOR')
        assert originalSupervisorUsers.find {it.userId == nonRootUserId}

        when:
        rootSkillsService.addRootRole(nonRootUserId)
        def rootUsers = rootSkillsService.getRootUsers()
        def supervisorUsers = rootSkillsService.getUsersWithRole('ROLE_SUPERVISOR')

        then:
        rootUsers
        rootUsers.find { it.userId == nonRootUserId }
        supervisorUsers
        supervisorUsers.find { it.userId == nonRootUserId }
    }

    def 'verify root user loses supervisor role when root is removed'() {

        setup:
        def originalRootUsers = rootSkillsService.getRootUsers()
        assert !originalRootUsers.find {it.userId == nonRootUserId}

        def originalSupervisorUsers = rootSkillsService.getUsersWithRole('ROLE_SUPERVISOR')
        assert !originalSupervisorUsers.find {it.userId == nonRootUserId}

        rootSkillsService.addRootRole(nonRootUserId)
        def rootUsers = rootSkillsService.getRootUsers()
        def supervisorUsers = rootSkillsService.getUsersWithRole('ROLE_SUPERVISOR')

        assert rootUsers.find { it.userId == nonRootUserId }
        assert supervisorUsers.find { it.userId == nonRootUserId }

        when:
        rootSkillsService.removeRootRole(nonRootUserId)
        rootUsers = rootSkillsService.getRootUsers()
        supervisorUsers = rootSkillsService.getUsersWithRole('ROLE_SUPERVISOR')

        then:
        rootUsers
        !rootUsers.find { it.userId == nonRootUserId }
        supervisorUsers
        !supervisorUsers.find { it.userId == nonRootUserId }
    }

    def 'verify root user loses supervisor role, and then can have root is removed'() {

        setup:
        def originalRootUsers = rootSkillsService.getRootUsers()
        assert !originalRootUsers.find {it.userId == nonRootUserId}

        def originalSupervisorUsers = rootSkillsService.getUsersWithRole('ROLE_SUPERVISOR')
        assert !originalSupervisorUsers.find {it.userId == nonRootUserId}

        rootSkillsService.addRootRole(nonRootUserId)
        def rootUsers = rootSkillsService.getRootUsers()
        def supervisorUsers = rootSkillsService.getUsersWithRole('ROLE_SUPERVISOR')

        assert rootUsers.find { it.userId == nonRootUserId }
        assert supervisorUsers.find { it.userId == nonRootUserId }

        when:
        rootSkillsService.removeSupervisorRole(nonRootUserId)

        // this was causing an assertion error since it also tries to remove supervisor role,
        // but it had already been removed previously
        rootSkillsService.removeRootRole(nonRootUserId)

        rootUsers = rootSkillsService.getRootUsers()
        supervisorUsers = rootSkillsService.getUsersWithRole('ROLE_SUPERVISOR')

        then:
        rootUsers
        !rootUsers.find { it.userId == nonRootUserId }
        supervisorUsers
        !supervisorUsers.find { it.userId == nonRootUserId }
    }

    def 'root user can manually report skill for a project they are not an admin of' () {
        // need to call DefaultIntSpec.getRandomUsers so that tests will work in ssl mode
        String userId = getRandomUsers(1)[0]

        //we need a different userId from the default root user for this test
        while (userId.contains("jh@dojo")) {
            userId = getRandomUsers(1)[0]
        }

        def proj = SkillsFactory.createProject()
        def subj = SkillsFactory.createSubject()
        def skills = SkillsFactory.createSkills(10, )

        skillsService.createProject(proj)
        skillsService.createSubject(subj)
        skillsService.createSkills(skills)

        when:
        def res = rootSkillsService.addSkill([projectId: SkillsFactory.defaultProjId, skillId: skills[0].skillId], userId)

        then:
        res.body.skillApplied
        res.body.explanation == "Skill event was applied"

        res.body.completed.size() == 3
        res.body.completed.find({ it.type == "Skill" }).id == skills[0].skillId
        res.body.completed.find({ it.type == "Skill" }).name == skills[0].name

        res.body.completed.find({ it.type == "Overall" }).id == "OVERALL"
        res.body.completed.find({ it.type == "Overall" }).name == "OVERALL"
        res.body.completed.find({ it.type == "Overall" }).level == 1

        res.body.completed.find({ it.type == "Subject" }).id == subj.subjectId
        res.body.completed.find({ it.type == "Subject" }).name == subj.name
        res.body.completed.find({ it.type == "Subject" }).level == 1
    }

    def 'root user can save user tags' () {
        // need to call DefaultIntSpec.getRandomUsers so that tests will work in ssl mode
        String userId = getRandomUsers(1)[0]

        //we need a different userId from the default root user for this test
        while (userId.contains("jh@dojo")) {
            userId = getRandomUsers(1)[0]
        }

        def proj = SkillsFactory.createProject()
        def subj = SkillsFactory.createSubject()
        def skills = SkillsFactory.createSkills(10,)

        skillsService.createProject(proj)
        skillsService.createSubject(subj)
        skillsService.createSkills(skills)

        when:
        rootSkillsService.addSkill([projectId: SkillsFactory.defaultProjId, skillId: skills[0].skillId], userId)
        def res = rootSkillsService.saveUserTag(userId, "myKey", ["coolTag"]);

        String metricsId = "numUsersPerTagBuilder"
        def tags1 = skillsService.getMetricsData(proj.projectId, metricsId, [tagKey: "myKey", currentPage: 1, pageSize: 5, sortDesc: true])
        println JsonOutput.toJson(tags1)
        then:
        res.success
        tags1.items[0].value == "coolTag"
    }

    def 'non-root user can NOT save user tags' () {
        // need to call DefaultIntSpec.getRandomUsers so that tests will work in ssl mode
        String userId = getRandomUsers(1)[0]

        //we need a different userId from the default root user for this test
        while (userId.contains("jh@dojo")) {
            userId = getRandomUsers(1)[0]
        }

        def proj = SkillsFactory.createProject()
        def subj = SkillsFactory.createSubject()
        def skills = SkillsFactory.createSkills(10,)

        skillsService.createProject(proj)
        skillsService.createSubject(subj)
        skillsService.createSkills(skills)

        when:
        rootSkillsService.addSkill([projectId: SkillsFactory.defaultProjId, skillId: skills[0].skillId], userId)
        nonRootSkillsService.saveUserTag(userId, "myKey", ["coolTag"]);
        then:
        SkillsClientException skillsClientException = thrown(SkillsClientException)
        skillsClientException.httpStatus == HttpStatus.FORBIDDEN
    }
}

