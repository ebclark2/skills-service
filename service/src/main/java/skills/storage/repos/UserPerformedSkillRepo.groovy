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
package skills.storage.repos

import groovy.transform.CompileStatic
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.lang.Nullable
import skills.storage.model.DayCountItem
import skills.storage.model.SkillDef
import skills.storage.model.UserPerformedSkill

@CompileStatic
interface UserPerformedSkillRepo extends JpaRepository<UserPerformedSkill, Integer> {

    // find an "exact" performed event;
    // may have more than 1 event with the same exact timestamp, this happens when multiple events may fall
    // within configured time window and client send the same timestamp (example UI calendar control)
    @Nullable
    List<UserPerformedSkill> findAllByProjectIdAndSkillIdAndUserIdAndPerformedOn(String projectId, String skillId, String userId, Date performedOn)

    void deleteByProjectIdAndSkillId(String projectId, String skillId)

    Long countByUserIdAndProjectId(String userId, String projectId)
    Long countByUserIdAndProjectIdAndSkillIdIgnoreCaseContaining(String userId, String projectId, String skillId)
    Long countByUserIdAndProjectIdAndSkillId(String userId, String projectId, String skillId)
    Long countByUserIdAndProjectIdAndSkillIdAndPerformedOnGreaterThanAndPerformedOnLessThan(String userId, String projectId, String skillId, Date startDate, Date endDate)

    @Query("SELECT DISTINCT(p.userId) from UserPerformedSkill p where p.projectId=?1 and lower(p.userId) LIKE %?2% order by p.userId asc" )
    List<String> findDistinctUserIdsForProject(String projectId, String userIdQuery, Pageable pageable)

    @Query("SELECT DISTINCT(p.userId) from UserPerformedSkill p where lower(p.userId) LIKE %?1% order by p.userId asc" )
    List<String> findDistinctUserIds(String userIdQuery, Pageable pageable)

    Boolean existsByUserId(String userId)
    Boolean existsByProjectIdAndUserId(String userId, String projectId)

    List<UserPerformedSkill> findByUserIdAndProjectIdAndSkillIdIgnoreCaseContaining(String userId, String projectId, String skillId, Pageable pageable)

    @Query('''SELECT COUNT(DISTINCT p.skillId) from UserPerformedSkill p where p.userId = ?2 
            and p.skillRefId in (select case when copiedFrom is not null then copiedFrom else id end as id from SkillDef where type = 'Skill' and projectId = ?1)''')
    Integer countDistinctSkillIdByProjectIdAndUserId(String projectId, String userId)

    @Query('''SELECT COUNT(DISTINCT p.userId) from UserPerformedSkill p 
                where p.skillRefId in (
                    select case when s.copiedFrom is not null then s.copiedFrom else s.id end as id
                    from SkillDef s
                    where 
                    s.projectId=?1 and s.skillId = ?2)''')
    Integer countDistinctUserIdByProjectIdAndSkillId(String projectId, String skillId)

    @Query(''' select DISTINCT(sdParent)
        from SkillDef sdParent, SkillRelDef srd, SkillDef sdChild
            inner join UserPerformedSkill ups on sdParent.id = ups.skillRefId and ups.userId=?1
        where 
            srd.parent=sdParent.id and 
            srd.child=sdChild.id and
            sdChild.projectId=?2 and 
            sdChild.skillId=?3 and 
            srd.type='Dependence' ''')
    List<SkillDef> findPerformedParentSkills(String userId, String projectId, String skillId)

    @Nullable
    @Query(value = '''
        WITH skills AS (
            select case when child.copied_from_skill_ref is not null then child.copied_from_skill_ref else child.id end as id,
                   child.point_increment                                                                                as pointIncrement
            from skill_definition parent,
                 skill_relationship_definition rel,
                 skill_definition child
            where parent.project_id = :projectId
              and parent.skill_id = :skillId
              and rel.parent_ref_id = parent.id
              and rel.child_ref_id = child.id
              and rel.type in ('RuleSetDefinition', 'SkillsGroupRequirement')
              and child.type = 'Skill'
              and child.version <= :version
        )
        select CAST(ups.performed_on as date) as day, SUM(skills.pointIncrement) as count
        from user_performed_skill ups,
             skills
        where ups.user_id = :userId
          and ups.skill_ref_id = skills.id
          and ups.skill_ref_id NOT IN (select up.skill_ref_id
                               from user_points up
                               where up.contributes_to_skills_group = 'false'
                                 and up.user_id = :userId
                                 and up.project_Id = :projectId)
        group by CAST(ups.performed_on as date)''', nativeQuery = true)
    List<DayCountItem> calculatePointHistoryForSubject(@Param('projectId') String projectId,
                                                                           @Param('userId') String userId,
                                                                           @Param('skillId') String skillId,
                                                                           @Param('version') Integer version)

    @Nullable
    @Query(value = '''
        WITH skills AS (
            select case when copied_from_skill_ref is not null then copied_from_skill_ref else id end as id,
                   point_increment as pointIncrement
            from skill_definition child
            where project_id = :projectId
              and type = 'Skill'
              and version <= :version
        )
        select CAST(ups.performed_on as date) as day, SUM(skills.pointIncrement) as count
        from user_performed_skill ups,
             skills
        where ups.user_id = :userId
          and ups.skill_ref_id = skills.id
          and ups.skill_ref_id NOT IN (select up.skill_ref_id
                               from user_points up
                               where up.contributes_to_skills_group = 'false'
                                 and up.user_id = :userId
                                 and up.project_Id = :projectId)
        group by CAST(ups.performed_on as date)''', nativeQuery = true)
    List<DayCountItem> calculatePointHistoryForProject(@Param('projectId') String projectId,
                                                       @Param('userId') String userId,
                                                       @Param('version') Integer version)

    @Query('''select CAST(ups.performedOn as date) as day, count(ups.id) as count
        from UserPerformedSkill ups
        where
        ups.projectId = :projectId and
        ups.skillId=:skillId
        group by CAST(ups.performedOn as date)
    ''')
    List<DayCountItem> countsByDay(@Param('projectId') String projectId, @Param('skillId') String skillId)

    @Query('''select CAST(ups.performedOn as date) as day, count(ups.id) as count
        from UserPerformedSkill ups
        where
        ups.skillRefId in (
            select case when copiedFrom is not null then copiedFrom else id end as id 
            from SkillDef 
            where type = 'Skill' and skillId = :skillId and projectId = :projectId
        ) and
        ups.performedOn > :from
        group by CAST(ups.performedOn as date)
    ''')
    List<DayCountItem> countsByDay(@Param('projectId') String projectId, @Param('skillId') String skillId, @Param("from") Date from)

    @Query("SELECT DISTINCT(p.projectId) from UserPerformedSkill p where p.userId=?1 order by p.projectId asc" )
    List<String> findDistinctProjectIdsWithUserPoints(String userId)

}
