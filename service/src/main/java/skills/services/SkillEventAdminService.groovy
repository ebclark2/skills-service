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
package skills.services

import callStack.profiler.Profile
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import skills.auth.UserInfoService
import skills.controller.exceptions.ErrorCode
import skills.controller.exceptions.SkillException
import skills.controller.result.model.RequestResult
import skills.services.admin.SkillCatalogService
import skills.services.events.BulkSkillEventResult
import skills.services.events.CompletionItem
import skills.services.events.SkillEventResult
import skills.services.events.SkillEventsService
import skills.storage.accessors.ProjDefAccessor
import skills.storage.model.*
import skills.storage.repos.*

@Component
@Slf4j
class SkillEventAdminService {

    @Autowired
    UserPerformedSkillRepo performedSkillRepository

    @Autowired
    UserPointsRepo userPointsRepo

    @Autowired
    SkillEventsSupportRepo skillEventsSupportRepo

    @Autowired
    UserAchievedLevelRepo achievedLevelRepo

    @Autowired
    SkillRelDefRepo skillRelDefRepo

    @Autowired
    ProjDefAccessor projDefAccessor

    @Autowired
    LevelDefinitionStorageService levelDefService

    @Autowired
    UserEventService userEventService

    @Autowired
    private SkillEventsService skillsManagementFacade

    @Autowired
    private UserInfoService userInfoService

    @Autowired
    SkillCatalogService skillCatalogService

    @Transactional
    @Profile
    BulkSkillEventResult bulkReportSkills(String projectId, String skillId, List<String> userIds, Date incomingSkillDate) {
        BulkSkillEventResult bulkResult = new BulkSkillEventResult(projectId: projectId, skillId: skillId)
        for (String requestedUserId : userIds) {
            try {
                String userId = userInfoService.getUserName(requestedUserId, false)
                SkillEventResult result = skillsManagementFacade.reportSkill(projectId, skillId, userId, false, incomingSkillDate)
                if (!bulkResult.name) { bulkResult.name = result.name }
                if (result.skillApplied) {
                    bulkResult.userIdsAppliedCount++
                } else {
                    bulkResult.userIdsNotAppliedCount++
                }
            } catch(Exception e) {
                bulkResult.userIdsErrored += requestedUserId
            }
        }
        return bulkResult
    }

    @Transactional
    RequestResult deleteSkillEvent(String projectId, String skillId, String userId, Long timestamp) {
        List<UserPerformedSkill> performedSkills = performedSkillRepository.findAllByProjectIdAndSkillIdAndUserIdAndPerformedOn(projectId, skillId, userId, new Date(timestamp))
        if (!performedSkills) {
            throw new SkillException("This skill event does not exist", projectId, skillId, ErrorCode.BadParam)
        }
        // may have more than 1 event with the same exact timestamp, this happens when multiple events may fall
        // within configured time window and client send the same timestamp (example UI calendar control)
        UserPerformedSkill performedSkill = performedSkills.first()
        log.debug("Deleting skill [{}] for user [{}]", performedSkill, userId)

        SkillDefMin skillDefinitionMin = getSkillDef(projectId, skillId)
        Long numExistingSkills = performedSkillRepository.countByUserIdAndProjectIdAndSkillId(userId, skillDefinitionMin.projectId, skillDefinitionMin.skillId) ?: 0 // account for null

        // handle catalog
        final boolean isInCatalog = skillCatalogService.isAvailableInCatalog(skillDefinitionMin.projectId, skillDefinitionMin.skillId)
        if (skillDefinitionMin.getCopiedFrom() > 0 || isInCatalog) {
            List<SkillDefMin> related = skillCatalogService.getRelatedSkills(skillDefinitionMin)
            related?.each {
                updateUserPointsAndAchievementsWhenPerformedSkillRemoved(userId, it, performedSkill.performedOn, numExistingSkills)
            }
        }

        RequestResult res = new RequestResult()

        List<SkillDef> performedDependencies = performedSkillRepository.findPerformedParentSkills(userId, projectId, skillId)
        if (performedDependencies) {
            res.success = false
            res.explanation = "You cannot delete a skill event when a parent skill dependency has already been performed. You must first delete " +
                    "the performed skills for the parent dependencies: ${performedDependencies.collect({ it.projectId + ":" + it.skillId })}."
            return res
        }

        SkillEventResult skillEventResult = updateUserPointsAndAchievementsWhenPerformedSkillRemoved(userId, skillDefinitionMin, performedSkill.performedOn, numExistingSkills)
        res.success = skillEventResult.skillApplied
        res.explanation = skillEventResult.explanation

        performedSkillRepository.delete(performedSkill)
        userEventService.removeEvent(performedSkill.performedOn, performedSkill.userId, performedSkill.skillRefId)

        return res
    }

