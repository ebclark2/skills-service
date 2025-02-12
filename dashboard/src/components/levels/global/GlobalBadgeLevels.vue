/*
Copyright 2020 SkillTree

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
<template>
  <div>
    <sub-page-header title="Levels"/>

    <b-card body-class="p-0">
      <loading-container v-model="isLoading">
        <div class="mb-4 m-3">
          <div class="row p-0">
            <div class="col-md">
              <project-selector ref="projectSelectorRef" v-model="selectedProject" @added="projectAdded" @removed="projectRemoved"></project-selector>
            </div>
            <div class="col-md my-3 m-md-0">
              <level-selector v-model="selectedLevel" :project-id="selectedProjectId" :disabled="!selectedProject" :placeholder="levelPlaceholder"></level-selector>
            </div>
            <div class="col-md-auto">
              <span>
                <button :disabled="!(selectedProject && selectedLevel)" type="button" class="btn btn-outline-primary"
                        @click="addLevel" data-cy="addGlobalBadgeLevel" aria-label="add project level requirement to global badge">
                  <span class="d-none d-sm-inline"></span>Add <i class="fas fa-plus-circle" aria-hidden="true"/>
                </button>
              </span>
            </div>
          </div>
        </div>
        <simple-levels-table ref="globalLevelsTable" v-if="badgeLevels && badgeLevels.length > 0"
                             @change-level="changeLevel"
                             :levels="badgeLevels" @level-removed="deleteLevel"></simple-levels-table>
        <no-content2 v-else title="No Levels Added Yet..." icon="fas fa-trophy" class="mb-5"
                     message="Please select a project and level from drop-down menus above to start adding levels to this badge!"></no-content2>

      </loading-container>
    </b-card>

    <change-project-level @level-changed="saveLevelChange"
                          @hidden="changeLevelClosed"
                          v-if="showChangeLevel"
                          :title="`Change Required Level for ${projectLevelName}`"
                          :current-level="projectLevel"
                          :project-id="projectLevelId"/>
  </div>
</template>

<script>
  import { createNamespacedHelpers } from 'vuex';

  import ChangeProjectLevel from '@/components/levels/global/ChangeProjectLevel';
  import GlobalBadgeService from '../../badges/global/GlobalBadgeService';
  import SimpleLevelsTable from './SimpleLevelsTable';
  import ProjectSelector from './ProjectSelector';
  import LevelSelector from './LevelSelector';
  import NoContent2 from '../../utils/NoContent2';
  import SubPageHeader from '../../utils/pages/SubPageHeader';
  import LoadingContainer from '../../utils/LoadingContainer';
  import MsgBoxMixin from '../../utils/modal/MsgBoxMixin';

  const { mapActions } = createNamespacedHelpers('badges');

  export default {
    name: 'Levels',
    components: {
      ProjectSelector,
      LevelSelector,
      SimpleLevelsTable,
      LoadingContainer,
      SubPageHeader,
      NoContent2,
      ChangeProjectLevel,
    },
    mixins: [MsgBoxMixin],
    data() {
      return {
        selectedProject: null,
        selectedLevel: null,
        isLoading: true,
        levelPlaceholder: 'First choose a Project',
        badge: null,
        badgeId: null,
        badgeLevels: [],
        showChangeLevel: false,
        projectLevelId: null,
        projectLevel: null,
        projectLevelName: null,
      };
    },
    computed: {
      selectedProjectId() {
        let selectedProjectId = null;
        if (this.selectedProject) {
          selectedProjectId = this.selectedProject.projectId;
        }
        return selectedProjectId;
      },
    },
    mounted() {
      this.badgeId = this.$route.params.badgeId;
      this.loadBadgeLevels();
    },
    watch: {
      '$route.params.badgeId': function badgeIdParamChanged() {
        this.badgeId = this.$route.params.badgeId;
      },
    },
    methods: {
      ...mapActions([
        'loadGlobalBadgeDetailsState',
      ]),
      loadBadgeLevels() {
        if (this.$route.params.badge) {
          this.badge = this.$route.params.badge;
          this.badgeLevels = this.badge.requiredProjectLevels;
          this.isLoading = false;
        } else {
          GlobalBadgeService.getBadge(this.badgeId)
            .then((response) => {
              this.badge = response;
              this.badgeLevels = response.requiredProjectLevels;
              this.isLoading = false;
            });
        }
      },
      addLevel() {
        GlobalBadgeService.assignProjectLevelToBadge(this.badgeId, this.selectedProject.projectId, this.selectedLevel)
          .then(() => {
            const newLevel = {
              badgeId: this.badgeId,
              projectId: this.selectedProject.projectId,
              projectName: this.selectedProject.name,
              level: this.selectedLevel,
            };
            this.badgeLevels.push(newLevel);
            this.selectedLevel = null;
            this.loadGlobalBadgeDetailsState({ badgeId: this.badgeId });
            this.selectedProject = null;
            this.$refs.projectSelectorRef.loadProjectsForBadge();
            this.$emit('global-badge-levels-changed', newLevel);
          });
      },
      deleteLevel(deletedLevel) {
        const msg = `Removing this level will award this badge to users that fulfill all of the remaining requirements.
        Are you sure you want to remove Level "${deletedLevel.level}" for project "${deletedLevel.projectName}" from Badge "${this.badge.name}"?`;
        this.msgConfirm(msg, 'WARNING: Remove Required Level').then((res) => {
          if (res) {
            this.levelDeleted(deletedLevel);
          }
        });
      },
      levelDeleted(deletedItem) {
        GlobalBadgeService.removeProjectLevelFromBadge(this.badgeId, deletedItem.projectId, deletedItem.level)
          .then(() => {
            this.badgeLevels = this.badgeLevels.filter((item) => `${item.projectId}${item.level}` !== `${deletedItem.projectId}${deletedItem.level}`);
            this.loadGlobalBadgeDetailsState({ badgeId: this.badgeId });
            this.$refs.projectSelectorRef.loadProjectsForBadge();
            this.$emit('global-badge-levels-changed', deletedItem);
          });
      },
      projectAdded() {
        // this.selectedProject = addedProject;
        this.levelPlaceholder = 'Pick a Level';
        this.selectedLevel = null;
      },
      projectRemoved() {
        this.selectedProject = null;
        this.selectedLevel = null;
        this.levelPlaceholder = 'First choose a Project';
      },
      changeLevel(level) {
        this.projectLevelId = level.projectId;
        this.projectLevelName = level.projectName;
        this.projectLevel = level.level;
        this.showChangeLevel = true;
      },
      changeLevelClosed(e) {
        const { projectId } = e;
        setTimeout(() => {
          this.handleFocus({ projectId });
        }, 0);
        this.showChangeLevel = false;
        this.projectLevelId = null;
        this.projectLevel = null;
        this.projectLevelName = null;
      },
      saveLevelChange(e) {
        GlobalBadgeService.changeProjectLevel(this.badgeId, e.projectId, e.oldLevel, e.newLevel)
          .then(() => this.loadBadgeLevels());
      },
      handleFocus(e) {
        if (e && e.projectId) {
          const refName = `edit_${e.projectId}`;
          const ref = this.$refs.globalLevelsTable.$refs[refName];
          this.$nextTick(() => {
            if (ref) {
              ref.focus();
            }
          });
        }
      },
    },
  };
</script>

<style>
  #level-def-panel .level-icon {
    font-size: 1.5rem;
    height: 24px;
    width: 24px;
  }

  #level-def-panel .VuePagination__count {
    display: none;
  }

  .icon-warning {
    font-size: 1.5rem;
  }

</style>

<style scoped>

</style>
