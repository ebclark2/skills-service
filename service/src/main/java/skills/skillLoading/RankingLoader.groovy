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
package skills.skillLoading

import callStack.profiler.Profile
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import skills.controller.exceptions.SkillException
import skills.services.LevelDefinitionStorageService
import skills.skillLoading.model.LeaderboardRes
import skills.skillLoading.model.RankedUserRes
import skills.storage.model.ProjDef
import skills.storage.model.UserAttrs
import skills.storage.repos.ProjDefRepo
import skills.storage.repos.UserAchievedLevelRepo
import skills.storage.repos.UserAttrsRepo
import skills.storage.repos.UserPointsRepo
import skills.storage.model.UserAchievement
import skills.storage.model.UserPoints
import skills.skillLoading.model.UsersPerLevel
import skills.skillLoading.model.SkillsRanking
import skills.skillLoading.model.SkillsRankingDistribution

@Component
@Slf4j
@CompileStatic
class RankingLoader {

    @Autowired
    UserPointsRepo userPointsRepository

    @Autowired
    UserAttrsRepo userAttrsRepo

    @Autowired
    UserAchievedLevelRepo achievedLevelRepository

    @Autowired
    LevelDefinitionStorageService levelDefinitionStorageService

    @Autowired
    ProjDefRepo projDefRepo

    SkillsRanking getUserSkillsRanking(String projectId, String userId, String subjectId = null){
        UserPoints usersPoints = findUserPoints(projectId, userId, subjectId)
        return doGetUserSkillsRanking(projectId, usersPoints, subjectId)
    }

    @Profile
    LeaderboardRes getLeaderboard(String projectId, String userId, LeaderboardRes.Type type, String subjectId = null){
        ProjDef projDef = projDefRepo.findByProjectId(projectId)

        List<RankedUserRes> res
        if (type == LeaderboardRes.Type.tenAroundMe) {
            UserPoints myPoints = userPointsRepository.findByProjectIdAndUserIdAndSkillIdAndDay(projectId, userId, subjectId, null)
            UserAttrs userAttrs = userAttrsRepo.findByUserId(userId)
            PageRequest pageRequestForBelow = PageRequest.of(0, 5, Sort.Direction.DESC, "points")
            List<UserPointsRepo.RankedUserRes> below = subjectId ?
                    userPointsRepository.findUsersForLeaderboardPointsLessOrEqual(projectId, subjectId, myPoints.points, userId, pageRequestForBelow) :
                    userPointsRepository.findUsersForLeaderboardPointsLessOrEqual(projectId, myPoints.points, userId, pageRequestForBelow)

            PageRequest pageRequestForAbove = PageRequest.of(0, 5, Sort.Direction.ASC, "points")
            List<UserPointsRepo.RankedUserRes> above = subjectId ?
                    userPointsRepository.findUsersForLeaderboardPointsMoreOrEqual(projectId, subjectId, myPoints.points, userId, pageRequestForAbove) :
                    userPointsRepository.findUsersForLeaderboardPointsMoreOrEqual(projectId, myPoints.points, userId, pageRequestForAbove)

            SkillsRanking rank = doGetUserSkillsRanking(projectId, myPoints, subjectId)
            if (rank.getPosition() <= 5){
                throw new SkillException("Are not allowed to request type=[${LeaderboardRes.Type.tenAroundMe}] when user is ranked in top 5. userId=[${userId}], rank=[${rank.position}], subject=[${subjectId}]", projectId)
            }
            res = []
            res.addAll(convertToRankedUserRes(above, rank.position-5, userId))
            res.add(new RankedUserRes(rank: rank.position, userId: userAttrs.userIdForDisplay, firstName: userAttrs.firstName, lastName: userAttrs.lastName, isItMe: true, points:  myPoints.points, userFirstSeenTimestamp: userAttrs.created.time))
            res.addAll(convertToRankedUserRes(below, rank.position+1, userId))
        } else {
            PageRequest pageRequest = PageRequest.of(0, 10, Sort.Direction.DESC, "points")
            List<UserPointsRepo.RankedUserRes> rankedUserRes = subjectId ?
                    userPointsRepository.findUsersForLeaderboard(projectId, subjectId, pageRequest) :
                    userPointsRepository.findUsersForLeaderboard(projectId, pageRequest)
            res = convertToRankedUserRes(rankedUserRes, 1, userId)
        }

        return new LeaderboardRes(rankedUsers: res, totalProjPoints: projDef.totalPoints)
    }

    private List<RankedUserRes> convertToRankedUserRes(List<UserPointsRepo.RankedUserRes> rankedUserRes, int startRank, String userId) {
        int count = startRank
        List<RankedUserRes> res = rankedUserRes.collect {
            new RankedUserRes(
                    rank: count++,
                    userId: it.getUserIdForDisplay(),
                    firstName: it.getUserFirstName(),
                    lastName: it.getUserLastName(),
                    points: it.getPoints(),
                    userFirstSeenTimestamp: it.getUserFirstSeenTimestamp()?.getTime(),
                    isItMe: it.getUserId() == userId,
            )
        }
        return res
    }

    @Profile
    private UserPoints findUserPoints(String projectId, String userId, String subjectId) {
        userPointsRepository.findByProjectIdAndUserIdAndSkillIdAndDay(projectId, userId, subjectId, null)
    }

