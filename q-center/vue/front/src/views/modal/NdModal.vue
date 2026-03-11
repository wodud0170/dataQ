<template>
    <v-card class="modal_wrapper" height="80vh">
        <v-card-title class="modal_title" height="5vh">
            {{ headerTitle }}
        </v-card-title>
        <v-card-text class="modal_body" :class="{'modal_body_xs': breakpoint === 'xs'}">
            <slot name="body">
                <!-- {{ defaultBodyContent }} -->
            </slot>
        </v-card-text>
        <v-card-actions class="mr-2 pb-4 modal_footer" height="5vh" :class="breakpoint === 'xs'? 'modal_footer_xs' : 'modal_footer_lg'">
            <template v-if="footerSubbtn">
                <v-btn class="gradient subbtnStyle" @click="$emit('subbtn')">
                    {{ footerSubbtnTitle }}
                </v-btn>
            </template>
            <v-btn color="gray" class="white--text" @click="$emit('hide')">
                {{ footerHideTitle }}
            </v-btn>
            <template v-if="footerSubmit">
                <v-btn class="gradient" @click="$emit('submit')">
                    {{ footerSubmitTitle }}
                </v-btn>
            </template>
        </v-card-actions>
    </v-card>
</template>

<script>
export default {
    name: 'NdModal',
    props: [
        // 'defaultBodyContent',
        'footerSubmit',
        'headerTitle',
        'footerHideTitle',
        'footerSubmitTitle',
        'footerSubbtn',
        'footerSubbtnTitle'
    ],
    data() {
        return {
            breakpoint:null,
        }
    },
    mounted () {
        this.breakpoint = this.$vuetify.breakpoint.name;
    },
}
</script>

<style scoped>
.modal_wrapper {
    position: relative;
}
.modal_title {
    position: absolute;
    width: 100%;
    /* height: 8vh !important; */
    top: 0;
    left: 0;
    overflow: hidden;
    box-sizing: border-box;
    border-bottom: 1px solid rgba(0, 0, 0, 0.1);
}

.modal_body {
    position: relative;
    width: 100%;
    /* max-height: 80vh; */
    top: 6vh;
    overflow-x: hidden;
    overflow-y: auto;
    box-sizing: border-box;
    padding-top: 20px !important;
}
.modal_body_xs {
    padding: 10px 0 0 !important;
}
.modal_footer {
    position: absolute;
    width: 100%;
    bottom: 0px;
    left: 0;
    padding: 16px !important;
    /* height: 8vh !important; */
    display: flex;
    align-items: center;
    flex-direction: row;
    background-color: #fff;
    box-sizing: border-box;
    border-top: 1px solid rgba(0, 0, 0, 0.1);
}
.modal_footer_xs {
    justify-content:space-evenly;
}
.modal_footer_lg {
    justify-content:flex-end;
}
.subbtnStyle {
    width: 150px;
    margin-left: 20px;
    position: absolute;
    left: 0;
}
</style>