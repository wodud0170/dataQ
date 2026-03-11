<template>
  <v-app>
    <NdLayout />
  </v-app>
</template>

<script>
import NdLayout from './views/NdLayout.vue'

export default {
  name: 'App',

  components: {
    NdLayout,
  },

  data: () => ({
    //
  }),
};
</script>
<style>
  @import './../static/css/styles.css';
</style>