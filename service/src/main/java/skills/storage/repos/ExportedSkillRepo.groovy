/**
 * Copyright 2021 SkillTree
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

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.data.repository.query.Param
import org.springframework.lang.Nullable
import skills.storage.CatalogSkill
import skills.storage.model.ExportedSkill
import skills.storage.model.ExportedSkillTiny
import skills.storage.model.ImportExportStats
import skills.storage.model.SkillDefWithExtra

interface ExportedSkillRepo extends PagingAndSortingRepository<ExportedSkill, Integer> {

    @Nullable
    @Query('''select 'true' from ExportedSkill es where es.projectId = ?1 and es.skill.skillId = ?2''')
    Boolean doesSkillExistInCatalog(String projectId, String skillId)

    @Nullable
    @Query('''select es.skill.skillId from ExportedSkill es where es.projectId = ?1 and es.skill.skillId in ?2''')
    List<String> doSkillsExistInCatalog(String projectId, List<String> skillIds)

    @Nullable
    @Query('''select 'true' from ExportedSkill es where es.skill.skillId = ?1''')
    Boolean doesSkillIdExistInCatalog(String skillId)

    @Nullable
    @Query('''select 'true' from ExportedSkill es where lower(es.skill.name) = lower(?1)''')
    Boolean doesSkillNameExistInCatalog(String skillId)

    @Nullable
    @Query('''select es from ExportedSkill es where es.projectId = :projectId and es.skill.skillId = :skillId and es.skill.projectId = :projectId''')
    ExportedSkill getCatalogSkill(@Param("projectId") String projectId, @Param("skillId") String skillId)


    @Nullable
    @Query('''select es.skill as skill, 
                subject.name as subjectName, 
                subject.skillId as subjectId,
                project.name as projectName,
                es.created as exportedOn 
        from ExportedSkill es
        join ProjDef project on project.projectId = es.projectId
        join SkillRelDef srd on srd.child = es.skill and srd.type = 'RuleSetDefinition'
        join SkillDef subject on subject = srd.parent and subject.type = 'Subject'
        where 
             es.projectId <> ?1 and
             not exists (select 1 from SkillDef sd where sd.projectId = ?1 and sd.copiedFrom = es.skill.id)
    ''')
    List<CatalogSkill> getSkillsInCatalog(String projectId, Pageable pageable)

    @Nullable
    @Query('''select count(es) 
        from ExportedSkill es
        join ProjDef project on project.projectId = es.projectId
        join SkillRelDef srd on srd.child = es.skill and srd.type = 'RuleSetDefinition'
        join SkillDef subject on subject = srd.parent and subject.type = 'Subject'
        where 
             es.projectId <> ?1 and
             not exists (select 1 from SkillDef sd where sd.projectId = ?1 and sd.copiedFrom = es.skill.id)
    ''')
    Integer countSkillsInCatalog(String projectId)

    @Nullable
    @Query('''
        select es.skill as skill, 
                subject.name as subjectName, 
                subject.skillId as subjectId,
                project.name as projectName,
                es.created as exportedOn 
        from ExportedSkill es
        join ProjDef project on project.projectId = es.projectId
        join SkillRelDef srd on srd.child = es.skill and srd.type = 'RuleSetDefinition'
        join SkillDef subject on subject = srd.parent and subject.type = 'Subject'
        where
            es.projectId <> :projectId and 
            not exists (select 1 from SkillDef sd where sd.projectId = :projectId and sd.copiedFrom = es.skill.id) and
            lower(es.skill.name) like lower(concat('%', :skillSearch, '%'))  and 
            lower(project.name) like lower(concat('%', :projectSearch, '%')) and 
            lower(subject.name) like lower(concat('%', :subjectSearch, '%'))
    ''')
    List<CatalogSkill> getSkillsInCatalog(@Param("projectId") String projectId,
                                          @Param("projectSearch")String projectSearch,
                                          @Param("subjectSearch") String subjectSearch,
                                          @Param("skillSearch")String skillSearch,
                                          Pageable pageable)

    @Nullable
    @Query('''
        select count(es.skill)
        from ExportedSkill es
        join ProjDef project on project.projectId = es.projectId
        join SkillRelDef srd on srd.child = es.skill and srd.type = 'RuleSetDefinition'
        join SkillDef subject on subject = srd.parent and subject.type = 'Subject'
        where
            es.projectId <> :projectId and 
            not exists (select 1 from SkillDef sd where sd.projectId = :projectId and sd.copiedFrom = es.skill.id) and
            lower(es.skill.name) like lower(concat('%', :skillSearch, '%'))  and 
            lower(project.name) like lower(concat('%', :projectSearch, '%')) and 
            lower(subject.name) like lower(concat('%', :subjectSearch, '%'))
    ''')
    Integer countSkillsInCatalog(@Param("projectId") String projectId,
                                 @Param("projectSearch")String projectSearch,
                                 @Param("subjectSearch") String subjectSearch,
                                 @Param("skillSearch")String skillSearch)

    @Nullable
    @Query('''select es.skill from ExportedSkill es where es.projectId = ?1''')
    List<SkillDefWithExtra> getSkillsExportedByProject(String projectId, Pageable pageable)

    @Nullable
    @Query('''
            select es.skill.skillId as skillId, 
                 es.skill.name as skillName,
                 es.created as exportedOn,
                 subject.name as subjectName,
                 subject.skillId as subjectId
             from ExportedSkill es, SkillRelDef srd, SkillDef subject 
             where es.projectId = ?1 and 
             subject = srd.parent and
             srd.type = 'RuleSetDefinition' and
             subject.type = 'Subject' and 
             srd.child.id = es.skill.id
            ''')
    List<ExportedSkillTiny> getTinySkillsExportedByProject(String projectId, Pageable pageable)

    @Query('''select count(es.id) from ExportedSkill es where es.projectId = ?1''')
    Integer countSkillsExportedByProject(String projectId)

    @Nullable
    @Query('''
            select count(es.skill) as numberOfSkills, 
                count (distinct sd.projectId) as numberOfProjects
            from ExportedSkill es
            left join SkillDef sd on sd.copiedFromProjectId = es.projectId 
            where es.projectId = ?1
    ''')
    ImportExportStats getExportedSkillStats(String projectId)

}
