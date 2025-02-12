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
  <b-modal id="importSkillsFromCatalog" size="xl" title="Import Skills from the Catalog"
           v-model="show"
           :no-close-on-backdrop="true" :centered="true" body-class="px-0 mx-0"
           header-bg-variant="info" header-text-variant="light" no-fade role="dialog"
           @hide="publishHidden">
    <skills-spinner :is-loading="loading"/>

    <no-content2 v-if="!loading && emptyCatalog" class="mt-4 mb-5"
                 title="Nothing Available for Import" data-cy="catalogSkillImportModal-NoData">
      When other projects export Skills to the Catalog then they will be available here
      to be imported.
    </no-content2>

    <div v-if="!loading && !emptyCatalog">
      <div class="row px-3 pt-1">
        <div class="col-md border-right">
          <b-form-group label="Skill Name:" label-for="skill-name-filter" label-class="text-muted">
            <b-form-input id="skill-name-filter" v-model="filters.skillName"
                          v-on:keydown.enter="loadData"
                          maxlength="50"
                          data-cy="skillNameFilter"/>
          </b-form-group>
        </div>
        <div class="col-md border-right">
          <b-form-group label="Project Name:" label-for="project-name-filter" label-class="text-muted">
            <b-form-input id="project-name-filter" v-model="filters.projectName"
                          v-on:keydown.enter="loadData"
                          maxlength="50"
                          data-cy="projectNameFilter"/>
          </b-form-group>
        </div>
        <div class="col-md">
          <b-form-group label="Subject Name:" label-for="subject-name-filter" label-class="text-muted">
            <b-form-input id="subject-name-filter" v-model="filters.subjectName"
                          v-on:keydown.enter="loadData"
                          maxlength="50"
                          data-cy="subjectNameFilter"/>
          </b-form-group>
        </div>
      </div>

      <div class="row px-3 mb-3 mt-2">
        <div class="col">
          <div class="pr-2 border-right mr-2 d-inline-block">
            <b-button variant="outline-primary" @click="loadData"
                      class="mt-1" data-cy="filterBtn"><i
              class="fa fa-filter"/> Filter
            </b-button>
            <b-button variant="outline-primary" @click="reset" class="ml-1 mt-1" data-cy="filterResetBtn"><i class="fa fa-times"/> Reset</b-button>
          </div>
          <b-button variant="outline-info" @click="changeSelectionForAll(true)"
                    data-cy="selectPageOfSkillsBtn" class="mr-2 mt-1"><i
            class="fa fa-check-square"/> Select Page
          </b-button>
          <b-button variant="outline-info" @click="changeSelectionForAll(false)"
                    data-cy="clearSelectedBtn" class="mt-1"><i class="far fa-square"></i>
            Clear
          </b-button>
        </div>
      </div>

      <skills-b-table :options="table.options" :items="table.items"
                      @page-size-changed="pageSizeChanged"
                      @page-changed="pageChanged"
                      @sort-changed="sortTable"
                      data-cy="importSkillsFromCatalogTable">
        <template #head(skillId)="data">
          <span class="text-primary"><i
            class="fas fa-graduation-cap skills-color-skills"/> {{ data.label }}</span>
        </template>
        <template #head(projectId)="data">
          <span class="text-primary"><i
            class="fas fa-tasks skills-color-projects"></i> {{ data.label }}</span>
        </template>
        <template #head(subjectId)="data">
          <span class="text-primary"><i
            class="fas fa-cubes skills-color-subjects"></i> {{ data.label }}</span>
        </template>
        <template #head(totalPoints)="data">
          <span class="text-primary"><i class="far fa-arrow-alt-circle-up skills-color-points"></i> {{
              data.label
            }}</span>
        </template>

        <template v-slot:cell(skillId)="data">
          <skill-already-existing-warning :skill="data.item"/>

          <div class="text-primary">
            <b-form-checkbox
              :disabled="data.item.alreadyHasThisSkillId || data.item.alreadyHasThisName"
              :id="`${data.item.projectId}-${data.item.skillId}`"
              v-model="data.item.selected"
              :name="`checkbox_${data.item.projectId}_${data.item.skillId}`"
              :value="true"
              :unchecked-value="false"
              :inline="true"
              v-on:input="updateActionsDisableStatus"
              :data-cy="`skillSelect_${data.item.projectId}-${data.item.skillId}`"
            >
              <span>{{ data.item.name }}</span>
            </b-form-checkbox>
          </div>
          <div class="sub-info">
            <span>ID:</span> {{ data.item.skillId }}
          </div>

          <b-button size="sm" variant="outline-info"
                    class="mr-2 py-0 px-1 mt-1"
                    @click="data.toggleDetails"
                    :aria-label="`Expand details for ${data.item.name}`"
                    :data-cy="`expandDetailsBtn_${data.item.projectId}_${data.item.skillId}`">
            <i v-if="data.detailsShowing" class="fa fa-minus-square"/>
            <i v-else class="fa fa-plus-square"/>
            Skill Details
          </b-button>
        </template>

        <template v-slot:cell(projectId)="data">
          <div class="text-primary">
            {{ data.item.projectName }}
          </div>
          <div class="sub-info">
            <span>ID:</span> {{ data.item.projectId }}
          </div>
        </template>

        <template v-slot:cell(subjectId)="data">
          <div class="text-primary">
            {{ data.item.subjectName }}
          </div>
          <div class="sub-info">
            <span>ID:</span> {{ data.item.subjectId }}
          </div>
        </template>

        <template v-slot:cell(totalPoints)="data">
          <div>
            {{ data.value }}
          </div>
          <div class="sub-info">
            {{ data.item.pointIncrement }} Increment x {{ data.item.numPerformToCompletion }}
            Occurrences
          </div>
        </template>

        <template #row-details="row">
          <skill-to-import-info :skill="row.item" />
        </template>

      </skills-b-table>
    </div>

    <div slot="modal-footer" class="w-100">
      <b-button v-if="!emptyCatalog" variant="success" size="sm" class="float-right ml-2"
                @click="importSkills" data-cy="importBtn" :disabled="importDisabled"><i
        class="far fa-arrow-alt-circle-down"></i> Import <b-badge variant="primary" data-cy="numSelectedSkills">{{ numSelectedSkills }}</b-badge>
      </b-button>
      <b-button v-if="!emptyCatalog" variant="secondary" size="sm" class="float-right" @click="close"
                data-cy="closeButton">
        <i class="fas fa-times"></i> Cancel
      </b-button>

      <b-button v-if="emptyCatalog" variant="success" size="sm" class="float-right" @click="close"
                data-cy="okButton">
        <i class="fas fa-thumbs-up"></i> OK
      </b-button>
    </div>
  </b-modal>