    @Profile
    private  SkillsRanking doGetUserSkillsRanking(String projectId, UserPoints usersPoints, String subjectId = null) {
        int numUsers = findNumberOfUsers(projectId, subjectId) as int
        // always calculate total number of users
        SkillsRanking ranking
        if (usersPoints) {
            int numUsersWithMorePoints = calculateNumberOfUsersWithGreaterPoints(subjectId, projectId, usersPoints)
            int position = numUsersWithMorePoints+1
            ranking = new SkillsRanking(numUsers: numUsers, position: position)
        } else {
            // last one
            ranking = new SkillsRanking(numUsers: numUsers+1, position: numUsers+1)
        }

        return ranking
    }

    @Profile
    private int calculateNumberOfUsersWithGreaterPoints(String subjectId, String projectId, UserPoints usersPoints) {
        subjectId ? userPointsRepository.calculateNumUsersWithLessScore(projectId, subjectId, usersPoints.points)
                : userPointsRepository.calculateNumUsersWithLessScore(projectId, usersPoints.points)
    }

    @Profile
    private long findNumberOfUsers(String projectId, String subjectId) {
        userPointsRepository.countByProjectIdAndSkillIdAndDay(projectId, subjectId, null)
    }

    SkillsRankingDistribution getRankingDistribution(String projectId, String userId, String subjectId = null) {
        UserPoints usersPoints = loadUserPoints(projectId, userId, subjectId)

        List<UserAchievement> myLevels = loadUserAchievements(userId, projectId, subjectId)
        int myLevel = myLevels ? myLevels.collect({it.level}).max() : 0

        final int currentPts = usersPoints?.points ?: 0
        List<UserPoints> next = findHighestUserPoints(projectId, currentPts, subjectId)
        Integer pointsToPassNextUser = next ? next.first().points - currentPts : -1

        Integer pointsAnotherUserToPassMe = -1
        if(currentPts){
            List<UserPoints> previous = findLowestUserPoints(projectId, currentPts, subjectId)
            pointsAnotherUserToPassMe = previous ? currentPts - previous.first().points : -1
        }

        return new SkillsRankingDistribution(myLevel: myLevel, myPoints: usersPoints?.points ?: 0,
                pointsToPassNextUser: pointsToPassNextUser, pointsAnotherUserToPassMe: pointsAnotherUserToPassMe)
    }

    @CompileStatic
    @Profile
    private UserPoints loadUserPoints(String projectId, String userId, String subjectId) {
        userPointsRepository.findByProjectIdAndUserIdAndSkillIdAndDay(projectId, userId, subjectId, null)
    }

    @CompileStatic
    @Profile
    private List<UserAchievement> loadUserAchievements(String userId, String projectId, String subjectId) {
        achievedLevelRepository.findAllByUserIdAndProjectIdAndSkillId(userId, projectId, subjectId)
    }

    @CompileStatic
    @Profile
    private List<UserPoints> findLowestUserPoints(String projectId, int points, String subjectId) {
        List<UserPoints> previous = userPointsRepository.findByProjectIdAndSkillIdAndPointsLessThanAndDayIsNull(projectId, subjectId, points, PageRequest.of(0, 1, Sort.Direction.DESC, "points"))
        previous
    }

    @CompileStatic
    @Profile
    private List<UserPoints> findHighestUserPoints(String projectId, int points, String subjectId) {
        List<UserPoints> next = userPointsRepository.findByProjectIdAndSkillIdAndPointsGreaterThanAndDayIsNull(projectId, subjectId, points, PageRequest.of(0, 1, Sort.Direction.ASC, "points"))
        next
    }

    @CompileStatic
    @Profile
    List<UsersPerLevel> getUserCountsPerLevel(String projectId, boolean includeZeroLevel = false, String subjectId = null) {
        List<skills.controller.result.model.LevelDefinitionRes> levels = levelDefinitionStorageService.getLevels(projectId, subjectId)
        List<UsersPerLevel> usersPerLevel = !levels ? [] : levels.sort({
            it.level
        }).collect { skills.controller.result.model.LevelDefinitionRes levelMeta ->
            Integer numUsers = achievedLevelRepository.countByProjectIdAndSkillIdAndLevel(projectId, subjectId, levelMeta.level)
            new UsersPerLevel(level: levelMeta.level, numUsers: numUsers ?: 0)
        }

        // when level completed by a user a UserAchievement record is stored,
        // a user that achieved level 1, 2 and 3 will have three UserAchievement records, therefore
        // the sql logic ends up double counting for lower levels; as a fix let's remove
        // number of users of higher levels from lower levels
        usersPerLevel = usersPerLevel.sort({ it.level })
        usersPerLevel.eachWithIndex { UsersPerLevel entry, int i ->
            if (i + 1 < usersPerLevel.size()) {
                entry.numUsers -= usersPerLevel[i + 1].numUsers
            }
        }

        if(includeZeroLevel){
            Integer numUsers = achievedLevelRepository.countByProjectIdAndSkillIdAndLevel(projectId, subjectId, 0)
            usersPerLevel.add(0, new UsersPerLevel(level: 0, numUsers: numUsers ?: 0))
        }

        return usersPerLevel
    }
}
