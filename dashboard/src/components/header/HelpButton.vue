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
  <b-dropdown right variant="link" id="helpMenu">
    <template slot="button-content">
      <i class="far fa-question-circle" style="font-size: 1.55rem" aria-hidden="true" data-cy="helpButton"></i>
      <span class="sr-only">help menu</span>
    </template>
    <b-dropdown-item :href="officialGuide" target="_blank" style="min-width: 12.5rem;">
      <span class="text-gray-700"> <i class="fas fa-book skills-color-officialDocs" aria-hidden="true"></i>Official Docs</span>
      <span class="float-right" aria-hidden="true"><i class="fas fa-external-link-alt text-secondary"></i></span>
    </b-dropdown-item>
    <b-dropdown-divider />
    <b-dropdown-group id="dropdown-group-1" header="Guides">
      <b-dropdown-item :href="dashboardGuideUrl" target="_blank">
        <span class="text-gray-700"> <i class="fas fa-info-circle skills-color-dashboardDocs" aria-hidden="true"/><span class="link-name">Dashboard</span></span>
        <span class="float-right" aria-hidden="true"><i class="fas fa-external-link-alt text-secondary"></i></span>
      </b-dropdown-item>
      <b-dropdown-item :href="integrationGuideUrl" target="_blank">
        <span class="text-gray-700"> <i class="fas fa-hands-helping skills-color-integrationDocs" aria-hidden="true"></i>Integration</span>
        <span class="float-right" aria-hidden="true"><i class="fas fa-external-link-alt text-secondary"></i></span>
      </b-dropdown-item>
    </b-dropdown-group>
    <b-dropdown-group v-if="supportLinksProps && supportLinksProps.length > 0" id="support-group" header="Support">
      <b-dropdown-item v-for="supportLink in supportLinksProps" :key="supportLink.label" :href="supportLink.link"
                       :data-cy="`helpButtonSupportLinkLabel_${supportLink.label}`"
                       target="_blank">
        <span class="text-gray-700"> <i class="text-primary" :class="supportLink.icon" aria-hidden="true"></i>{{ supportLink.label }}</span>
        <span class="float-right" aria-hidden="true"><i class="fas fa-external-link-alt text-secondary"></i></span>
      </b-dropdown-item>
    </b-dropdown-group>
  </b-dropdown>
</template>

<script>
  export default {
    name: 'HelpButton',
    computed: {
      officialGuide() {
        return `${this.$store.getters.config.docsHost}`;
      },
      dashboardGuideUrl() {
        return `${this.$store.getters.config.docsHost}/dashboard/user-guide/`;
      },
      integrationGuideUrl() {
        return `${this.$store.getters.config.docsHost}/skills-client/`;
      },
      supportLinksProps() {
        const configs = this.$store.getters.config;
        const dupKeys = Object.keys(configs).filter((conf) => conf.startsWith('supportLink')).map((filteredConf) => filteredConf.substr(0, 12));
        const keys = dupKeys.filter((v, i, a) => a.indexOf(v) === i);
        return keys.map((key) => ({
          link: configs[key],
          label: configs[`${key}Label`],
          icon: configs[`${key}Icon`],
        }));
      },
    },
  };
</script>

<style scoped>
.fa-external-link-alt {
  font-size: 0.6rem;
}
.link-name {
  padding-right: 2rem;
}
.text-gray-700 {
  width: 40rem !important;
}
.text-gray-700 > i {
  width: 1.6rem;
}
.sr-only {
  position:absolute;
  left:-10000px;
  top:auto;
  width:1px;
  height:1px;
  overflow:hidden;
}
</style>
