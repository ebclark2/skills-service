import Vue from 'vue';
import Vuex from 'vuex';
import auth from './modules/auth';
import users from './modules/users';
import subjects from './modules/subjects';
import projects from './modules/projects';
import badges from './modules/badges';
import config from './modules/config';
import libVersion from './modules/libVersion';

Vue.use(Vuex);

export default new Vuex.Store({
  state: {
    projectId: '',
    previousUrl: '',
    minimumProjectPoints: 100,
    minimumSubjectPoints: 100,
  },
  mutations: {
    currentProjectId(state, projectId) {
      state.projectId = projectId;
    },
    previousUrl(state, previousUrl) {
      state.previousUrl = previousUrl;
    },
  },
  modules: {
    auth,
    users,
    subjects,
    projects,
    badges,
    config,
    libVersion,
  },
});