    private SkillEventResult updateUserPointsAndAchievementsWhenPerformedSkillRemoved(String userId, SkillDefMin skillDefinitionMin, Date performedOn, Long numExistingPerformedSkills) {
        updateUserPoints(userId, skillDefinitionMin, performedOn, skillDefinitionMin.skillId)
        boolean requestedSkillCompleted = hasReachedMaxPoints(numExistingPerformedSkills, skillDefinitionMin)
        if (requestedSkillCompleted) {
            checkForBadgesAchieved(userId, skillDefinitionMin)
            //this removes the skill achievements
            achievedLevelRepo.deleteByProjectIdAndSkillIdAndUserIdAndLevel(skillDefinitionMin.projectId, skillDefinitionMin.skillId, userId, null)
        }
        SkillEventResult skillEventResult = new SkillEventResult(projectId: skillDefinitionMin.projectId, skillId: skillDefinitionMin.skillId, name: skillDefinitionMin.name)
        checkParentGraph(performedOn, skillEventResult, userId, skillDefinitionMin)
        deleteProjectLevelIfNecessary(skillDefinitionMin.projectId, userId, numExistingPerformedSkills.toInteger())
        return skillEventResult
    }

    private void deleteProjectLevelIfNecessary(String projectId, String userId, int numberOfExistingEvents) {
        List<UserAchievement> projAchievements = achievedLevelRepo.findAllByUserIdAndProjectIdAndSkillId(userId, projectId, null)
        Integer userProjectPoints = userPointsRepo.getPointsByProjectIdAndUserId(projectId, userId)
        if (userProjectPoints == null && (numberOfExistingEvents - 1) <= 0 ){
            log.info("There are no skill events for user [{}] proj [{}]. Will remove all of them", userId, projectId)
            deleteAchievements(projAchievements)
        } else {
            if (projAchievements && userProjectPoints != null) {
                ProjDef projDef = projDefAccessor.getProjDef(projectId)
                LevelDefinitionStorageService.LevelInfo userCurrentLevelShouldBe = levelDefService.getOverallLevelInfo(projDef, userProjectPoints)
                List<UserAchievement> toDelete = projAchievements.findAll { it.level > userCurrentLevelShouldBe.level }
                deleteAchievements(toDelete)
            }
        }
    }

    private void deleteAchievements(List<UserAchievement> toDelete) {
        for (UserAchievement achievement in toDelete) {
            log.debug("deleting achievement ${achievement}, User no longer has enough points")
            achievedLevelRepo.delete(achievement)
        }
    }

    private void checkForBadgesAchieved(String userId, SkillDefMin currentSkillDef) {
        List<SkillRelDef> parentsRels = skillRelDefRepo.findAllByChildIdAndType(currentSkillDef.id, SkillRelDef.RelationshipType.BadgeRequirement)
        parentsRels.each {
            if (it.parent.type == SkillDef.ContainerType.Badge && withinActiveTimeframe(it.parent)) {
                SkillDef badge = it.parent
                List<SkillDef> nonAchievedChildren = achievedLevelRepo.findNonAchievedChildren(userId, badge.projectId, badge.skillId, SkillRelDef.RelationshipType.BadgeRequirement)
                if (!nonAchievedChildren) {
                    achievedLevelRepo.deleteByProjectIdAndSkillIdAndUserIdAndLevel(badge.projectId, badge.skillId, userId, null)
                }
            }
        }
    }

