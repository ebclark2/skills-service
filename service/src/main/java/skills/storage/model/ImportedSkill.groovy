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
package skills.storage.model

import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

class ImportedSkill implements Serializable{


    @ManyToOne
    @JoinColumn(name="owning_container_skill_ref_id")
    SkillDef owningContainer //if skill belongs to a Subject, this should be the subject
}