</template>

<script>
  import SkillAlreadyExistingWarning from '@/components/skills/catalog/SkillAlreadyExistingWarning';
  import CatalogService from './CatalogService';
  import SkillsBTable from '../../utils/table/SkillsBTable';
  import NoContent2 from '../../utils/NoContent2';
  import SkillsSpinner from '../../utils/SkillsSpinner';
  import SkillToImportInfo from './SkillToImportInfo';

  export default {
    name: 'ImportFromCatalog',
    components: {
      SkillAlreadyExistingWarning,
      SkillToImportInfo,
      SkillsSpinner,
      NoContent2,
      SkillsBTable,
    },
    props: {
      value: {
        type: Boolean,
        required: true,
      },
      currentProjectSkills: {
        type: Array,
        required: true,
      },
    },
    data() {
      return {
        show: this.value,
        loading: false,
        initialLoadHadData: false,
        importDisabled: true,
        numSelectedSkills: 0,
        filters: {
          skillName: '',
          projectName: '',
          subjectName: '',
        },
        table: {
          options: {
            busy: false,
            bordered: true,
            outlined: true,
            stacked: 'md',
            sortBy: 'skillId',
            sortDesc: false,
            fields: [
              {
                key: 'skillId',
                label: 'Skill',
                sortable: true,
              },
              {
                key: 'projectId',
                label: 'Project',
                sortable: true,
              },
              {
                key: 'subjectId',
                label: 'Subject',
                sortable: true,
              },
              {
                key: 'totalPoints',
                label: 'Points',
                sortable: true,
              },
            ],
            pagination: {
              server: true,
              currentPage: 1,
              totalRows: 1,
              pageSize: 5,
              possiblePageSizes: [5, 10, 15, 20],
            },
          },
          items: [],
        },
      };
    },
    mounted() {
      this.loadData(true);
    },
    watch: {
      show(newValue) {
        this.$emit('input', newValue);
      },
    },
    computed: {
      isSkill() {
        return this.exportType === 'Skill';
      },
      emptyCatalog() {
        return !this.initialLoadHadData;
      },
      maxProjectNameLength() {
        return this.$store.state.maxProjectNameLength;
      },
    },
    methods: {
      doesSkillIdAlreadyExist(skillId) {
        return this.currentProjectSkills.find((skill) => skill.skillId.toUpperCase() === skillId.toUpperCase()) !== undefined;
      },
      doesSkillNameAlreadyExist(name) {
        return this.currentProjectSkills.find((skill) => skill.name.toUpperCase() === name.toUpperCase()) !== undefined;
      },
      loadData(isInitial = undefined) {
        if (isInitial === true) {
          this.loading = true;
        }
        this.table.options.busy = true;
        const params = {
          limit: this.table.options.pagination.pageSize,
          page: this.table.options.pagination.currentPage,
          orderBy: this.table.options.sortBy,
          ascending: !this.table.options.sortDesc,
          projectNameSearch: encodeURIComponent(this.filters.projectName.trim()),
          subjectNameSearch: encodeURIComponent(this.filters.subjectName.trim()),
          skillNameSearch: encodeURIComponent(this.filters.skillName.trim()),
        };
        CatalogService.getCatalogSkills(this.$route.params.projectId, params)
          .then((res) => {
            const dataSkills = res.data;
            if (dataSkills) {
              this.table.items = dataSkills.map((item) => ({
                selected: false,
                ...item,
                alreadyHasThisSkillId: this.doesSkillIdAlreadyExist(item.skillId),
                alreadyHasThisName: this.doesSkillNameAlreadyExist(item.name),
              }));
              this.table.options.pagination.totalRows = res.totalCount;
              if (this.table.items.length > 0) {
                this.initialLoadHadData = true;
              }
            }
          })
          .finally(() => {
            this.loading = false;
            this.table.options.busy = false;
          });
      },
      close(e) {
        this.show = false;
        this.publishHidden(e);
      },
      publishHidden(e) {
        this.$emit('hidden', { ...e });
      },
      pageChanged(pageNum) {
        this.table.options.pagination.currentPage = pageNum;
        this.loadData();
      },
      pageSizeChanged(newSize) {
        this.table.options.pagination.pageSize = newSize;
        this.loadData();
      },
      sortTable(sortContext) {
        this.table.options.sortBy = sortContext.sortBy;
        this.table.options.sortDesc = sortContext.sortDesc;

        // set to the first page
        this.table.options.pagination.currentPage = 1;
        this.loadData();
      },
      updateActionsDisableStatus() {
        this.numSelectedSkills = this.table.items.reduce((total, item) => (item.selected ? total + 1 : total), 0);
        this.importDisabled = this.numSelectedSkills === 0;
      },
      importSkills() {
        const selected = this.table.items.filter((item) => item.selected);
        const projAndSkillIds = selected.map((skill) => ({
          projectId: skill.projectId,
          skillId: skill.skillId,
        }));
        this.$emit('to-import', projAndSkillIds);
        this.show = false;
      },
      changeSelectionForAll(selectedValue) {
        this.table.items.forEach((item) => {
          // eslint-disable-next-line no-param-reassign
          item.selected = selectedValue;
        });
        this.updateActionsDisableStatus();
      },
      reset() {
        this.filters.skillName = '';
        this.filters.projectName = '';
        this.filters.subjectName = '';
        this.loadData();
      },
    },
  };
</script>

<style scoped>
.sub-info {
  font-size: 0.9rem;
}
</style>