    private boolean withinActiveTimeframe(SkillDef skillDef) {
        boolean withinActiveTimeframe = true
        if (skillDef.startDate && skillDef.endDate) {
            Date now = new Date()
            withinActiveTimeframe = skillDef.startDate.before(now) && skillDef.endDate.after(now)
        }
        return withinActiveTimeframe
    }

    private boolean hasReachedMaxPoints(long numSkills, SkillDefMin skillDefinition) {
        return numSkills * skillDefinition.pointIncrement >= skillDefinition.totalPoints
    }

    private UserPoints updateUserPoints(String userId, SkillDefMin requestedSkill, Date incomingSkillDate, String skillId = null) {
        doUpdateUserPoints(requestedSkill, userId, incomingSkillDate, skillId)
        return doUpdateUserPoints(requestedSkill, userId, null, skillId)
    }

    private UserPoints doUpdateUserPoints(SkillDefMin requestedSkill, String userId, Date incomingSkillDate, String skillId) {
        Date day = incomingSkillDate ? new Date(incomingSkillDate.time).clearTime() : null
        UserPoints userPoints = userPointsRepo.findByProjectIdAndUserIdAndSkillIdAndDay(requestedSkill.projectId, userId, skillId, day)
        userPoints.points -= requestedSkill.pointIncrement

        if (userPoints.points <= 0) {
            userPointsRepo.delete(userPoints)
        } else {
            userPointsRepo.save(userPoints)
        }

        return userPoints
    }

    private void checkParentGraph(Date incomingSkillDate, SkillEventResult res, String userId, SkillDefMin skillDef) {
        updateByTraversingUpSkillDefs(incomingSkillDate, res, skillDef, skillDef, userId)

        // updated project level
        updateUserPoints(userId, skillDef, incomingSkillDate, null)
    }

    private void updateByTraversingUpSkillDefs(Date incomingSkillDate, SkillEventResult res,
                                               SkillDefMin currentDef,
                                               SkillDefMin requesterDef,
                                               String userId) {
        if (currentDef.type == SkillDef.ContainerType.Subject) {
            UserPoints updatedPoints = updateUserPoints(userId, requesterDef, incomingSkillDate, currentDef.skillId)

            List<LevelDef> levelDefs = skillEventsSupportRepo.findLevelsBySkillId(currentDef.id)
            int currentScore = updatedPoints.points
            LevelDefinitionStorageService.LevelInfo levelInfo = levelDefService.getLevelInfo(currentDef.projectId, levelDefs, currentDef.totalPoints, currentScore)
            calculateLevels(levelInfo, updatedPoints, userId)
        }

        List<SkillDefMin> parentsRels = skillEventsSupportRepo.findParentSkillsByChildIdAndType(currentDef.id, SkillRelDef.RelationshipType.RuleSetDefinition)
        parentsRels?.each {
            updateByTraversingUpSkillDefs(incomingSkillDate, res, it, requesterDef, userId)
        }
    }

    private CompletionItem calculateLevels(LevelDefinitionStorageService.LevelInfo levelInfo, UserPoints userPts, String userId) {
        CompletionItem res

        List<UserAchievement> userAchievedLevels = achievedLevelRepo.findAllByUserIdAndProjectIdAndSkillId(userId, userPts.projectId, userPts.skillId)

        // we are decrementing, so we need to remove any level that is greater than the current level
        List<UserAchievement> levelsToRemove = userAchievedLevels?.findAll { it.level > levelInfo.level }
        if (levelsToRemove) {
            achievedLevelRepo.deleteAll(levelsToRemove)
        }

        return res
    }

    private SkillDefMin getSkillDef(String projectId, String skillId) {
        SkillDefMin skillDefinition = skillEventsSupportRepo.findByProjectIdAndSkillIdAndType(projectId, skillId, SkillDef.ContainerType.Skill)
        if (!skillDefinition) {
            throw new SkillException("Skill definition does not exist. Must create the skill definition first!", projectId, skillId)
        }
        return skillDefinition
    }

}
